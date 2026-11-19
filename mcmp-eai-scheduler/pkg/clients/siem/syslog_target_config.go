package siem

import (
	"fmt"
	"net"
	"strconv"
	"strings"
)

// SyslogTargetConfig defines one network target (e.g. QRadar, Splunk).
type SyslogTargetConfig struct {
	Host               string
	Port               int
	Protocol           string // tcp or udp
	UseTLS             bool   // true for TCP over TLS (Splunk)
	InsecureSkipVerify bool   // skip TLS certificate verification
	CaCertPath         string // optional custom CA certificate path

	// ServerName overrides the name used for the TLS handshake (SNI and
	// certificate verification). Only needed when a TLS terminating proxy
	// routes by SNI.
	ServerName string

	// Format selects the wire format: "syslog" (default) or "cef".
	// QRadar is a syslog receiver and needs the header; the Splunk input used
	// by the Java backend expects the bare CEF line.
	Format string
}

// Configured reports whether the target has the minimal set of values needed
// to build a connection.
func (c SyslogTargetConfig) Configured() bool {
	return strings.TrimSpace(c.Host) != "" && c.Port != 0
}

// WireFormat returns the normalized wire format of the target.
func (c SyslogTargetConfig) WireFormat() string {
	format := strings.ToLower(strings.TrimSpace(c.Format))
	if format == "" {
		return FormatSyslog
	}
	return format
}

// Address returns the dialable "host:port" form of the target.
// A trailing space or a pasted "https://..." URL makes dial fail before a
// single packet is sent - and such a failure is invisible in a packet capture.
func (c SyslogTargetConfig) Address() (string, error) {
	host := strings.TrimSpace(c.Host)
	switch {
	case host == "":
		return "", fmt.Errorf("host is empty")
	case strings.Contains(host, "://"):
		return "", fmt.Errorf("host %q must be a plain host name or IP without scheme", host)
	case strings.ContainsAny(host, "/?# "):
		return "", fmt.Errorf("host %q contains invalid characters", host)
	}
	if c.Port <= 0 || c.Port > 65535 {
		return "", fmt.Errorf("invalid port %d", c.Port)
	}
	return net.JoinHostPort(host, strconv.Itoa(c.Port)), nil
}
