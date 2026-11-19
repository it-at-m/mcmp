package siem

import (
	"crypto/tls"
	"fmt"
	"io"
	"log"
	"net"
	"sync"
	"time"
)

// networkWriter writes to a network connection (UDP, plain TCP or TCP over TLS).
//
// The connection is kept open for the whole run, exactly like the Java
// LogstashTcpSocketAppender (keepAliveDuration). A connection closed by the
// receiver is detected on write and replaced by a fresh one.
type networkWriter struct {
	name      string
	network   string
	address   string
	useTLS    bool
	tlsConfig *tls.Config

	mu   sync.Mutex
	conn net.Conn
}

// dial establishes a new connection. For TLS targets the handshake is retried
// without the hybrid post-quantum key exchange: recent Go versions send
// X25519MLKEM768 by default, which enlarges the ClientHello and is dropped by
// some TLS middleboxes without any useful error message.
func (n *networkWriter) dial() (net.Conn, error) {
	dialer := &net.Dialer{Timeout: dialTimeout, KeepAlive: 30 * time.Second}

	if !n.useTLS {
		return dialer.Dial(n.network, n.address)
	}

	conn, err := tls.DialWithDialer(dialer, "tcp", n.address, n.tlsConfig)
	if err == nil {
		return conn, nil
	}

	fallback := n.tlsConfig.Clone()
	fallback.CurvePreferences = []tls.CurveID{tls.X25519, tls.CurveP256, tls.CurveP384}

	fallbackConn, fallbackErr := tls.DialWithDialer(dialer, "tcp", n.address, fallback)
	if fallbackErr != nil {
		return nil, fmt.Errorf("%w (retry without post-quantum key exchange also failed: %v)", err, fallbackErr)
	}
	log.Printf("SIEM[%s]: handshake only succeeded with classic curves", n.name)

	return fallbackConn, nil
}

// write sends p over the cached connection, dialing on first use.
// The caller must hold n.mu.
func (n *networkWriter) write(p []byte) error {
	if n.conn == nil {
		conn, err := n.dial()
		if err != nil {
			return err
		}
		n.conn = conn
	}

	_ = n.conn.SetWriteDeadline(time.Now().Add(writeTimeout))
	written, err := n.conn.Write(p)
	if err != nil {
		return err
	}
	if written != len(p) {
		return io.ErrShortWrite
	}
	return nil
}

// dropConn discards the cached connection. The caller must hold n.mu.
func (n *networkWriter) dropConn() {
	if n.conn != nil {
		_ = n.conn.Close()
		n.conn = nil
	}
}

// Write sends one event. It never returns an error on purpose: the logger hands
// one payload to several sinks, and a transient network problem must not stop
// the remaining sinks from receiving the event. Failures are logged instead.
func (n *networkWriter) Write(p []byte) (int, error) {
	n.mu.Lock()
	defer n.mu.Unlock()

	if err := n.write(p); err != nil {
		// The receiver may have closed an idle connection (restart, idle
		// timeout): reconnect once instead of going silent for the rest of
		// the process lifetime.
		n.dropConn()
		if retryErr := n.write(p); retryErr != nil {
			log.Printf("Failed to send SIEM message to %s (%s): %v", n.address, n.network, retryErr)
		}
	}

	return len(p), nil
}

// Close shuts the connection down gracefully.
//
// The old implementation closed the socket right after the write. The peer's
// close_notify then arrived on an already closed socket, the kernel answered
// with RST, and a TLS terminating load balancer forwarded that RST to its
// backend - Splunk discarded the still buffered event. CloseWrite plus drain
// avoids the RST entirely.
func (n *networkWriter) Close() error {
	n.mu.Lock()
	defer n.mu.Unlock()

	conn := n.conn
	n.conn = nil

	return closeGracefully(conn)
}
