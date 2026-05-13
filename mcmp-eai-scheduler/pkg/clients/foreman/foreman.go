package foreman

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"git.muenchen.de/mcmp/webanwendung/mcmp-eai-common/pkg/client/httpclient"
	"git.muenchen.de/mcmp/webanwendung/mcmp-eai-common/pkg/logging"
)

// URL constants for Foreman API endpoints
const (
	urlHosts               = "%s/hosts"
	urlHost                = "%s/hosts/%s"
	MaxParallelQueries     = 5
	DefaultParallelQueries = 3
	MaxHostsPerPage        = 100000
	DefaultTimeout         = 120 * time.Second
	MaxRetries             = 3
	DefaultRetryDelay      = 5 * time.Second
)

// NewClient creates and initializes a new Foreman client instance with Basic Authentication.
func NewClient(config ClientConfig) (*Client, error) {
	// Validate required configuration parameters
	if config.Username == "" {
		return nil, fmt.Errorf("username is required")
	}
	if config.Password == "" {
		return nil, fmt.Errorf("password is required")
	}
	if config.ApiEndpoint == "" {
		return nil, fmt.Errorf("API endpoint is required")
	}
	if config.ParallelQueries < 1 || config.ParallelQueries > MaxParallelQueries {
		config.ParallelQueries = DefaultParallelQueries
	}
	// Defaults for the http client are handled inside httpclient.NewClient if 0,
	// but we can map them here to be explicit from ClientConfig.
	if config.RequestTimeout == 0 {
		config.RequestTimeout = DefaultTimeout
	}
	if config.MaxRetries == 0 {
		config.MaxRetries = MaxRetries
	}
	if config.RetryDelay == 0 {
		config.RetryDelay = DefaultRetryDelay
	}

	// Initialize logger
	logger := logging.NewDebugLogger(nil)

	// Map to generic http client config
	httpConfig := httpclient.Config{
		Username:        config.Username,
		Password:        config.Password,
		ProxyURL:        config.ProxyURL,
		RequestTimeout:  config.RequestTimeout,
		MaxRetries:      config.MaxRetries,
		RetryDelay:      config.RetryDelay,
		EnableTLSVerify: config.EnableTLSVerify,
		Debug:           config.Debug,
	}

	// Create generic http client
	baseClient, err := httpclient.NewClient(httpConfig, logger)
	if err != nil {
		return nil, err
	}

	// Initialize client instance with debug logging capability
	c := &Client{
		DebugLogger: logger,
		httpClient:  baseClient,
		debug:       config.Debug,
		config:      config,
	}
	if config.Debug {
		c.EnableDebug()
	}

	// Format all API endpoint URLs with the provided endpoint
	c.urlHosts = fmt.Sprintf(urlHosts, config.ApiEndpoint)
	c.urlHost = fmt.Sprintf(urlHost, config.ApiEndpoint, "%s") // %s placeholder for ID

	return c, nil
}

// getRequest performs a generic GET request to the Foreman API with context support
func (c *Client) getRequest(ctx context.Context, requestUrl string) (string, error) {
	// Log the request URL for debugging
	c.DebugPrintf("url: %s", requestUrl)

	// Use the generic client's Get method which handles Retries and Basic Auth
	bodyBytes, statusCode, err := c.httpClient.Get(ctx, requestUrl)
	// Handle communication errors (timeout, connection refused, etc.)
	if err != nil {
		return "", fmt.Errorf("request failed: %w", err)
	}

	// Handle API specific errors based on Status Code
	c.DebugPrintf("status code: %d", statusCode)

	if statusCode != http.StatusOK {
		apiErr := &APIError{
			StatusCode: statusCode,
			Message:    http.StatusText(statusCode),
			Details:    string(bodyBytes),
		}
		return "", apiErr
	}

	body := string(bodyBytes)
	c.DebugPrintf("body: %s", body)

	return body, nil
}

// buildQueryString creates a query string from QueryParams
func (c *Client) buildQueryString(params QueryParams) string {
	values := url.Values{}

	if params.Page > 0 {
		values.Set("page", strconv.Itoa(params.Page))
	}
	if params.PerPage > 0 {
		values.Set("per_page", strconv.Itoa(params.PerPage))
	}
	if params.Search != "" {
		values.Set("search", params.Search)
	}
	if params.OrderBy != "" {
		values.Set("order", params.OrderBy)
		if params.OrderDir != "" {
			values.Set("order", params.OrderBy+" "+params.OrderDir)
		}
	}
	if params.ThinResults {
		values.Set("thin", "true")
	}

	if len(values) > 0 {
		return "?" + values.Encode()
	}
	return ""
}

