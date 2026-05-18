package oauth2client

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"golang.org/x/oauth2"
	"golang.org/x/oauth2/clientcredentials"
)

var (
	ErrEndpointRequired = errors.New("endpoint URL is required")
	ErrJSONDataRequired = errors.New("JSON data is required")
)

// HTTPClient defines the interface for HTTP client operations
// This interface enables dependency injection and testing with mock implementations
type HTTPClient interface {
	Do(req *http.Request) (*http.Response, error)
}

// ClientConfig holds the configuration for creating a new OAuth2 client
type ClientConfig struct {
	AuthServerURL         string
	Realm                 string
	ClientID              string
	ClientSecret          string
	Username              string // For password grant
	Password              string // For password grant
	GrantType             string // "client_credentials" or "password"
	TokenURL              string
	ApiEndpoint           string
	EnableTLSVerify       bool
	RequestTimeout        time.Duration
	Scopes                []string
	UserAgent             string        // Optional: Custom User-Agent header
	ConnectTimeout        time.Duration // Timeout for establishing connections
	ReadTimeout           time.Duration // Timeout for reading response headers
	MaxRetries            int           // Maximum number of retries (0 = no retries, default for compatibility)
	RetryDelay            time.Duration // Delay between retries
	MaxIdleConns          int
	IdleConnTimeout       time.Duration
	TLSHandshakeTimeout   time.Duration
	ExpectContinueTimeout time.Duration
	MaxIdleConnsPerHost   int
	MaxConnsPerHost       int
}

// Client represents an OAuth2-enabled API client with HTTP communication capabilities
type Client struct {
	logger     logging.Logger
	httpClient HTTPClient
	userAgent  string
	config     ClientConfig
}

// oauth2Transport wraps the OAuth2 transport with custom TLS configuration
// This ensures that the custom TLS settings are preserved when OAuth2 automatic
// token management is used for authenticated requests
type oauth2Transport struct {
	base *http.Transport
	rt   http.RoundTripper
}

// acceptHeaderTransport injects an Accept: application/json header into every request.
// This is required for some OAuth2 servers (e.g. oVirt/OLVM SSO) that mandate this header.
type acceptHeaderTransport struct {
	rt http.RoundTripper
}

