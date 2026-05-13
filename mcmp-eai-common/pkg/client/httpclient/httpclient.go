package httpclient

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// Config holds the configuration for the universal HTTP client
type Config struct {
	Username        string
	Password        string
	BearerToken     string
	ProxyURL        string
	RequestTimeout  time.Duration
	MaxRetries      int
	RetryDelay      time.Duration
	EnableTLSVerify bool
	Debug           bool
}

// Client is a generic HTTP client wrapper with retry logic and optimized transport
type Client struct {
	logger     logging.Logger
	httpClient *http.Client
	config     Config
}

// NewClient creates and initializes a new generic HTTP client
func NewClient(config Config, logger logging.Logger) (*Client, error) {
	if config.MaxRetries < 0 {
		return nil, fmt.Errorf("MaxRetries cannot be negative")
	}
	if config.RetryDelay < 0 {
		return nil, fmt.Errorf("RetryDelay cannot be negative")
	}
	if config.RequestTimeout < 0 {
		return nil, fmt.Errorf("RequestTimeout cannot be negative")
	}
	// Defaults setup
	if config.RequestTimeout == 0 {
		config.RequestTimeout = 120 * time.Second
	}
	if config.RetryDelay == 0 {
		config.RetryDelay = 5 * time.Second
	}
	if config.MaxRetries == 0 {
		config.MaxRetries = 3
	}

	// Use NoOpLogger if nil is provided
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	c := &Client{
		logger: logger,
		config: config,
	}

	// Configure secure TLS settings
	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: !config.EnableTLSVerify,
	}

	// Configure HTTP transport with optimized settings
	transport := &http.Transport{
		TLSClientConfig:       tlsConfig,
		MaxIdleConns:          10,
		MaxIdleConnsPerHost:   2,
		MaxConnsPerHost:       3,
		IdleConnTimeout:       30 * time.Second,
		TLSHandshakeTimeout:   30 * time.Second,
		ExpectContinueTimeout: 5 * time.Second,
		ResponseHeaderTimeout: 60 * time.Second,
		DisableKeepAlives:     false,
		DisableCompression:    false,
	}

	// Proxy configuration
	if config.ProxyURL != "" {
		proxyURL, err := url.Parse(config.ProxyURL)
		if err != nil {
			return nil, fmt.Errorf("invalid proxy URL: %w", err)
		}
		transport.Proxy = http.ProxyURL(proxyURL)
	}

	// Create HTTP client with Basic Authentication Transport
	c.httpClient = &http.Client{
		Transport: &authTransport{
			Transport:   transport,
			Username:    config.Username,
			Password:    config.Password,
			BearerToken: config.BearerToken,
		},
		Timeout: config.RequestTimeout,
	}

	return c, nil
}

// Do performs a single HTTP request (NO retries).
// Caller is responsible for closing resp.Body.
func (c *Client) Do(req *http.Request) (*http.Response, error) {
	if req == nil {
		return nil, fmt.Errorf("request cannot be nil")
	}
	return c.httpClient.Do(req)
}

// DoWithRetry performs an HTTP request with exponential backoff retry logic.
// It returns the response body, status code, and error.
// The error returned corresponds to network errors or context cancellation,
// not HTTP status codes (4xx/5xx).
func (c *Client) DoWithRetry(ctx context.Context, req *http.Request) ([]byte, int, error) {
	if req == nil {
		return nil, 0, fmt.Errorf("request cannot be nil")
	}

	req = req.WithContext(ctx)

	var lastErr error
	var resp *http.Response

	for attempt := 0; attempt <= c.config.MaxRetries; attempt++ {
		if attempt > 0 {
			// Exponential backoff with Jitter
			baseDelay := c.config.RetryDelay * time.Duration(1<<uint(attempt-1)) // 1,2,4,...
			maxJitter := int64(baseDelay.Milliseconds()) + 1
			jitterBig, err := rand.Int(rand.Reader, big.NewInt(maxJitter))
			jitterMs := int64(0)
			if err == nil {
				jitterMs = jitterBig.Int64()
			}
			delay := baseDelay + time.Duration(jitterMs)*time.Millisecond

			c.logger.DebugPrintf("Retry attempt %d/%d for URL: %s", attempt, c.config.MaxRetries, req.URL.String())
			c.logger.DebugPrintf("Waiting %v before retry", delay)

			select {
			case <-ctx.Done():
				return nil, 0, ctx.Err()
			case <-time.After(delay):
			}

			// Reset request body for retries
			if req.GetBody != nil {
				newBody, err := req.GetBody()
				if err != nil {
					return nil, 0, fmt.Errorf("failed to reset request body for retry: %w", err)
				}
				req.Body = newBody
			}
		}

		start := time.Now()
		var err error
		resp, err = c.Do(req) // <-- builds on Do(req)
		duration := time.Since(start)

		if err == nil {
			c.logger.DebugPrintf("Request succeeded after %v (attempt %d). Status: %d", duration, attempt+1, resp.StatusCode)

			// If status is OK-ish (2xx), or CLIENT ERROR (4xx), return immediately (don't retry 4xx)
			if resp.StatusCode < 500 {
				defer resp.Body.Close()
				body, err := io.ReadAll(resp.Body)
				if err != nil {
					return nil, resp.StatusCode, fmt.Errorf("reading response body failed: %w", err)
				}
				return body, resp.StatusCode, nil
			}

			// 5xx: read body for diagnostics, then retry
			errorBody, _ := io.ReadAll(resp.Body)
			_ = resp.Body.Close()

			lastErr = fmt.Errorf("server error: %d, body: %s", resp.StatusCode, string(errorBody))
			continue
		}

		c.logger.DebugPrintf("Request failed after %v (attempt %d): %v", duration, attempt+1, err)
		lastErr = err

		if duration < 100*time.Millisecond {
			time.Sleep(100 * time.Millisecond) // Short pause for very fast failures
		}

		if !c.isRetryableError(err) {
			break
		}
	}

	return nil, 0, fmt.Errorf("request failed after %d attempts: %w", c.config.MaxRetries+1, lastErr)
}

