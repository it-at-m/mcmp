// Package siem writes CEF events to a rotated file and to network targets:
// QRadar receives syslog over UDP, Splunk a raw CEF stream over TCP/TLS.
package siem

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strings"
	"time"

	"gopkg.in/natefinch/lumberjack.v2"
)

const (
	CefVersion    = "0"
	DeviceVendor  = "LHM"
	DeviceProduct = "mCMP"
	DeviceVersion = "1.0"

	// FormatSyslog prefixes the CEF line with an RFC3164 header
	// ("<14>Nov 18 10:00:00 host CEF:0|..."). Syslog receivers such as QRadar
	// require this framing.
	FormatSyslog = "syslog"

	// FormatCEF sends only the bare CEF line. This is exactly what the Java
	// backend sends to Splunk: LogstashTcpSocketAppender with a
	// PatternLayoutEncoder using the pattern "%msg%n" emits no syslog framing.
	FormatCEF = "cef"
)

const (
	dialTimeout  = 5 * time.Second
	writeTimeout = 5 * time.Second
	closeTimeout = 2 * time.Second
)

var berlinLocation *time.Location

func init() {
	loc, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		panic(fmt.Sprintf("failed to load time location Europe/Berlin: %v", err))
	}
	berlinLocation = loc
}

// SiemConfig holds the configuration for SIEM logging.
type SiemConfig struct {
	Enabled        bool
	DeviceHostName string
	File           struct {
		Filename   string
		MaxSize    int  // in megabytes
		MaxBackups int  // number of files
		MaxAge     int  // days
		Compress   bool // disabled by default
	}
	QRadar SyslogTargetConfig
	Splunk SyslogTargetConfig
}

// sink is a single output of the logger. The name is used for diagnostics so a
// failing output can be identified in the log.
type sink struct {
	name   string
	writer io.Writer
}

// writeToAll hands p to every sink independently.
//
// io.MultiWriter is deliberately not used here: it stops at the first error, so
// a full disk on the rotated file would silently keep the same event from ever
// reaching QRadar and Splunk. Every sink is written on its own; a broken output
// therefore no longer suppresses the remaining ones.
func writeToAll(sinks []sink, p string) {
	for _, sk := range sinks {
		if _, err := io.WriteString(sk.writer, p); err != nil {
			log.Printf("Failed to write SIEM event to %s: %v", sk.name, err)
		}
	}
}

// closeGracefully sends close_notify (TLS) or FIN (TCP), drains the peer's
// remaining records so that the peer's own close_notify finds an open socket,
// and only then releases the descriptor. Closing without the drain makes the
// kernel answer the peer with RST.
//
// UDP and other connectionless transports have no teardown handshake; draining
// such a socket would block until the read deadline and delay shutdown by
// closeTimeout for no benefit.
func closeGracefully(conn net.Conn) error {
	if conn == nil {
		return nil
	}

	_ = conn.SetWriteDeadline(time.Now().Add(closeTimeout))

	switch c := conn.(type) {
	case *tls.Conn:
		_ = c.CloseWrite() // sends close_notify, keeps the socket readable
	case *net.TCPConn:
		_ = c.CloseWrite()
	default:
		return conn.Close()
	}

	// Drain the peer's close_notify. Read returns io.EOF once it arrived, or an
	// i/o timeout after closeTimeout if the peer stays silent.
	_ = conn.SetReadDeadline(time.Now().Add(closeTimeout))
	_, _ = io.Copy(io.Discard, conn)

	return conn.Close()
}

