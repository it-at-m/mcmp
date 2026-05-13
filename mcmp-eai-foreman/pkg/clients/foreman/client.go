package foreman

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/httpclient"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
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
	if config.RequestTimeout == 0 {
		config.RequestTimeout = DefaultTimeout
	}
	if config.MaxRetries == 0 {
		config.MaxRetries = MaxRetries
	}
	if config.RetryDelay == 0 {
		config.RetryDelay = DefaultRetryDelay
	}

	// Initialize client instance with debug logging capability
	c := &Client{
		DebugLogger: logging.NewDebugLogger(nil),
		debug:       config.Debug,
		config:      config,
	}
	if config.Debug {
		c.EnableDebug()
	}

	// Format all API endpoint URLs with the provided endpoint
	c.urlHosts = fmt.Sprintf(urlHosts, config.ApiEndpoint)
	c.urlHost = fmt.Sprintf(urlHost, config.ApiEndpoint, "%s") // %s placeholder für ID

	httpclientConfig := httpclient.Config{
		Username:        config.Username,
		Password:        config.Password,
		EnableTLSVerify: config.EnableTLSVerify,
		RequestTimeout:  config.RequestTimeout,
		MaxRetries:      config.MaxRetries,
		RetryDelay:      config.RetryDelay,
		ProxyURL:        config.ProxyURL,
		Debug:           config.Debug,
	}

	client, err := httpclient.NewClient(httpclientConfig, c.DebugLogger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize http client: %w", err)
	}

	c.httpClient = client
	return c, nil
}

// buildQueryString erstellt Query-String aus QueryParams
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

func (c *Client) GetAPIEndpoint() string {
	return c.config.ApiEndpoint
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

func (c *Client) GetHostsWithParams(ctx context.Context, params QueryParams) ([]Host, error) {
	queryString := c.buildQueryString(params)
	requestURL := c.urlHosts + queryString

	var hostResponse HostResponse
	if err := c.httpClient.GetJSON(ctx, requestURL, &hostResponse); err != nil {
		return nil, fmt.Errorf("getting hosts failed: %w", err)
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

	var host Host
	if err := c.httpClient.GetJSON(ctx, requestURL, &host); err != nil {
		return nil, fmt.Errorf("getting host %s failed: %w", id, err)
	}

	return &host, nil
}

// GetHostByName retrieves a host by name using search
func (c *Client) GetHostByName(name string) (*Host, error) {
	return c.GetHostByNameWithContext(context.Background(), name)
}

// GetHostByNameWithContext retrieves a host by name with context support
func (c *Client) GetHostByNameWithContext(ctx context.Context, name string) (*Host, error) {
	params := DefaultQueryParams()
	params.Search = "name = " + name
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

// Hilfsfunktion für robuste Int-Konvertierung
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

// parseHostWithWarnings versucht ein partielles Parsing mit detaillierten Warnungen
func (c *Client) parseHostWithWarnings(data []byte) (Host, []string) {
	var host Host
	var warnings []string

	// Parse in raw map für manuelle Verarbeitung
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
			// Versuche String-zu-Bool Konvertierung
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

		// Versuche normales Unmarshaling für jedes Interface
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