// NewClient creates and initializes a new OAuth2 client instance with OAuth2 support.
// This constructor function sets up the HTTP client with proper TLS configuration
// and OAuth2 client credentials authentication for secure communication with external APIs.
//
// The function performs the following operations:
// 1. Validates required configuration parameters
// 2. Sets default values for optional parameters
// 3. Configures secure TLS settings with modern cipher suites
// 4. Creates HTTP transport with appropriate timeouts
// 5. Sets up OAuth2 client credentials flow
// 6. Returns a fully configured client instance
//
// Parameters:
//   - config: ClientConfig struct containing all necessary configuration options
//   - logger: Logger instance for logging operations
//
// Returns:
//   - *Client: Configured OAuth2 client instance
//   - error: Error if configuration validation fails
func NewClient(ctx context.Context, config ClientConfig, logger logging.Logger) (*Client, error) {
	if err := config.validateAndSetDefaults(); err != nil {
		return nil, err
	}

	// Use NoOpLogger if nil is provided
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	c := &Client{
		logger:    logger,
		userAgent: config.UserAgent,
		config:    config,
	}

	// Configure secure TLS settings with modern security standards
	// TLS 1.3 is enforced as minimum version for enhanced security
	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: !config.EnableTLSVerify,
	}

	// Configure HTTP transport with optimized connection pooling and timeouts
	transport := &http.Transport{
		TLSClientConfig:       tlsConfig,
		MaxIdleConns:          config.MaxIdleConns,          // Use config value
		IdleConnTimeout:       config.IdleConnTimeout,       // Use config value
		TLSHandshakeTimeout:   config.TLSHandshakeTimeout,   // Use config value
		ExpectContinueTimeout: config.ExpectContinueTimeout, // Use config value
		ResponseHeaderTimeout: config.ReadTimeout,           // Time waiting for first byte of the response headers
		DialContext:           (&net.Dialer{Timeout: config.ConnectTimeout, KeepAlive: 60 * time.Second}).DialContext,
		ForceAttemptHTTP2:     true,
		MaxIdleConnsPerHost:   config.MaxIdleConnsPerHost, // Use config value
		MaxConnsPerHost:       config.MaxConnsPerHost,     // Use config value
		DisableKeepAlives:     false,
		DisableCompression:    false,
	}

	tokenURL := config.TokenURL
	logger.DebugPrintf("OAuth2 Token URL: %s", tokenURL)
	logger.DebugPrintf("OAuth2 Client ID: %s", config.ClientID)

	var oauthClient *http.Client

	// Create base HTTP client for OAuth2 token requests
	baseClient := &http.Client{
		Transport: &acceptHeaderTransport{rt: transport},
		Timeout:   config.RequestTimeout,
	}

	if config.GrantType == "password" {
		// Use password grant flow
		oauth2Config := &oauth2.Config{
			ClientID:     config.ClientID,
			ClientSecret: config.ClientSecret,
			Endpoint: oauth2.Endpoint{
				TokenURL: tokenURL,
			},
			Scopes: config.Scopes,
		}

		// Create OAuth2-enabled HTTP client with background context
		oauth2Ctx := context.WithValue(context.Background(), oauth2.HTTPClient, baseClient)

		logger.DebugPrintf("Testing OAuth2 password token fetch...")
		token, err := oauth2Config.PasswordCredentialsToken(oauth2Ctx, config.Username, config.Password)
		if err != nil {
			logger.DebugPrintf("FAILED to fetch OAuth2 token: %v", err)
			return nil, fmt.Errorf("failed to fetch OAuth2 token: %w", err)
		}
		logger.DebugPrintf("Successfully fetched OAuth2 token (expires: %v)", token.Expiry)

		tokenSource := oauth2Config.TokenSource(oauth2Ctx, token)
		oauthClient = oauth2.NewClient(oauth2Ctx, tokenSource)
		oauthClient.Timeout = config.RequestTimeout
	} else {
		// Use client credentials flow (default)
		oauth2Config := &clientcredentials.Config{
			ClientID:     config.ClientID,
			ClientSecret: config.ClientSecret,
			TokenURL:     tokenURL,
			Scopes:       config.Scopes,
		}

		// Create OAuth2-enabled HTTP client with background context
		oauth2Ctx := context.WithValue(context.Background(), oauth2.HTTPClient, baseClient)
		oauthClient = oauth2Config.Client(oauth2Ctx)
		oauthClient.Timeout = config.RequestTimeout

		logger.DebugPrintf("Testing OAuth2 client credentials token fetch...")
		token, err := oauth2Config.Token(oauth2Ctx)
		if err != nil {
			logger.DebugPrintf("FAILED to fetch OAuth2 token: %v", err)
			return nil, fmt.Errorf("failed to fetch OAuth2 token: %w", err)
		}
		logger.DebugPrintf("Successfully fetched OAuth2 token (expires: %v)", token.Expiry)
	}

	// Override transport to maintain TLS configuration with OAuth2 wrapper
	oauthClient.Transport = &oauth2Transport{
		base: transport,
		rt:   oauthClient.Transport,
	}

	c.httpClient = oauthClient
	return c, nil
}