func (c *Client) GetConfiguredParallelQueries() int {
	return c.config.ParallelQueries
}

// GetAllHosts retrieves all hosts from Foreman API with default parameters
func (c *Client) GetAllHosts() ([]Host, error) {
	return c.GetAllHostsWithContext(context.Background())
}

// GetAllHostsWithContext retrieves all hosts with context support
func (c *Client) GetAllHostsWithContext(ctx context.Context) ([]Host, error) {
	params := DefaultQueryParams()
	params.PerPage = MaxHostsPerPage
	return c.GetHostsWithParams(ctx, params)
}

// GetHostsWithParams retrieves hosts with custom query parameters
func (c *Client) GetHostsWithParams(ctx context.Context, params QueryParams) ([]Host, error) {
	queryString := c.buildQueryString(params)
	requestURL := c.urlHosts + queryString

	// Direct call to getRequest, retry logic is now internal
	resp, err := c.getRequest(ctx, requestURL)
	if err != nil {
		return nil, fmt.Errorf("getting hosts failed: %w", err)
	}

	resp = strings.TrimSpace(resp)
	if resp == "" || resp == "null" {
		return []Host{}, nil
	}

	var hostResponse HostResponse
	if err := json.Unmarshal([]byte(resp), &hostResponse); err != nil {
		return nil, fmt.Errorf("unmarshaling hosts response failed: %w", err)
	}

	return hostResponse.Results, nil
}

// GetHost retrieves a specific host by ID from Foreman API
func (c *Client) GetHost(id string) (*Host, error) {
	return c.GetHostWithContext(context.Background(), id)
}

// GetHostWithContext retrieves a specific host with context support and robust JSON parsing
func (c *Client) GetHostWithContext(ctx context.Context, id string) (*Host, error) {
	requestURL := fmt.Sprintf(c.urlHost, id)

	// Direct call to getRequest, retry logic is now internal
	resp, err := c.getRequest(ctx, requestURL)
	if err != nil {
		return nil, fmt.Errorf("getting host %s failed: %w", id, err)
	}

	resp = strings.TrimSpace(resp)
	if resp == "" {
		return nil, nil
	}

	// Robust JSON parsing with fallback mechanism
	var host Host

	// First attempt: full host object
	if err := json.Unmarshal([]byte(resp), &host); err == nil {
		return &host, nil
	}

	c.DebugPrintf("Warning: Full host parsing failed for ID %s: %v", id, err)
	c.DebugPrintf("Attempting robust parsing with essential fields only...")

	// Second attempt: essential fields only with RobustHost
	var robustHost RobustHost
	if err := json.Unmarshal([]byte(resp), &robustHost); err == nil {
		c.DebugPrintf("Successfully parsed host %s with robust mode", id)
		return robustHost.ToHost(), nil
	}

	c.DebugPrintf("Warning: Robust parsing also failed for ID %s: %v", id, err)

	// Dritter Versuch: Manuelles Parsing mit Warnungen (als letzter Ausweg)
	host, warnings := c.parseHostWithWarnings([]byte(resp))
	if len(warnings) > 0 {
		c.DebugPrintf("Warnings during manual parsing for ID %s:", id)
		for _, warning := range warnings {
			c.DebugPrintf("  - %s", warning)
		}
	}
	return &host, nil
}

func (c *Client) GetHostByNameWithContext(ctx context.Context, name string) (*Host, error) {
	params := DefaultQueryParams()
	params.Search = "name=" + name
	params.PerPage = 1

	hosts, err := c.GetHostsWithParams(ctx, params)
	if err != nil {
		return nil, err
	}

	if len(hosts) == 0 {
		return nil, nil
	}

	return &hosts[0], nil
}

// GetHostByName retrieves a host by name using search
func (c *Client) GetHostByName(name string) (*Host, error) {
	return c.GetHostByNameWithContext(context.Background(), name)
}