// NewSiemLogger creates a logger that writes to the rotated file and to the
// configured network targets.
func NewSiemLogger(cfg SiemConfig) *SiemLogger {
	logger := &SiemLogger{
		enabled: cfg.Enabled,
	}
	if !cfg.Enabled {
		return logger
	}

	hostname := strings.TrimSpace(cfg.DeviceHostName)
	if hostname == "" {
		detected, err := os.Hostname()
		if err != nil {
			detected = "localhost"
		}
		hostname = detected
	}
	logger.hostname = hostname

	var syslogSinks []sink
	var rawSinks []sink

	// 1. File logger with rotation
	if cfg.File.Filename != "" {
		fileLogger := &lumberjack.Logger{
			Filename:   cfg.File.Filename,
			MaxSize:    cfg.File.MaxSize,
			MaxBackups: cfg.File.MaxBackups,
			MaxAge:     cfg.File.MaxAge,
			Compress:   cfg.File.Compress,
		}
		syslogSinks = append(syslogSinks, sink{name: "file " + cfg.File.Filename, writer: fileLogger})
		logger.closers = append(logger.closers, fileLogger)
	}

	// 2. QRadar target
	if cfg.QRadar.Configured() {
		writer, err := createNetworkWriter("qradar", cfg.QRadar)
		if err != nil {
			logger.initErrs = append(logger.initErrs, fmt.Errorf("qradar: %w", err))
			log.Printf("Failed to configure QRadar SIEM writer: %v", err)
		} else {
			description := describeTarget("qradar", cfg.QRadar)
			sk := sink{name: description, writer: writer}
			if cfg.QRadar.WireFormat() == FormatCEF {
				rawSinks = append(rawSinks, sk)
			} else {
				syslogSinks = append(syslogSinks, sk)
			}
			logger.targets = append(logger.targets, description)
			logger.closers = append(logger.closers, writer)
		}
	} else if cfg.QRadar.Host != "" || cfg.QRadar.Port != 0 {
		logger.initErrs = append(logger.initErrs,
			fmt.Errorf("qradar: incomplete configuration (host=%q, port=%d)", cfg.QRadar.Host, cfg.QRadar.Port))
	}

	// 3. Splunk target (with TLS support)
	if cfg.Splunk.Configured() {
		writer, err := createNetworkWriter("splunk", cfg.Splunk)
		if err != nil {
			logger.initErrs = append(logger.initErrs, fmt.Errorf("splunk: %w", err))
			log.Printf("Failed to configure Splunk SIEM writer: %v", err)
		} else {
			description := describeTarget("splunk", cfg.Splunk)
			sk := sink{name: description, writer: writer}
			if cfg.Splunk.WireFormat() == FormatCEF {
				rawSinks = append(rawSinks, sk)
			} else {
				syslogSinks = append(syslogSinks, sk)
			}
			logger.targets = append(logger.targets, description)
			logger.closers = append(logger.closers, writer)
		}
	} else if cfg.Splunk.Host != "" || cfg.Splunk.Port != 0 {
		logger.initErrs = append(logger.initErrs,
			fmt.Errorf("splunk: incomplete configuration (host=%q, port=%d)", cfg.Splunk.Host, cfg.Splunk.Port))
	}

	logger.syslogSinks = syslogSinks
	logger.rawSinks = rawSinks
	if len(syslogSinks) == 0 && len(rawSinks) == 0 {
		log.Printf("SIEM: enabled but no output configured - falling back to stdout")
		logger.syslogSinks = []sink{{name: "stdout", writer: os.Stdout}}
	}

	return logger
}

// createNetworkWriter validates the target and builds the writer.
func createNetworkWriter(name string, cfg SyslogTargetConfig) (*networkWriter, error) {
	protocol := strings.ToLower(strings.TrimSpace(cfg.Protocol))
	if protocol == "" {
		protocol = "udp"
	}
	if protocol != "tcp" && protocol != "udp" {
		return nil, fmt.Errorf("unsupported protocol %q (expected \"tcp\" or \"udp\")", cfg.Protocol)
	}
	if cfg.UseTLS && protocol != "tcp" {
		return nil, fmt.Errorf("use_tls requires protocol \"tcp\", got %q", cfg.Protocol)
	}
	switch cfg.WireFormat() {
	case FormatSyslog, FormatCEF:
	default:
		return nil, fmt.Errorf("unsupported format %q (expected \"syslog\" or \"cef\")", cfg.Format)
	}

	address, err := cfg.Address()
	if err != nil {
		return nil, err
	}

	nw := &networkWriter{
		name:    name,
		network: protocol,
		address: address,
		useTLS:  cfg.UseTLS,
	}

	if cfg.UseTLS {
		tlsConf := &tls.Config{
			InsecureSkipVerify: cfg.InsecureSkipVerify,
			ServerName:         cfg.Host,
		}
		if cfg.ServerName != "" {
			tlsConf.ServerName = cfg.ServerName
		}

		if cfg.CaCertPath != "" {
			caCert, err := os.ReadFile(cfg.CaCertPath)
			if err != nil {
				return nil, fmt.Errorf("failed to read CA certificate: %w", err)
			}
			caCertPool := x509.NewCertPool()
			if !caCertPool.AppendCertsFromPEM(caCert) {
				return nil, fmt.Errorf("failed to append CA certificate from %s", cfg.CaCertPath)
			}
			tlsConf.RootCAs = caCertPool
		}

		nw.tlsConfig = tlsConf
	}

	return nw, nil
}

// describeTarget returns a human readable description for the log.
func describeTarget(name string, cfg SyslogTargetConfig) string {
	address, err := cfg.Address()
	if err != nil {
		return fmt.Sprintf("%s: invalid address: %v", name, err)
	}
	protocol := strings.ToLower(strings.TrimSpace(cfg.Protocol))
	if protocol == "" {
		protocol = "udp"
	}
	return fmt.Sprintf("%s %s (%s, tls=%t, format=%s)", name, address, protocol, cfg.UseTLS, cfg.WireFormat())
}