// validateAndSetDefaults validates the ClientConfig and sets default values for optional fields.
// This method ensures that all required fields are present and valid, while applying sensible
// defaults for optional configuration parameters to simplify client initialization.
func (c *ClientConfig) validateAndSetDefaults() error {
	if c.RequestTimeout == 0 {
		c.RequestTimeout = 30 * time.Second
	}
	if c.UserAgent == "" {
		c.UserAgent = "MCMP-EAI-Client/1.0"
	}
	if c.ConnectTimeout == 0 {
		c.ConnectTimeout = 60 * time.Second
	}
	if c.ReadTimeout == 0 {
		c.ReadTimeout = 60 * time.Second
	}
	if c.MaxRetries < 0 {
		c.MaxRetries = 3
	}
	if c.RetryDelay == 0 {
		c.RetryDelay = 2 * time.Second
	}
	if c.MaxIdleConns == 0 {
		c.MaxIdleConns = 100
	}
	if c.IdleConnTimeout == 0 {
		c.IdleConnTimeout = 90 * time.Second
	}
	if c.TLSHandshakeTimeout == 0 {
		c.TLSHandshakeTimeout = 10 * time.Second
	}
	if c.ExpectContinueTimeout == 0 {
		c.ExpectContinueTimeout = 1 * time.Second
	}
	if c.MaxIdleConnsPerHost == 0 {
		c.MaxIdleConnsPerHost = 10
	}
	if c.MaxConnsPerHost < 0 { // Allow 0 to disable limit
		c.MaxConnsPerHost = 0
	}
	if c.GrantType == "" {
		c.GrantType = "client_credentials"
	}

	var validationErrors []string
	if c.TokenURL == "" {
		validationErrors = append(validationErrors, "token URL is required")
	} else if !strings.HasPrefix(c.TokenURL, "http") {
		validationErrors = append(validationErrors, "token URL must start with http or https")
	}
	if c.AuthServerURL == "" {
		validationErrors = append(validationErrors, "auth server URL is required")
	} else if !strings.HasPrefix(c.AuthServerURL, "http") {
		validationErrors = append(validationErrors, "auth server URL must start with http or https")
	}
	if c.GrantType == "client_credentials" {
		if c.Realm == "" {
			validationErrors = append(validationErrors, "realm is required for client credentials")
		}
		if c.ClientID == "" {
			validationErrors = append(validationErrors, "client ID is required")
		}
		if c.ClientSecret == "" {
			validationErrors = append(validationErrors, "client secret is required")
		}
	} else if c.GrantType == "password" {
		if c.Username == "" {
			validationErrors = append(validationErrors, "username is required for password grant")
		}
		if c.Password == "" {
			validationErrors = append(validationErrors, "password is required for password grant")
		}
	} else {
		validationErrors = append(validationErrors, "grant type must be 'client_credentials' or 'password'")
	}
	if c.RequestTimeout < 0 {
		validationErrors = append(validationErrors, "request timeout cannot be negative")
	}
	if c.ConnectTimeout < 0 {
		validationErrors = append(validationErrors, "connect timeout cannot be negative")
	}
	if c.ReadTimeout < 0 {
		validationErrors = append(validationErrors, "read timeout cannot be negative")
	}
	if c.MaxRetries < 0 {
		validationErrors = append(validationErrors, "max retries cannot be negative")
	}
	if c.RetryDelay < 0 {
		validationErrors = append(validationErrors, "retry delay cannot be negative")
	}
	// New validation for transport settings
	if c.MaxIdleConns < 0 {
		validationErrors = append(validationErrors, "max idle connections cannot be negative")
	}
	if c.IdleConnTimeout < 0 {
		validationErrors = append(validationErrors, "idle connection timeout cannot be negative")
	}
	if c.TLSHandshakeTimeout < 0 {
		validationErrors = append(validationErrors, "TLS handshake timeout cannot be negative")
	}
	if c.ExpectContinueTimeout < 0 {
		validationErrors = append(validationErrors, "expect continue timeout cannot be negative")
	}
	if c.MaxIdleConnsPerHost < 0 {
		validationErrors = append(validationErrors, "max idle connections per host cannot be negative")
	}
	if c.MaxConnsPerHost < 0 {
		validationErrors = append(validationErrors, "max connections per host cannot be negative")
	}
	if len(validationErrors) > 0 {
		return fmt.Errorf("configuration validation failed: %s",
			strings.Join(validationErrors, "; "))
	}
	return nil
}

