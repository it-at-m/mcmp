package siem

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"

	"gopkg.in/natefinch/lumberjack.v2"
)

const (
	CefVersion    = "0"
	DeviceVendor  = "LHM"
	DeviceProduct = "mCMP"
	DeviceVersion = "1.0"
)

var berlinLocation *time.Location

// SyslogTargetConfig defines network targets (e.g. QRadar, Splunk)
type SyslogTargetConfig struct {
	Host               string
	Port               int
	Protocol           string // tcp or udp
	UseTLS             bool   // true for TCP over TLS (Splunk)
	InsecureSkipVerify bool   // skip TLS certificate verification
	CaCertPath         string // optional custom CA certificate path
}

// SiemConfig holds the configuration for SIEM logging
type SiemConfig struct {
	Enabled bool
	File    struct {
		Filename   string
		MaxSize    int  // in megabytes
		MaxBackups int  // number of files
		MaxAge     int  // days
		Compress   bool // disabled by default
	}
	QRadar SyslogTargetConfig
	Splunk SyslogTargetConfig
}
type SiemLogger struct {
	writer   io.Writer
	hostname string
	enabled  bool
}

// networkWriter is a helper to write to a network connection (supports UDP, plain TCP, and TCP over TLS)
type networkWriter struct {
	network   string
	address   string
	useTLS    bool
	tlsConfig *tls.Config
}

func init() {
	loc, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		panic(fmt.Sprintf("failed to load time location Europe/Berlin: %v", err))
	}
	berlinLocation = loc
}

// NewSiemLogger creates a new logger that writes to both file (rotated) and network (syslog)
func NewSiemLogger(cfg SiemConfig) *SiemLogger {
	logger := &SiemLogger{
		enabled: cfg.Enabled,
	}

	if !cfg.Enabled {
		return logger
	}

	// Get hostname for logs
	hostname, err := os.Hostname()
	if err != nil {
		hostname = "localhost"
	}
	logger.hostname = hostname

	var writers []io.Writer

	// 1. File Logger with Rotation (using lumberjack)
	if cfg.File.Filename != "" {
		fileLogger := &lumberjack.Logger{
			Filename:   cfg.File.Filename,
			MaxSize:    cfg.File.MaxSize,    // megabytes
			MaxBackups: cfg.File.MaxBackups, // files
			MaxAge:     cfg.File.MaxAge,     // days
			Compress:   cfg.File.Compress,   // disabled by default
		}
		writers = append(writers, fileLogger)
	}

	// 2. QRadar Target
	if cfg.QRadar.Host != "" && cfg.QRadar.Port != 0 {
		writer, err := createNetworkWriter(cfg.QRadar)
		if err != nil {
			log.Printf("Failed to configure QRadar SIEM writer: %v", err)
		} else {
			writers = append(writers, writer)
		}
	}

	// 3. Splunk Target (with TLS support)
	if cfg.Splunk.Host != "" && cfg.Splunk.Port != 0 {
		writer, err := createNetworkWriter(cfg.Splunk)
		if err != nil {
			log.Printf("Failed to configure Splunk SIEM writer: %v", err)
		} else {
			writers = append(writers, writer)
		}
	}

	if len(writers) > 0 {
		logger.writer = io.MultiWriter(writers...)
	} else {
		// Fallback to stdout if enabled but no outputs configured
		logger.writer = os.Stdout
	}

	return logger
}

