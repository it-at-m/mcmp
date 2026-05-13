package patchnight

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// API endpoint constants for patchnight services
// These constants define the API endpoints for different patchnight services
const (
	endpointLinuxPatchnightDate       = "/patchnight_datum.json"  // Endpoint for patchnight date schedules
	endpointLinuxPatchnightInclude    = "/patchnight_includ.json" // Endpoint for servers included in patchnight
	endpointLinuxPatchnightExclude    = "/patchnight_exclud.json" // Endpoint for servers excluded from patchnight
	endpointWindowsPatchnightDate     = "/windows/patchnight_win_datum.json"
	endpointWindowsPatchnightIncludeK = "/windows/k-server.json"
	endpointWindowsPatchnightIncludeP = "/windows/p-server.json"
	endpointWindowsPatchnightExclude  = "/windows/manuelle-server.json"
	endpointWindowsPatchnightStatus   = "/windows/UpdateStatus_Report.json"
)

// Regular expression for valid hostname format
// Matches DNS naming conventions with proper label validation
var hostnameRegex = regexp.MustCompile(`^[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?)*$`)

// ClientConfig holds the configuration for creating a new patchnight client
// This structure encapsulates all necessary parameters for HTTP client configuration
// including security settings, timeout values, and retry behavior
type ClientConfig struct {
	// Hostname specifies the target server hostname for patchnight API calls
	Hostname string

	// Debug enables verbose logging for troubleshooting API communication
	Debug bool

	// EnableTLSVerify controls whether TLS certificate verification is enforced
	// Should be true in production environments for security
	EnableTLSVerify bool

	// RequestTimeout defines the maximum duration for individual HTTP requests
	// Includes connection establishment, request sending, and response reading
	RequestTimeout time.Duration

	// MaxRetries specifies the maximum number of retry attempts for failed requests
	// Applies to retryable errors like temporary network issues or server errors
	MaxRetries int

	// RetryDelay sets the base delay between retry attempts
	// Actual delay increases exponentially with each retry (exponential backoff)
	RetryDelay time.Duration

	// UserAgent string identifies the client in HTTP requests
	// Used for logging and monitoring purposes on the server side
	UserAgent string
}

func applyClientConfigDefaults(cfg ClientConfig) ClientConfig {
	if cfg.RequestTimeout == 0 {
		cfg.RequestTimeout = 30 * time.Second
	}
	if cfg.MaxRetries == 0 {
		cfg.MaxRetries = 3
	}
	if cfg.RetryDelay == 0 {
		cfg.RetryDelay = 1 * time.Second
	}
	if cfg.UserAgent == "" {
		cfg.UserAgent = "MCMP-EAI-Patchnight/1.0"
	}
	return cfg
}

// NewClient creates and initializes a new patchnight client instance with enhanced security and reliability.
// This constructor validates all configuration parameters and establishes secure HTTP client settings.
// The client uses modern TLS configuration, connection pooling, and retry mechanisms for robustness.
//
// Security features:
// - TLS 1.2 minimum with strong cipher suites
// - Certificate verification (configurable)
// - Connection timeouts and limits
// - Request size limits
//
// Reliability features:
// - Exponential backoff retry logic
// - Connection pooling and reuse
// - Configurable timeouts
// - Context-based cancellation support
//
// Parameters:
//   - config: ClientConfig structure containing all client configuration options
//
// Returns:
//   - *Client: Configured client instance ready for API operations
//   - error: Configuration validation error or nil on success
func NewClient(config ClientConfig) (*Client, error) {
	// Validate required configuration parameters
	if config.Hostname == "" {
		return nil, fmt.Errorf("hostname is required")
	}

	// Validate and normalize hostname format
	// Removes whitespace and validates DNS name format
	hostname := strings.TrimSpace(config.Hostname)
	if !isValidHostname(hostname) {
		return nil, fmt.Errorf("invalid hostname format: %s", hostname)
	}

	// Apply default configuration values for optional parameters
	// These defaults provide reasonable behavior for most use cases
	config = applyClientConfigDefaults(config)

	// Initialize client structure with configuration
	c := &Client{
		DebugLogger: logging.NewDebugLogger(nil),
		config:      config,
	}
	if config.Debug {
		c.DebugLogger.EnableDebug()
	}

	// Create secure base URL for API endpoints
	// Uses HTTPS for all communications
	baseURL, err := url.Parse(fmt.Sprintf("https://%s", hostname))
	if err != nil {
		return nil, fmt.Errorf("failed to parse hostname as URL: %w", err)
	}
	c.baseURL = baseURL

	// Configure secure TLS settings with modern security standards
	// Uses TLS 1.2 minimum and strong cipher suites for data protection
	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: !config.EnableTLSVerify,
		CipherSuites: []uint16{
			tls.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
			tls.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
			tls.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			tls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
		},
	}

	// Configure HTTP transport with optimized connection settings
	// Enables connection pooling and reuse for better performance
	transport := &http.Transport{
		TLSClientConfig:       tlsConfig,
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
		DisableCompression:    false,
	}

	// Create HTTP client with configured transport and timeout
	// The timeout applies to the entire request lifecycle.go
	c.httpClient = &http.Client{
		Transport: transport,
		Timeout:   config.RequestTimeout,
	}

	return c, nil
}

