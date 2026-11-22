package siem

import (
	"errors"
	"fmt"
	"io"
	"sort"
	"strconv"
	"strings"
	"time"
)

// SiemLogger writes CEF events to all configured sinks.
//
// Two sink groups exist because a target may explicitly ask for the bare CEF
// line:
//   - syslogSinks receives syslog-framed lines (rotated file, QRadar, and
//     Splunk in the recommended configuration)
//   - rawSinks receives the bare CEF line (only for Format = "cef")
type SiemLogger struct {
	syslogSinks []sink
	rawSinks    []sink

	hostname string
	enabled  bool

	targets  []string    // network targets that are part of the sink list
	initErrs []error     // targets that were configured but could not be registered
	closers  []io.Closer // file and network writers, closed by Close()
}

// ActiveTargets returns the network targets that are part of the writer chain.
func (s *SiemLogger) ActiveTargets() []string {
	if s == nil {
		return nil
	}
	return s.targets
}

// InitErrors returns configuration problems that prevented a target from being
// registered. They must be logged: a misconfigured target is otherwise skipped
// without any visible message.
func (s *SiemLogger) InitErrors() []error {
	if s == nil {
		return nil
	}
	return s.initErrs
}

// Close flushes and closes all outputs gracefully. A syslog target that was
// closed right after the last write makes the peer answer with RST, and the
// still buffered events are discarded; the network writers therefore shut down
// via closeGracefully (close_notify + drain) instead of a bare conn.Close().
func (s *SiemLogger) Close() error {
	if s == nil {
		return nil
	}

	closers := s.closers
	s.closers = nil

	var errs []error
	for _, c := range closers {
		if err := c.Close(); err != nil {
			errs = append(errs, err)
		}
	}
	return errors.Join(errs...)
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

// logCefEvent builds one CEF event and hands it to both sink groups.
func (s *SiemLogger) logCefEvent(signatureId, eventName string, severity int, username, remoteIp, message string, additionalFields map[string]string) {
	if s == nil || !s.enabled {
		return
	}
	if len(s.syslogSinks) == 0 && len(s.rawSinks) == 0 {
		return
	}

	// CEF header:
	// CEF:Version|Device Vendor|Device Product|Device Version|Signature ID|Name|Severity|Extension
	var sb strings.Builder
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

	// rt (Receipt Time) in epoch milliseconds
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

	// Splunk raw input expects a line per event.
	sb.WriteString("\n")
	body := sb.String()

	// Raw sinks first: bare CEF line, only used by targets with Format = "cef".
	if len(s.rawSinks) > 0 {
		writeToAll(s.rawSinks, body)
	}

	// Syslog sinks (file, QRadar, Splunk): RFC3164 header in front of the CEF line.
	// PRI <14> = (Facility User(1) * 8) + Severity Info(6)
	if len(s.syslogSinks) > 0 {
		header := fmt.Sprintf("<14>%s %s ", time.Now().In(berlinLocation).Format(time.Stamp), s.hostname)
		writeToAll(s.syslogSinks, header+body)
	}
}

// escapeCefField escapes CEF header fields (| and \).
func (s *SiemLogger) escapeCefField(value string) string {
	if value == "" {
		return ""
	}
	r := strings.ReplaceAll(value, "\\", "\\\\")
	return strings.ReplaceAll(r, "|", "\\|")
}

// escapeCefExtension escapes CEF extension values (=, \n, \r, \).
func (s *SiemLogger) escapeCefExtension(value string) string {
	if value == "" {
		return ""
	}
	r := strings.ReplaceAll(value, "\\", "\\\\")
	r = strings.ReplaceAll(r, "=", "\\=")
	r = strings.ReplaceAll(r, "\n", "\\n")
	return strings.ReplaceAll(r, "\r", "\\r")
}