// Helper function for robust int conversion
func convertToInt(value interface{}) (int, error) {
	switch v := value.(type) {
	case float64:
		return int(v), nil
	case string:
		return strconv.Atoi(v)
	case int:
		return v, nil
	default:
		return 0, fmt.Errorf("cannot convert %T to int", v)
	}
}

// parseHostWithWarnings attempts a partial parse with detailed warnings
func (c *Client) parseHostWithWarnings(data []byte) (Host, []string) {
	var host Host
	var warnings []string

	// Parse into raw map for manual processing
	var rawData map[string]interface{}
	if err := json.Unmarshal(data, &rawData); err != nil {
		warnings = append(warnings, fmt.Sprintf("Failed to parse JSON: %v", err))
		return host, warnings
	}

	// Manually parse essential fields with error handling
	if id, exists := rawData["id"]; exists {
		if intVal, err := convertToInt(id); err == nil {
			host.ID = intVal
		} else {
			warnings = append(warnings, fmt.Sprintf("Could not parse 'id' field: %v", err))
		}
	}

	if name, exists := rawData["name"]; exists {
		if nameStr, ok := name.(string); ok {
			host.Name = nameStr
		} else {
			host.Name = fmt.Sprintf("%v", name)
			warnings = append(warnings, fmt.Sprintf("'name' field is not a string, converted to: %s", host.Name))
		}
	}

	c.parseStringField(rawData, "ip", &host.IP, &warnings)
	c.parseSimpleStringField(rawData, "mac", &host.Mac, &warnings)
	c.parseSimpleStringField(rawData, "uuid", &host.UUID, &warnings)
	return host, warnings
}

func (c *Client) parseStringField(rawData map[string]interface{}, fieldName string, target **string, warnings *[]string) {
	if value, exists := rawData[fieldName]; exists && value != nil {
		if strVal, ok := value.(string); ok && strVal != "" {
			*target = &strVal
		} else {
			converted := fmt.Sprintf("%v", value)
			*target = &converted
			*warnings = append(*warnings, fmt.Sprintf("Field '%s' is not a string, converted to: %s", fieldName, converted))
		}
	}
}

func (c *Client) parseSimpleStringField(rawData map[string]interface{}, fieldName string, target *string, warnings *[]string) {
	if value, exists := rawData[fieldName]; exists && value != nil {
		if strVal, ok := value.(string); ok {
			*target = strVal
		} else {
			converted := fmt.Sprintf("%v", value)
			*target = converted
			*warnings = append(*warnings, fmt.Sprintf("Field '%s' is not a string, converted to: %s", fieldName, converted))
		}
	}
}

func (c *Client) parseIntField(rawData map[string]interface{}, fieldName string, target *int, warnings *[]string) {
	if value, exists := rawData[fieldName]; exists {
		if intVal, err := convertToInt(value); err == nil {
			*target = intVal
		} else {
			*warnings = append(*warnings, fmt.Sprintf("Could not parse '%s' field: %v", fieldName, err))
		}
	}
}

func (c *Client) parseBoolField(rawData map[string]interface{}, fieldName string, target *bool, warnings *[]string) {
	if value, exists := rawData[fieldName]; exists {
		if boolVal, ok := value.(bool); ok {
			*target = boolVal
		} else {
			if strVal, ok := value.(string); ok {
				if parsedBool, err := strconv.ParseBool(strVal); err == nil {
					*target = parsedBool
				} else {
					*warnings = append(*warnings, fmt.Sprintf("Could not parse '%s' as bool: %v", fieldName, err))
				}
			} else {
				*warnings = append(*warnings, fmt.Sprintf("Field '%s' is not a boolean", fieldName))
			}
		}
	}
}

func (c *Client) parseInterfaces(interfaceSlice []interface{}, warnings *[]string) []Interface {
	var interfaces []Interface

	for i, iface := range interfaceSlice {
		var parsedInterface Interface

		// Attempt normal unmarshaling for each interface
		if ifaceBytes, err := json.Marshal(iface); err == nil {
			if err := json.Unmarshal(ifaceBytes, &parsedInterface); err == nil {
				interfaces = append(interfaces, parsedInterface)
			} else {
				*warnings = append(*warnings, fmt.Sprintf("Could not parse interface at index %d: %v", i, err))
			}
		}
	}

	return interfaces
}