// EnableDebug activates debug logging for the patchnight client.
// When enabled, the client will log detailed information about HTTP requests,
// responses, and internal processing steps. This is valuable for troubleshooting
// API communication issues but should be disabled in production for performance.
func (c *Client) EnableDebug() {
	c.debug = true
}

// SetUserAgent allows setting a custom User-Agent header for HTTP requests.
// The User-Agent identifies the client application and version to the server,
// which is useful for monitoring, analytics, and debugging purposes.
//
// Parameters:
//   - userAgent: Custom User-Agent string to use in HTTP requests
func (c *Client) SetUserAgent(userAgent string) {
	if userAgent != "" {
		c.config.UserAgent = userAgent
	}
}

// executeGetRequest performs a secure HTTP GET request with retry logic and proper error handling.
// This method implements the core HTTP communication logic with several reliability features:
//
// Retry Logic:
// - Exponential backoff for temporary failures
// - Configurable retry attempts and delays
// - Context-aware cancellation support
//
// Security Features:
// - Secure headers (User-Agent, Cache-Control, Accept)
// - TLS certificate verification
// - Response size limiting
//
// Error Handling:
// - Distinguishes between retryable and permanent errors
// - Provides detailed error context
// - Handles network timeouts and server errors
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//   - endpoint: API endpoint path to append to base URL
//
// Returns:
//   - []byte: Response body data on success
//   - error: Detailed error information on failure
func (c *Client) executeGetRequest(ctx context.Context, endpoint string) ([]byte, error) {
	// Construct complete URL by combining base URL with endpoint
	requestURL := c.baseURL.ResolveReference(&url.URL{Path: endpoint})

	var lastErr error
	// Retry loop with exponential backoff
	for attempt := 0; attempt <= c.config.MaxRetries; attempt++ {
		// Apply exponential backoff delay for retry attempts
		if attempt > 0 {
			c.DebugPrintf("Retry attempt %d for URL: %s", attempt, requestURL.String())

			// Calculate exponential backoff delay
			delay := c.config.RetryDelay * time.Duration(1<<(attempt-1))
			select {
			case <-ctx.Done():
				return nil, ctx.Err()
			case <-time.After(delay):
			}
		}

		// Create HTTP request with context for cancellation support
		req, err := http.NewRequestWithContext(ctx, "GET", requestURL.String(), nil)
		if err != nil {
			lastErr = fmt.Errorf("failed to create HTTP request: %w", err)
			continue
		}

		// Set secure and informative HTTP headers
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", c.config.UserAgent)
		req.Header.Set("Cache-Control", "no-cache")

		c.DebugPrintf("Executing GET request to URL: %s (attempt %d)", requestURL.String(), attempt+1)

		// Execute HTTP request with configured client
		resp, err := c.httpClient.Do(req)
		if err != nil {
			// If the context has been canceled or its deadline exceeded,
			// abort immediately instead of retrying.
			if ctxErr := ctx.Err(); ctxErr != nil {
				return nil, ctxErr
			}

			lastErr = fmt.Errorf("HTTP request failed: %w", err)
			continue
		}

		// Process response and handle errors
		body, err := c.handleResponse(resp)
		if err != nil {
			lastErr = err
			// Check if error is retryable (server errors, timeouts, etc.)
			if isRetryableError(resp.StatusCode) {
				continue
			}
			break
		}

		c.DebugPrintf("Successfully received response with %d bytes", len(body))
		return body, nil
	}

	return nil, fmt.Errorf("request failed after %d attempts: %w", c.config.MaxRetries+1, lastErr)
}