func createNetworkWriter(cfg SyslogTargetConfig) (*networkWriter, error) {
	protocol := strings.ToLower(cfg.Protocol)
	if protocol == "" {
		protocol = "udp"
	}

	nw := &networkWriter{
		network: protocol,
		address: fmt.Sprintf("%s:%d", cfg.Host, cfg.Port),
		useTLS:  cfg.UseTLS,
	}

	if cfg.UseTLS {
		tlsConf := &tls.Config{
			InsecureSkipVerify: cfg.InsecureSkipVerify,
			ServerName:         cfg.Host,
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

func (n *networkWriter) Write(p []byte) (int, error) {
	dialer := &net.Dialer{Timeout: 5 * time.Second}

	var conn net.Conn
	var err error

	if n.useTLS {
		conn, err = tls.DialWithDialer(dialer, n.network, n.address, n.tlsConfig)
	} else {
		conn, err = dialer.Dial(n.network, n.address)
	}

	if err != nil {
		// Log the error instead of returning it to io.MultiWriter,
		// so that a failure in one target (e.g. QRadar down) doesn't prevent delivery to the other (Splunk)
		log.Printf("Failed to send SIEM message to %s (%s): %v", n.address, n.network, err)
		return len(p), nil
	}
	defer conn.Close()

	_, err = conn.Write(p)
	if err != nil {
		log.Printf("Failed to write SIEM data to %s: %v", n.address, err)
		return len(p), nil
	}

	return len(p), nil
}

func (s *SiemLogger) LogAuthSuccess(username, remoteIp string, authorities []string, details string) {
	additionalFields := make(map[string]string)
	additionalFields["act"] = "Authentication Success"
	additionalFields["outcome"] = "Success"
	if len(authorities) > 0 {
		additionalFields["roles"] = fmt.Sprintf("%v", authorities)
	}
	if details != "" {
		additionalFields["details"] = details
	}
	s.logCefEvent("100", "Login Success", 0, username, remoteIp,
		fmt.Sprintf("User %s logged in successfully from %s", username, remoteIp), additionalFields)
}

func (s *SiemLogger) LogAuthFailure(username, remoteIp, errorMsg, details string) {
	additionalFields := make(map[string]string)
	additionalFields["act"] = "Authentication Failure"
	additionalFields["outcome"] = "Failure"
	if errorMsg != "" {
		additionalFields["reason"] = errorMsg
	}
	if details != "" {
		additionalFields["details"] = details
	}
	s.logCefEvent("200", "Login Failed", 7, username, remoteIp,
		fmt.Sprintf("User %s login failed from %s", username, remoteIp), additionalFields)
}

func (s *SiemLogger) LogAdminAccess(username, remoteIp string) {
	s.logCefEvent("400", "Admin Access Granted", 6, username, remoteIp, "User granted admin privileges", nil)
}

func (s *SiemLogger) LogSecurityError(username, remoteIp, errorMsg string) {
	s.logCefEvent("500", "Security Error", 8, username, remoteIp, errorMsg, nil)
}

func (s *SiemLogger) LogVmStatusChange(identifier, username, hostname, appService, appServiceNumber, changeNumber, jobNumber, message string) {
	additionalFields := make(map[string]string)
	if hostname != "" {
		additionalFields["dhost"] = hostname
	}
	if appService != "" {
		additionalFields["cs1group"] = appService
	}
	if appServiceNumber != "" {
		additionalFields["cs2groupNumber"] = appServiceNumber
	}
	if changeNumber != "" {
		additionalFields["cs3snowNumber"] = changeNumber
	}
	if jobNumber != "" {
		additionalFields["cs4mcmpNumber"] = jobNumber
	}
	s.logCefEvent("VM StatusChange", identifier, 0, username, "", message, additionalFields)
}

func (s *SiemLogger) logCefEvent(signatureId, eventName string, severity int, username, remoteIp, message string, additionalFields map[string]string) {
	if !s.enabled || s.writer == nil {
		return
	}

	var sb strings.Builder
	// Syslog Header: <PRI>TIMESTAMP HOSTNAME
	// PRI <14> = (Facility User(1) * 8) + Severity Info(6)
	sb.WriteString(fmt.Sprintf("<14>%s %s ", time.Now().In(berlinLocation).Format(time.Stamp), s.hostname))

	// CEF Header: CEF:Version|Device Vendor|Device Product|Device Version|Signature ID|Name|Severity|Extension
	// Example: CEF:0|LHM|mMCP|1.0|...
	sb.WriteString("CEF:")
	sb.WriteString(CefVersion)
	sb.WriteString("|")
	sb.WriteString(DeviceVendor)
	sb.WriteString("|")
	sb.WriteString(DeviceProduct)
	sb.WriteString("|")
	sb.WriteString(DeviceVersion)
	sb.WriteString("|")
	sb.WriteString(s.escapeCefField(signatureId))
	sb.WriteString("|")
	sb.WriteString(s.escapeCefField(eventName))
	sb.WriteString("|")
	sb.WriteString(strconv.Itoa(severity))
	sb.WriteString("|")

	// Extensions
	if username != "" {
		sb.WriteString("suser=")
		sb.WriteString(s.escapeCefExtension(username))
		sb.WriteString(" ")
	}
	if remoteIp != "" {
		sb.WriteString("src=")
		sb.WriteString(s.escapeCefExtension(remoteIp))
		sb.WriteString(" ")
	}

	// rt (Receipt Time) in Epoch Milliseconds
	sb.WriteString("rt=")
	sb.WriteString(strconv.FormatInt(time.Now().In(berlinLocation).UnixMilli(), 10))
	sb.WriteString(" ")

	sb.WriteString("deviceHostName=")
	sb.WriteString(s.escapeCefExtension(s.hostname))
	sb.WriteString(" ")

	if additionalFields != nil {
		keys := make([]string, 0, len(additionalFields))
		for k := range additionalFields {
			keys = append(keys, k)
		}
		sort.Strings(keys)

		for _, k := range keys {
			sb.WriteString(k)
			sb.WriteString("=")
			sb.WriteString(s.escapeCefExtension(additionalFields[k]))
			sb.WriteString(" ")
		}
	}

	if message != "" {
		sb.WriteString("msg=")
		sb.WriteString(s.escapeCefExtension(message))
		sb.WriteString(" ")
	}

	sb.WriteString("\n")

	// Write to all configured outputs
	_, err := s.writer.Write([]byte(sb.String()))
	if err != nil {
		// Fallback logging to stderr if SIEM logging fails
		log.Printf("Error writing to SIEM logger: %v", err)
	}
}

func (s *SiemLogger) escapeCefField(value string) string {
	if value == "" {
		return ""
	}
	// Escaping rules for Header fields: | and \
	r := strings.ReplaceAll(value, "\\", "\\\\")
	return strings.ReplaceAll(r, "|", "\\|")
}

func (s *SiemLogger) escapeCefExtension(value string) string {
	if value == "" {
		return ""
	}
	// Escaping rules for Extensions: =, \n, \r, \
	r := strings.ReplaceAll(value, "\\", "\\\\")
	r = strings.ReplaceAll(r, "=", "\\=")
	r = strings.ReplaceAll(r, "\n", "\\n")
	return strings.ReplaceAll(r, "\r", "\\r")
}
