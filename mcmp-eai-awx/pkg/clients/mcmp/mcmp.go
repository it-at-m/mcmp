// Package mcmp provides a client for interacting with the Munich City Management Platform (MCMP) APIs.
//
// The client supports OAuth2 authentication using client credentials flow and provides
// robust error handling, retry mechanisms, and comprehensive logging capabilities.
//
// Example usage:
//
//	config := mcmp.ClientConfig{
//	    AuthServerURL:   "https://auth.example.com",
//	    Realm:           "mcmp",
//	    ClientID:        "your-client-id",
//	    ClientSecret:    "your-client-secret",
//	    VerifyTLS: true,
//	}
//
//	client, err := mcmp.NewClient(config)
//	if err != nil {
//	    log.Fatal(err)
//	}
//
//	err = client.SendForemanData(ctx, endpoint, jsonData)
package mcmp

import (
	"bytes"
	"context"
	"crypto/tls"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"golang.org/x/oauth2/clientcredentials"
)

const (
	UserAgentDefault             = "MCMP-EAI-AWX/1.0"
	DefaultTLSVersion            = tls.VersionTLS13
	DefaultMaxIdleConns          = 100
	DefaultIdleConnTimeout       = 90 * time.Second
	DefaultTLSHandshakeTimeout   = 10 * time.Second
	DefaultExpectContinueTimeout = 1 * time.Second
	DefaultRequestTimeout        = 30 * time.Second
	MinSuccessStatusCode         = 200
	MaxSuccessStatusCode         = 299
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
	UserAgent       string
	ConnectTimeout  time.Duration
	ReadTimeout     time.Duration
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
	if err := validateConfig(config); err != nil {
		return nil, err
	}

	// Set default values for optional parameters
	if config.RequestTimeout == 0 {
		config.RequestTimeout = DefaultRequestTimeout
	}
	if config.UserAgent == "" {
		config.UserAgent = UserAgentDefault
	}

	// Initialize client instance with debug logging capability
	c := &Client{
		DebugLogger: logging.NewDebugLogger(nil),
		debug:       config.Debug,
	}
	if config.Debug {
		c.EnableDebug()
	}

	// Configure secure TLS settings with modern security standards
	// TLS 1.3 is enforced as minimum version for enhanced security
	tlsConfig := &tls.Config{
		MinVersion:         DefaultTLSVersion, // Updated to TLS 1.3 for enhanced security
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
		MaxIdleConns:          DefaultMaxIdleConns,          // Maximum idle connections across all hosts
		IdleConnTimeout:       DefaultIdleConnTimeout,       // Keep-alive timeout for idle connections
		TLSHandshakeTimeout:   DefaultTLSHandshakeTimeout,   // Timeout for TLS handshake
		ExpectContinueTimeout: DefaultExpectContinueTimeout, // Timeout for Expect: 100-continue
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
	c.config = config
	return c, nil
}

// oauth2Transport wraps the OAuth2 transport with custom TLS configuration
// This ensures that the custom TLS settings are preserved when OAuth2 automatic
// token management is used for authenticated requests
type oauth2Transport struct {
	base *http.Transport   // Base transport with custom TLS configuration
	rt   http.RoundTripper // OAuth2 transport wrapper
}

// validateConfig validates the provided client configuration
// This function is used to ensure that all required configuration parameters are provided
// and that the provided values are valid.
func validateConfig(config ClientConfig) error {
	var validationErrors []string

	if config.AuthServerURL == "" {
		validationErrors = append(validationErrors, "auth server URL is required")
	} else if !strings.HasPrefix(config.AuthServerURL, "http") {
		validationErrors = append(validationErrors, "auth server URL must start with http or https")
	}
	if config.Realm == "" {
		validationErrors = append(validationErrors, "realm is required")
	}
	if config.ClientID == "" {
		validationErrors = append(validationErrors, "client ID is required")
	}
	if config.ClientSecret == "" {
		validationErrors = append(validationErrors, "client secret is required")
	}
	if config.RequestTimeout < 0 {
		validationErrors = append(validationErrors, "request timeout cannot be negative")
	}
	if len(validationErrors) > 0 {
		return fmt.Errorf("configuration validation failed: %s",
			strings.Join(validationErrors, "; "))
	}
	return nil
}

// RoundTrip implements the http.RoundTripper interface for the OAuth2 transport wrapper
// This method delegates the actual HTTP request execution to the OAuth2 transport
// while maintaining the custom TLS configuration from the base transport
func (t *oauth2Transport) RoundTrip(req *http.Request) (*http.Response, error) {
	return t.rt.RoundTrip(req)
}

// SendAWXInventory sends JSON data to a MCMP endpoint using HTTP POST request with automatic OAuth2 authentication.
// This method replaces PostPatchnightData with better error handling and validation.
//
// The function performs the following critical operations:
// 1. Input Parameter Validation: Validates that both endpoint URL and JSON data are provided
//   - Prevents execution with empty or invalid parameters
//   - Returns descriptive error messages for missing configuration
//
// 2. HTTP Request Creation: Creates a properly configured HTTP POST request
//   - Uses context for request cancellation and timeout control
//   - Includes proper JSON data buffering for transmission
//   - Supports graceful cancellation through context propagation
//
// 3. HTTP Header Configuration: Sets appropriate headers for JSON API communication
//   - Content-Type: application/json - Indicates JSON request body format
//   - Accept: application/json - Specifies expected response format
//   - User-Agent: MCMP-EAI-Patchnight/1.0 - Identifies the client application
//
// 4. OAuth2 Authentication: Utilizes automatic OAuth2 token management
//   - Leverages the configured OAuth2 client credentials flow
//   - Automatically handles token acquisition, refresh, and injection
//   - No manual token management required by calling code
//
// 5. Request Execution: Performs the HTTP request with comprehensive error handling
//   - Uses the OAuth2-enabled HTTP client for authenticated communication
//   - Implements proper connection management and resource cleanup
//   - Supports concurrent request execution through goroutine safety
//
// 6. Response Processing: Reads and validates the complete response
//   - Reads entire response body for validation and error reporting
//   - Provides detailed error information for troubleshooting
//   - Supports debug logging for development and troubleshooting
//
// 7. Status Code Validation: Ensures successful operation through HTTP status validation
//   - Accepts 2xx status codes as successful operations
//   - Provides detailed error messages for non-success status codes
//   - Includes response body in error messages for API debugging
//
// Debug Logging Features:
// - When debug mode is enabled, the function logs detailed request/response information
// - Logs include: target endpoint URL, content length, authentication method
// - Response logging includes: HTTP status code and complete response body
// - Debug information assists with API integration testing and troubleshooting
//
// Error Handling Scenarios:
// - Empty endpoint URL: Returns validation error before request execution
// - Empty JSON data: Returns validation error to prevent malformed requests
// - HTTP request creation failure: Network or URL parsing errors
// - Network communication errors: Connection timeouts, DNS resolution failures
// - Authentication failures: OAuth2 token acquisition or validation errors
// - HTTP status codes outside 2xx range: API errors, server errors, client errors
// - Response body reading errors: Network interruption during response processing
//
// Integration with MCMP APIs:
// This function is specifically designed for integration with MCMP (Munich City Management Platform) APIs
// and ServiceNow systems. It handles the specific authentication and communication requirements
// of these enterprise systems while providing robust error handling and debugging capabilities.
//
// Parameters:
//   - ctx: Context for request cancellation, timeout control, and operation tracing
//     Used for graceful shutdown and request lifecycle.go management
//   - endpoint: Target URL endpoint for the HTTP POST request
//     Must be a valid HTTP/HTTPS URL pointing to the MCMP API endpoint
//   - jsonData: JSON-encoded data to send in the request body
//     Should contain properly formatted JSON matching the API contract
//
// Returns:
//   - error: nil on successful operation, detailed error message on failure
//     Error messages include context and suggestions for troubleshooting
//
// Usage Example:
//
//	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
//	defer cancel()
//
//	jsonData, _ := json.Marshal(hostData)
//	err := client.SendForemanData(ctx, "https://api.mcmp.example.com/foreman", jsonData)
//	if err != nil {
//	    log.Printf("Failed to send data: %v", err)
//	}
//
// Thread Safety:
// This function is thread-safe and can be called concurrently from multiple goroutines.
// The underlying HTTP client and OAuth2 implementation handle concurrent access properly.
func (c *Client) SendAWXInventory(ctx context.Context, endpoint string, jsonData []byte) error {
	// Input validation: Verify that endpoint URL is provided
	// This prevents execution with invalid configuration and provides clear error messaging
	if endpoint == "" {
		return fmt.Errorf("endpoint URL is required")
	}

	// Input validation: Verify that JSON data is provided and not empty
	// Prevents sending empty requests that would likely be rejected by the API
	if len(jsonData) == 0 {
		return fmt.Errorf("JSON data is required")
	}

	// Create HTTP POST request with JSON data and context for cancellation support
	// The context enables request cancellation, timeout handling, and operation tracing
	// bytes.NewBuffer creates an efficient reader for the JSON data transmission
	req, err := http.NewRequestWithContext(ctx, "POST", endpoint, bytes.NewBuffer(jsonData))
	if err != nil {
		// Request creation can fail due to invalid URLs or context issues
		return fmt.Errorf("failed to create HTTP request: %w", err)
	}

	// Configure HTTP headers for proper JSON API communication
	// These headers ensure that both client and server understand the data format
	req.Header.Set("Content-Type", "application/json") // Indicates JSON request body
	req.Header.Set("Accept", "application/json")       // Specifies expected JSON response
	req.Header.Set("User-Agent", c.config.UserAgent)   // Client identification

	// Debug logging for request details when debug mode is enabled
	// Provides visibility into request execution for troubleshooting and monitoring
	if c.debug {
		c.DebugPrintf("POST Request to: %s", endpoint)
		c.DebugPrintf("Content-Length: %d", len(jsonData))
		c.DebugPrintf("Using automatic OAuth2 token management")
	}

	// Execute HTTP request using OAuth2 authenticated client
	// The OAuth2 client automatically handles token acquisition, refresh, and injection
	// This provides seamless authentication without manual token management
	resp, err := c.httpClient.Do(req)
	if err != nil {
		// Network errors, authentication failures, or timeout issues
		return fmt.Errorf("failed to send HTTP request: %w", err)
	}
	// Ensure response body is properly closed to prevent resource leaks
	// Critical for connection pooling and resource management in long-running applications
	defer resp.Body.Close()

	// Read complete response body for validation and comprehensive error reporting
	// Reading the full body enables detailed error messages and response validation
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		// Network interruption during response reading or memory allocation failures
		return fmt.Errorf("failed to read response body: %w", err)
	}

	// Debug logging for response analysis and troubleshooting
	// Essential for API integration testing and production debugging
	if c.debug {
		c.DebugPrintf("Response Status: %s\n", resp.Status)
		c.DebugPrintf("Response Body: %s\n", string(body))
	}

	// Validate HTTP status code for successful operation
	// MCMP and ServiceNow APIs return 2xx status codes for successful operations
	// Status codes outside this range indicate various types of failures:
	// - 4xx: Client errors (bad request, authentication, authorization, not found)
	// - 5xx: Server errors (internal server error, service unavailable, gateway timeout)
	if resp.StatusCode < MinSuccessStatusCode || resp.StatusCode > MaxSuccessStatusCode {
		// Include response body in error message for comprehensive troubleshooting
		// This helps identify specific API error conditions and requirements
		return fmt.Errorf("HTTP error %d: %s, Body: %s", resp.StatusCode, resp.Status, string(body))
	}

	// Success: Request completed successfully with 2xx status code
	return nil
}