// handleResponse processes HTTP response and extracts body with proper error handling.
// This method implements several safety and security measures:
//
// Security Features:
// - Response size limiting (prevents memory exhaustion attacks)
// - Proper resource cleanup with deferred close
// - Input validation and sanitization
//
// Error Handling:
// - HTTP status code validation
// - Comprehensive error messages with context
// - Graceful handling of malformed responses
//
// Parameters:
//   - resp: HTTP response object to process
//
// Returns:
//   - []byte: Response body content
//   - error: Processing error or nil on success
func (c *Client) handleResponse(resp *http.Response) ([]byte, error) {
	// Ensure response body is always closed to prevent resource leaks
	defer resp.Body.Close()

	c.DebugPrintf("Received HTTP status code: %d", resp.StatusCode)

	// Read response body with size limitation to prevent memory exhaustion
	const maxResponseSize = 10 * 1024 * 1024 // 10MB limit

	// Read up to maxResponseSize+1 bytes to detect if the body is larger
	limitedReader := io.LimitReader(resp.Body, maxResponseSize+1)
	body, err := io.ReadAll(limitedReader)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	// Check if response was truncated due to size limit
	if len(body) > maxResponseSize {
		return nil, fmt.Errorf("response body too large (>%d bytes)", maxResponseSize)
	}

	// Validate HTTP status code range
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("HTTP error %d: %s, Body: %s", resp.StatusCode, resp.Status, string(body))
	}

	// Log response body for debugging purposes
	if c.debug {
		c.DebugPrintf("Response body received: %s", string(body))
	}

	return body, nil
}

// fetchAndUnmarshal fetches data from the specified endpoint and unmarshals the JSON response into the provided target.
// It uses the provided context for the HTTP request and returns an error if the request or unmarshaling fails.
func fetchAndUnmarshal[T any](ctx context.Context, c *Client, endpoint string, target *T) error {
	body, err := c.executeGetRequest(ctx, endpoint)
	if err != nil {
		return err
	}

	body = bytes.TrimPrefix(body, []byte("\xef\xbb\xbf"))
	bodyStr := strings.TrimSpace(string(body))
	if bodyStr == "" {
		// Target remains empty (zero value of T)
		return nil
	}

	if err := json.Unmarshal([]byte(bodyStr), target); err != nil {
		fmt.Printf("\n\nBody:\n%s\n\n", bodyStr)
		return fmt.Errorf("failed to parse %s JSON: %w", endpoint, err)
	}
	return nil
}

func (c *Client) FetchLinuxPatchnightDates(ctx context.Context) ([]PatchnightDate, error) {
	return c.fetchPatchnightDates(ctx, endpointLinuxPatchnightDate)
}

func (c *Client) FetchWindowsPatchnightDates(ctx context.Context) ([]PatchnightDate, error) {
	return c.fetchPatchnightDates(ctx, endpointWindowsPatchnightDate)
}