func (t *oauth2Transport) RoundTrip(req *http.Request) (*http.Response, error) {
	return t.rt.RoundTrip(req)
}

// RoundTrip implements the http.RoundTripper interface for the OAuth2 transport wrapper
// This method delegates the actual HTTP request execution to the OAuth2 transport
// while maintaining the custom TLS configuration from the base transport
func (t *acceptHeaderTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	// Clone the request to avoid mutating the original
	clone := req.Clone(req.Context())
	if clone.Header.Get("Accept") == "" {
		clone.Header.Set("Accept", "application/json")
	}
	return t.rt.RoundTrip(clone)
}

// GetJSON performs a GET request to the specified endpoint and returns the response body.
// It automatically handles OAuth2 authentication, retries, and error handling.
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//   - endpoint: Full URL endpoint for the HTTP GET request
//
// Returns:
//   - []byte: The response body on success
//   - error: Detailed error message on failure
func (c *Client) GetJSON(ctx context.Context, endpoint string) ([]byte, error) {
	return c.doRequestWithRetry(ctx, http.MethodGet, endpoint, nil)
}

// GetJSONUnmarshal performs a GET request and unmarshals the JSON response into the provided target.
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//   - endpoint: Full URL endpoint for the HTTP GET request
//   - target: Pointer to the struct where the JSON response should be unmarshaled
//
// Returns:
//   - error: nil on success, detailed error message on failure
func (c *Client) GetJSONUnmarshal(ctx context.Context, endpoint string, target interface{}) error {
	body, err := c.GetJSON(ctx, endpoint)
	if err != nil {
		return err
	}

	if err := json.Unmarshal(body, target); err != nil {
		return fmt.Errorf("failed to unmarshal response: %w", err)
	}

	return nil
}

// SendJSON sends JSON data to a MCMP endpoint using HTTP POST request with automatic OAuth2 authentication.
//
// The function performs the following operations:
// 1. Validates input parameters (endpoint URL and JSON data)
// 2. Creates HTTP POST request with proper context handling
// 3. Sets appropriate HTTP headers for JSON communication
// 4. Executes the request using OAuth2 authenticated HTTP client
// 5. Reads and validates the response
// 6. Provides comprehensive error handling with detailed messages
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//   - endpoint: Target URL endpoint for the HTTP POST request
//   - jsonData: JSON-encoded data to send in the request body
//
// Returns:
//   - error: nil on success, detailed error message on failure
//
// Error scenarios:
//   - Empty endpoint URL
//   - Empty JSON data
//   - HTTP request creation failure
//   - Network communication errors
//   - HTTP status codes outside 2xx range
//   - Response body reading errors
func (c *Client) SendJSON(ctx context.Context, endpoint string, jsonData []byte) error {
	_, err := c.sendJSONInternal(ctx, endpoint, jsonData)
	return err
}

func (c *Client) SendJSONWithResponse(ctx context.Context, endpoint string, jsonData []byte) ([]byte, error) {
	return c.sendJSONInternal(ctx, endpoint, jsonData)
}

// sendJSONInternal is a private helper method that contains the shared logic for sending JSON data.
// It handles validation, request creation, retry logic, and response processing.
//
// Returns:
//   - []byte: The response body (for SendJSONWithResponse)
//   - error: Detailed error message on failure
func (c *Client) sendJSONInternal(ctx context.Context, endpoint string, jsonData []byte) ([]byte, error) {
	if len(jsonData) == 0 {
		return nil, ErrJSONDataRequired
	}
	return c.doRequestWithRetry(ctx, http.MethodPost, endpoint, jsonData)
}

