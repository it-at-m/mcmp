package mail

import (
	"bytes"
	"crypto/tls"
	"errors"
	"fmt"
	"mime/quotedprintable"
	"net/smtp"
	"strings"
)

type loginAuth struct {
	username, password string
}

func LoginAuth(username, password string) smtp.Auth {
	return &loginAuth{username, password}
}

func (l *loginAuth) Start(server *smtp.ServerInfo) (string, []byte, error) {
	return "LOGIN", []byte{}, nil
}

func (l *loginAuth) Next(fromServer []byte, more bool) ([]byte, error) {
	if more {
		switch string(fromServer) {
		case "Username:", "user:", "User:":
			return []byte(l.username), nil
		case "Password:", "pass:", "Pass:":
			return []byte(l.password), nil
		default:
			return nil, errors.New("unknown server dialog")
		}
	}
	return nil, nil
}

func SendEmail(server string, port int, username string, password string, to []string, cc []string, subject string, body string, isHTML bool) error {
	addr := fmt.Sprintf("%s:%d", server, port)

	// Build message with headers in correct order
	var sb strings.Builder

	// Write headers in a defined order (important for email clients)
	sb.WriteString("From: " + username + "\r\n")
	sb.WriteString("To: " + strings.Join(to, ", ") + "\r\n")
	if len(cc) > 0 {
		sb.WriteString("Cc: " + strings.Join(cc, ", ") + "\r\n")
	}
	sb.WriteString("Subject: " + subject + "\r\n")
	sb.WriteString("MIME-Version: 1.0\r\n")
	if isHTML {
		sb.WriteString("Content-Type: text/html; charset=\"utf-8\"\r\n")
		sb.WriteString("Content-Transfer-Encoding: quoted-printable\r\n")
	} else {
		sb.WriteString("Content-Type: text/plain; charset=\"utf-8\"\r\n")
		sb.WriteString("Content-Transfer-Encoding: 8bit\r\n")
	}
	sb.WriteString("\r\n")

	if isHTML {
		var qpBuffer bytes.Buffer
		qpWriter := quotedprintable.NewWriter(&qpBuffer)
		_, _ = qpWriter.Write([]byte(body))
		_ = qpWriter.Close()
		sb.WriteString(qpBuffer.String())
	} else {
		// Normaler Text-Body mit CRLF Fix
		cleanBody := strings.ReplaceAll(body, "\r\n", "\n")
		cleanBody = strings.ReplaceAll(cleanBody, "\n", "\r\n")
		sb.WriteString(cleanBody)
	}

	message := sb.String()

	// Collect all recipients for the SMTP Envelope (TO + CC + BCC)
	allRecipients := append(to, cc...)

	// Initialize Authentication
	auth := LoginAuth(username, password)

	// Establish connection to server
	c, err := smtp.Dial(addr)
	if err != nil {
		return err
	}
	defer func(c *smtp.Client) {
		err := c.Quit()
		if err != nil {
			fmt.Println("Error closing SMTP connection:", err)
		}
	}(c)

	// Configure STARTTLS (enforce TLS 1.2)
	tlsConfig := &tls.Config{
		ServerName: server,
		MinVersion: tls.VersionTLS12,
	}

	if err = c.StartTLS(tlsConfig); err != nil {
		return err
	}

	// Perform Authentication
	if err = c.Auth(auth); err != nil {
		return err
	}

	// Set sender and recipients
	if err = c.Mail(username); err != nil {
		return err
	}
	for _, addr := range allRecipients {
		if err = c.Rcpt(addr); err != nil {
			return err
		}
	}

	// Transfer data
	w, err := c.Data()
	if err != nil {
		return err
	}
	_, err = w.Write([]byte(message))
	if err != nil {
		return err
	}
	err = w.Close()
	if err != nil {
		return err
	}

	return nil
}