// fetchPatchnightDates retrieves all patchnight date schedules from the API with context support.
// This method fetches the complete list of scheduled patchnight dates across all environments.
// Each patchnight date includes timing information and environment targeting.
//
// Data Processing:
// - Handles empty API responses gracefully
// - Validates all received data structures
// - Provides comprehensive error context
//
// Error Handling:
// - Network communication errors
// - JSON parsing failures
// - Data validation errors
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//
// Returns:
//   - []PatchnightDate: List of scheduled patchnight dates
//   - error: Detailed error information on failure
func (c *Client) fetchPatchnightDates(ctx context.Context, endpoint string) ([]PatchnightDate, error) {
	var resp PatchnightDateResponse
	if err := fetchAndUnmarshal(ctx, c, endpoint, &resp); err != nil {
		return nil, fmt.Errorf("failed to fetch %s patchnight dates: %w", endpoint, err)
	}

	if len(resp.PatchnightDates) == 0 {
		return []PatchnightDate{}, nil
	}

	// Instead of aborting on the first error, skip invalid entries and log them
	validDates := make([]PatchnightDate, 0, len(resp.PatchnightDates))
	for i, date := range resp.PatchnightDates {
		if err := date.Validate(); err != nil {
			c.ErrorPrintf(
				"Skipping invalid patchnight date at index %d: env=%q date=%q error=%v",
				i, date.Environment, date.Date, err,
			)
			continue
		}
		validDates = append(validDates, date)
	}

	if len(validDates) == 0 {
		return []PatchnightDate{}, nil
	}
	return validDates, nil
}

// FetchLinuxIncludedServers retrieves servers included in patchnight operations with context support.
// This method fetches the complete list of servers that participate in patchnight maintenance.
// Each server includes scheduling information, environment targeting, and timing windows.
//
// Data Processing:
// - Handles empty API responses gracefully
// - Validates server configuration data
// - Provides comprehensive error context
//
// Error Handling:
// - Network communication errors
// - JSON parsing failures
// - Server configuration validation errors
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//
// Returns:
//   - []PatchnightLinuxIncludedServer: List of servers included in patchnight operations
//   - error: Detailed error information on failure
func (c *Client) FetchLinuxIncludedServers(ctx context.Context) ([]PatchnightLinuxIncludedServer, error) {
	var resp PatchnightIncludedServersResponse
	if err := fetchAndUnmarshal(ctx, c, endpointLinuxPatchnightInclude, &resp); err != nil {
		return nil, fmt.Errorf("failed to fetch included servers: %w", err)
	}

	servers := resp.PatchnightIncludedServers

	// Skip invalid entries and log them instead of aborting completely
	validServers := make([]PatchnightLinuxIncludedServer, 0, len(servers))
	for i, server := range servers {
		if err := server.Validate(); err != nil {
			c.ErrorPrintf(
				"Skipping invalid included server at index %d: name=%q env=%q start_time=%q end_time=%q error=%v",
				i, server.Name, server.Environment, server.StartTime, server.EndTime, err,
			)
			continue
		}
		validServers = append(validServers, server)
	}

	if len(validServers) == 0 {
		return []PatchnightLinuxIncludedServer{}, nil
	}
	return validServers, nil
}

// FetchLinuxExcludedServers retrieves servers excluded from patchnight operations with context support.
// This method fetches the complete list of servers that are excluded from patchnight maintenance.
// These servers are tracked for completeness but won't receive maintenance windows.
//
// Data Processing:
// - Handles empty API responses gracefully
// - Validates basic server information
// - Provides comprehensive error context
//
// Error Handling:
// - Network communication errors
// - JSON parsing failures
// - Server information validation errors
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//
// Returns:
//   - []PatchnightLinuxExcludedServer: List of servers excluded from patchnight operations
//   - error: Detailed error information on failure
func (c *Client) FetchLinuxExcludedServers(ctx context.Context) ([]PatchnightLinuxExcludedServer, error) {
	var resp PatchnightExcludedServersResponse
	if err := fetchAndUnmarshal(ctx, c, endpointLinuxPatchnightExclude, &resp); err != nil {
		return nil, fmt.Errorf("failed to fetch excluded servers: %w", err)
	}

	servers := resp.PatchnightLinuxExcludedServers

	// Skip invalid entries and log them
	validServers := make([]PatchnightLinuxExcludedServer, 0, len(servers))
	for i, server := range servers {
		if err := server.Validate(); err != nil {
			c.ErrorPrintf(
				"Skipping invalid excluded server at index %d: name=%q os=%q os_version=%q error=%v",
				i, server.Name, server.OS, server.OSVersion, err,
			)
			continue
		}
		validServers = append(validServers, server)
	}

	if len(validServers) == 0 {
		return []PatchnightLinuxExcludedServer{}, nil
	}
	return validServers, nil
}