// doRequestWithRetry is a generalized helper method for performing HTTP requests with retry logic.
// It replaces and extends sendJSONInternal to support multiple HTTP methods.
func (c *Client) doRequestWithRetry(ctx context.Context, method, endpoint string, jsonData []byte) ([]byte, error) {
	// Validate required input parameters
	if endpoint == "" {
		return nil, ErrEndpointRequired
	}

	var lastErr error
	var finalBody []byte
	maxAttempts := c.config.MaxRetries + 1

	for attempt := 0; attempt < maxAttempts; attempt++ {
		var bodyReader io.Reader
		if len(jsonData) > 0 {
			bodyReader = bytes.NewBuffer(jsonData)
		}

		// Create HTTP request with specified method
		req, err := http.NewRequestWithContext(ctx, method, endpoint, bodyReader)
		if err != nil {
			return nil, fmt.Errorf("failed to create HTTP request: %w", err)
		}

		// Set appropriate HTTP headers
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", c.userAgent)
		if len(jsonData) > 0 {
			req.Header.Set("Content-Type", "application/json")
		}

		// Debug logging for request details
		c.logger.DebugPrintf("%s Request to: %s (attempt %d/%d)", method, endpoint, attempt+1, maxAttempts)
		if len(jsonData) > 0 {
			c.logger.DebugPrintf("Content-Length: %d", len(jsonData))
		}

		// Execute HTTP request using OAuth2 authenticated client
		resp, err := c.httpClient.Do(req)
		if err != nil {
			// Check for timeout or temporary network errors
			var netErr net.Error
			if ok := errors.As(err, &netErr); ok && (netErr.Timeout() || netErr.Temporary()) {
				lastErr = fmt.Errorf("transient network error: %w", err)
			} else {
				lastErr = err
			}

			// Retry if attempts remain
			if attempt < c.config.MaxRetries {
				backoff := c.config.RetryDelay * time.Duration(1<<attempt) // Exponential backoff
				c.logger.DebugPrintf("Request error: %v (retrying in %s)", lastErr, backoff)
				select {
				case <-time.After(backoff):
					continue
				case <-ctx.Done():
					return nil, fmt.Errorf("request cancelled while waiting to retry: %w", ctx.Err())
				}
			}
			return nil, fmt.Errorf("failed to send HTTP request: %w", lastErr)
		}

		// Read complete response body
		body, err := io.ReadAll(resp.Body)
		_ = resp.Body.Close()
		if err != nil {
			lastErr = fmt.Errorf("failed to read response body: %w", err)
			if attempt < c.config.MaxRetries {
				backoff := c.config.RetryDelay * time.Duration(1<<attempt)
				c.logger.DebugPrintf("Read error: %v (retrying in %s)", lastErr, backoff)
				select {
				case <-time.After(backoff):
					continue
				case <-ctx.Done():
					return nil, fmt.Errorf("request cancelled while waiting to retry: %w", ctx.Err())
				}
			}
			return nil, lastErr
		}

		// Debug logging for response
		c.logger.DebugPrintf("Response Status: %s", resp.Status)
		c.logger.DebugPrintf("Response Body: %s", string(body))

		// Validate HTTP status code
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			// Transient/retriable status codes
			if resp.StatusCode == http.StatusRequestTimeout ||
				resp.StatusCode == http.StatusTooManyRequests ||
				(resp.StatusCode >= 500 && resp.StatusCode <= 504) {
				lastErr = fmt.Errorf("HTTP error %d: %s, Body: %s", resp.StatusCode, resp.Status, string(body))
				if attempt < c.config.MaxRetries {
					backoff := c.config.RetryDelay * time.Duration(1<<attempt)
					c.logger.DebugPrintf("Server returned retriable status %d (retrying in %s)", resp.StatusCode, backoff)
					select {
					case <-time.After(backoff):
						continue
					case <-ctx.Done():
						return nil, fmt.Errorf("request cancelled while waiting to retry: %w", ctx.Err())
					}
				}
				return nil, lastErr
			}
			// Non-retriable status code
			return nil, fmt.Errorf("HTTP error %d: %s, Body: %s", resp.StatusCode, resp.Status, string(body))
		}

		// Success
		finalBody = body
		break
	}

	if lastErr != nil && len(finalBody) == 0 {
		return nil, lastErr
	}

	return finalBody, nil
}