// Get performs a GET request to the specified URL with retry logic.
//
// Parameters:
//   - ctx: Context for cancellation and timeout.
//   - url: The target URL.
//
// Returns:
//   - []byte: The response body.
//   - int: The HTTP status code. Returns 0 if a network error occurred.
//   - error: A network error or context error. HTTP 4xx/5xx are NOT returned as errors.
func (c *Client) Get(ctx context.Context, url string) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, 0, fmt.Errorf("creating request failed: %w", err)
	}
	return c.DoWithRetry(ctx, req)
}

// Post performs a generic POST request helper
func (c *Client) Post(ctx context.Context, url, contentType string, body []byte) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(body))
	if err != nil {
		return nil, 0, fmt.Errorf("creating request failed: %w", err)
	}
	req.Header.Set("Content-Type", contentType)

	// Enable request body replay for retries
	req.GetBody = func() (io.ReadCloser, error) {
		return io.NopCloser(bytes.NewReader(body)), nil
	}

	return c.DoWithRetry(ctx, req)
}

// GetJSON executes a GET request and unmarshals the response into the target struct.
func (c *Client) GetJSON(ctx context.Context, url string, target interface{}) error {
	body, statusCode, err := c.Get(ctx, url)
	if err != nil {
		return err
	}

	if statusCode != http.StatusOK {
		return fmt.Errorf("API error (status %d): %s", statusCode, string(body))
	}

	if err := json.Unmarshal(body, target); err != nil {
		return fmt.Errorf("failed to decode JSON response from %s: %w", url, err)
	}

	return nil
}

// PostJSONUnmarshal marshals the payload, executes a POST request, and unmarshals the response into the target struct.
func (c *Client) PostJSONUnmarshal(ctx context.Context, url string, payload interface{}, target interface{}) error {
	body, err := c.PostJSON(ctx, url, payload)
	if err != nil {
		return err
	}

	if err := json.Unmarshal(body, target); err != nil {
		return fmt.Errorf("failed to decode JSON response from %s: %w", url, err)
	}

	return nil
}

// PostJSON marshals the payload and executes a POST request, returning the response body.
// It creates a new json encoded request.
func (c *Client) PostJSON(ctx context.Context, url string, payload interface{}) ([]byte, error) {
	jsonPayload, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal payload: %w", err)
	}

	body, statusCode, err := c.Post(ctx, url, "application/json", jsonPayload)
	if err != nil {
		return nil, err
	}

	if statusCode != http.StatusOK && statusCode != http.StatusCreated {
		return nil, fmt.Errorf("API error (status %d): %s", statusCode, string(body))
	}

	return body, nil
}

// PostXML performs a POST request with XML content type
func (c *Client) PostXML(ctx context.Context, url string, body []byte) ([]byte, int, error) {
	return c.PostWithCustomHeaders(ctx, url, "text/xml", body, map[string]string{
		"Accept": "text/xml",
	})
}

// PostWithCustomHeaders performs a POST with custom headers (useful for cookie-based auth)
func (c *Client) PostWithCustomHeaders(ctx context.Context, url, contentType string, body []byte, headers map[string]string) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(body))
	if err != nil {
		return nil, 0, fmt.Errorf("creating request failed: %w", err)
	}
	req.Header.Set("Content-Type", contentType)

	// Apply custom headers
	for key, value := range headers {
		req.Header.Set(key, value)
	}

	// Enable request body replay for retries
	req.GetBody = func() (io.ReadCloser, error) {
		return io.NopCloser(bytes.NewReader(body)), nil
	}

	return c.DoWithRetry(ctx, req)
}

// isRetryableError determines if an error is transient
func (c *Client) isRetryableError(err error) bool {
	// context.Canceled and context.DeadlineExceeded are NOT retryable
	// as they indicate an intentional cancellation or a hard timeout.
	if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, context.Canceled) {
		return false
	}

	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return true
	}

	errStr := err.Error()
	if strings.Contains(errStr, "timeout") ||
		strings.Contains(errStr, "deadline exceeded") ||
		strings.Contains(errStr, "connection reset") ||
		strings.Contains(errStr, "connection refused") ||
		strings.Contains(errStr, "EOF") {
		return true
	}

	return false
}

// authTransport wraps the HTTP transport with Authentication
type authTransport struct {
	Transport   http.RoundTripper
	Username    string
	Password    string
	BearerToken string
}

func (t *authTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	req = req.Clone(req.Context())

	if t.BearerToken != "" {
		req.Header.Set("Authorization", "Bearer "+t.BearerToken)
	} else if t.Username != "" && t.Password != "" {
		auth := base64.StdEncoding.EncodeToString([]byte(t.Username + ":" + t.Password))
		req.Header.Set("Authorization", "Basic "+auth)
	}

	// Default Headers
	if req.Header.Get("Accept") == "" {
		req.Header.Set("Accept", "application/json")
	}
	if req.Header.Get("Content-Type") == "" {
		req.Header.Set("Content-Type", "application/json")
	}

	return t.Transport.RoundTrip(req)
}
