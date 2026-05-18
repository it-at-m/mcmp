package mcmp

import (
	"bytes"
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"golang.org/x/oauth2/clientcredentials"
)

// ClientConfig holds the configuration for creating a new MCMP client
type ClientConfig struct {
	Debug           bool
	AuthServerURL   string
	Realm           string
	ClientID        string
	ClientSecret    string
	EnableTLSVerify bool
	RequestTimeout  time.Duration
	Scopes          []string
}

// NewClient creates and initializes a new MCMP client instance with OAuth2 support.
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
//   - Debug: Enable debug logging for troubleshooting
//   - AuthServerURL: Base URL of the OAuth2 authentication server
//   - Realm: Authentication realm identifier
//   - ClientID: OAuth2 client identifier
//   - ClientSecret: OAuth2 client secret for authentication
//   - VerifyTLS: Whether to verify TLS certificates (default: true)
//   - RequestTimeout: HTTP request timeout (default: 30 seconds)
//   - Scopes: OAuth2 scopes to request
//
// Returns:
//   - *Client: Configured MCMP client instance
//   - error: Error if configuration validation fails
func NewClient(config ClientConfig) (*Client, error) {
	// Validate required configuration parameters
	if config.AuthServerURL == "" {
		return nil, fmt.Errorf("auth server URL is required")
	}
	if config.Realm == "" {
		return nil, fmt.Errorf("realm is required")
	}
	if config.ClientID == "" {
		return nil, fmt.Errorf("client ID is required")
	}
	if config.ClientSecret == "" {
		return nil, fmt.Errorf("client secret is required")
	}

	// Set default values for optional parameters
	if config.RequestTimeout == 0 {
		config.RequestTimeout = 30 * time.Second
	}

	// Initialize client instance with debug logging capability
	c := &Client{
		DebugLogger: logging.NewDebugLogger(nil),
		debug:       config.Debug,
	}

	// Configure secure TLS settings with modern security standards
	// TLS 1.3 is enforced as minimum version for enhanced security
	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS13, // Updated to TLS 1.3 for enhanced security
		InsecureSkipVerify: !config.EnableTLSVerify,
		CipherSuites: []uint16{
			tls.TLS_AES_256_GCM_SHA384,       // Primary cipher suite for TLS 1.3
			tls.TLS_CHACHA20_POLY1305_SHA256, // Alternative cipher suite for performance
			tls.TLS_AES_128_GCM_SHA256,       // Fallback cipher suite
		},
	}

	// Configure HTTP transport with optimized connection pooling and timeouts
	transport := &http.Transport{
		TLSClientConfig:       tlsConfig,
		MaxIdleConns:          100,              // Maximum idle connections across all hosts
		IdleConnTimeout:       90 * time.Second, // Keep-alive timeout for idle connections
		TLSHandshakeTimeout:   10 * time.Second, // Timeout for TLS handshake
		ExpectContinueTimeout: 1 * time.Second,  // Timeout for Expect: 100-continue
	}

	// Configure OAuth2 client credentials flow
	// This creates the token endpoint URL using the provided auth server and realm
	tokenURL := fmt.Sprintf("%s/realms/%s/protocol/openid-connect/token", config.AuthServerURL, config.Realm)
	oauth2Config := &clientcredentials.Config{
		ClientID:     config.ClientID,
		ClientSecret: config.ClientSecret,
		TokenURL:     tokenURL,
		Scopes:       config.Scopes,
	}

	// Create base HTTP client for OAuth2 token requests
	baseClient := &http.Client{
		Transport: transport,
		Timeout:   config.RequestTimeout,
	}

	// Create OAuth2-enabled HTTP client with background context
	// The background context is NOT cancelled to ensure OAuth2 token requests work properly
	ctx := context.WithValue(context.Background(), "oauth2.HTTPClient", baseClient)
	oauthClient := oauth2Config.Client(ctx)
	oauthClient.Timeout = config.RequestTimeout

	// Override transport to maintain TLS configuration with OAuth2 wrapper
	oauthClient.Transport = &oauth2Transport{
		base: transport,
		rt:   oauthClient.Transport,
	}

	c.httpClient = oauthClient
	return c, nil
}

// oauth2Transport wraps the OAuth2 transport with custom TLS configuration
// This ensures that the custom TLS settings are preserved when OAuth2 automatic
// token management is used for authenticated requests
type oauth2Transport struct {
	base *http.Transport   // Base transport with custom TLS configuration
	rt   http.RoundTripper // OAuth2 transport wrapper
}

// RoundTrip implements the http.RoundTripper interface for the OAuth2 transport wrapper
// This method delegates the actual HTTP request execution to the OAuth2 transport
// while maintaining the custom TLS configuration from the base transport
func (t *oauth2Transport) RoundTrip(req *http.Request) (*http.Response, error) {
	return t.rt.RoundTrip(req)
}

// SendSNowData SendPatchnightData sends JSON data to a MCMP endpoint using HTTP POST request with automatic OAuth2 authentication.
// This method replaces PostPatchnightData with better error handling and validation.
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
func (c *Client) SendSNowData(ctx context.Context, endpoint string, jsonData []byte) error {
	// Validate required input parameters
	if endpoint == "" {
		return fmt.Errorf("endpoint URL is required")
	}
	if len(jsonData) == 0 {
		return fmt.Errorf("JSON data is required")
	}

	// Create HTTP POST request with JSON data and context for cancellation
	req, err := http.NewRequestWithContext(ctx, "POST", endpoint, bytes.NewBuffer(jsonData))
	if err != nil {
		return fmt.Errorf("failed to create HTTP request: %w", err)
	}

	// Set appropriate HTTP headers for JSON communication
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "MCMP-EAI-Patchnight/1.0")

	// Debug logging for request details when debug mode is enabled
	if c.debug {
		c.DebugPrintf("POST Request to: %s", endpoint)
		c.DebugPrintf("Content-Length: %d", len(jsonData))
		c.DebugPrintf("Using automatic OAuth2 token management")
	}

	// Execute HTTP request using OAuth2 authenticated client
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to send HTTP request: %w", err)
	}
	defer resp.Body.Close()

	// Read complete response body for validation and error reporting
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read response body: %w", err)
	}

	// Debug logging for response data
	// Helps with API troubleshooting and integration testing
	if c.debug {
		c.DebugPrintf("Response Status: %s\n", resp.Status)
		c.DebugPrintf("Response Body: %s\n", string(body))
	}

	// Validate HTTP status code for success
	// ServiceNow APIs typically return 2xx status codes for successful operations
	// Status codes outside this range indicate various types of failures:
	// - 4xx: Client errors (bad request, authentication, authorization)
	// - 5xx: Server errors (internal server error, service unavailable)
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("HTTP error %d: %s, Body: %s", resp.StatusCode, resp.Status, string(body))
	}

	return nil
}