func (c *Client) FetchWindowsKIncludedServers(ctx context.Context) ([]string, error) {
	return c.fetchWindowsServers(ctx, endpointWindowsPatchnightIncludeK)
}

func (c *Client) FetchWindowsPIncludedServers(ctx context.Context) ([]string, error) {
	return c.fetchWindowsServers(ctx, endpointWindowsPatchnightIncludeP)
}

func (c *Client) FetchWindowsExcludedServers(ctx context.Context) ([]string, error) {
	return c.fetchWindowsServers(ctx, endpointWindowsPatchnightExclude)
}

func (c *Client) fetchWindowsServers(ctx context.Context, endpoint string) ([]string, error) {
	var servers []WindowsServer
	if err := fetchAndUnmarshal(ctx, c, endpoint, &servers); err != nil {
		return nil, fmt.Errorf("failed to fetch %s servers: %w", endpoint, err)
	}
	if len(servers) == 0 {
		return []string{}, nil
	}

	result := make([]string, 0, len(servers))
	for _, s := range servers {
		name := strings.TrimSpace(s.FullDomainName)
		if name != "" {
			result = append(result, name)
		}
	}
	return result, nil
}

func (c *Client) FetchWindowsUpdateStatus(ctx context.Context) ([]WindowsPatchnightStatus, error) {
	var statuses []WindowsPatchnightStatus
	if err := fetchAndUnmarshal(ctx, c, endpointWindowsPatchnightStatus, &statuses); err != nil {
		return nil, fmt.Errorf("failed to fetch windows patchnight status: %w", err)
	}

	if len(statuses) == 0 {
		return []WindowsPatchnightStatus{}, nil
	}

	// Skip invalid entries and log them
	validStatuses := make([]WindowsPatchnightStatus, 0, len(statuses))
	for i := range statuses {
		if err := statuses[i].Validate(); err != nil {
			c.ErrorPrintf(
				"Skipping invalid windows patchnight status at index %d: server=%q error=%v",
				i, statuses[i].Server, err,
			)
			continue
		}
		validStatuses = append(validStatuses, statuses[i])
	}

	if len(validStatuses) == 0 {
		return []WindowsPatchnightStatus{}, nil
	}
	return validStatuses, nil
}

// isValidHostname validates whether a hostname conforms to DNS naming standards.
// This function implements RFC-compliant hostname validation with the following rules:
//
// Validation Rules:
// - Length between 1 and 253 characters
// - Contains only alphanumeric characters, hyphens, and dots
// - Labels cannot start or end with hyphens
// - Labels cannot exceed 63 characters
// - Must follow DNS naming conventions
//
// Parameters:
//   - hostname: OntapHostname string to validate
//
// Returns:
//   - bool: true if hostname is valid, false otherwise
//
// isValidHostname validates whether a hostname conforms to DNS naming standards.
func isValidHostname(hostname string) bool {
	// Check length constraints
	if len(hostname) == 0 || len(hostname) > 253 {
		return false
	}

	return hostnameRegex.MatchString(hostname)
}

// isRetryableError determines whether an HTTP error should trigger a retry attempt.
// This function categorizes HTTP status codes into retryable and non-retryable errors:
//
// Retryable Errors (temporary, may succeed on retry):
// - 500 Internal Server Error
// - 502 Bad Gateway
// - 503 Service Unavailable
// - 504 Gateway Timeout
// - 429 Too Many Requests
//
// Non-Retryable Errors (permanent, will not succeed on retry):
// - 4xx Client Errors (authentication, authorization, bad request)
// - Other 5xx Server Errors not listed above
//
// Parameters:
//   - statusCode: HTTP status code to evaluate
//
// Returns:
//   - bool: true if error should trigger retry, false for permanent errors

func isRetryableError(statusCode int) bool {
	switch statusCode {
	case http.StatusInternalServerError,
		http.StatusBadGateway,
		http.StatusServiceUnavailable,
		http.StatusGatewayTimeout,
		http.StatusTooManyRequests:
		return true
	default:
		return false
	}
}
