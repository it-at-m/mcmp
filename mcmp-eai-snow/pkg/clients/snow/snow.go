package snow

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"golang.org/x/oauth2/clientcredentials"
)

// URL constants for ServiceNow API endpoints
// These constants define the API endpoints for different ServiceNow services
const (
	urlAppservice      = "%s/cmdb/appservice"              // Application service endpoint
	urlTag             = "%s/tag/"                         // Tag management endpoint
	urlCmdbCi          = "%s/cmdb/ci/"                     // urlCmdbCi defines the endpoint for Configuration Items in the CMDB module.
	urlGroup           = "%s/foundation_data/group/"       // Foundation data group endpoint
	urlVMwareInstance  = "%s/cmdb/cmdb_ci_vmware_instance" // urlVMwareInstance defines the endpoint for VMware instance configuration items in the CMDB.
	urlLockedShutdown  = "%s/cmdb/cmdb_ci_vmware_instance/locked-shutdown"
	urlLockedRightsize = "%s/cmdb/cmdb_ci_vmware_instance/locked-rightsize"
	urlVMwareServer    = "%s/cmdb/cmdb_ci_vmware_instance/%s/cmdb_ci_server"
)

// NewClient creates and initializes a new ServiceNow client instance with OAuth2 support and proxy configuration.
// This constructor function sets up the HTTP client with proper TLS configuration, proxy support,
// and OAuth2 client credentials authentication for secure communication with ServiceNow APIs.
//
// Parameters:
//   - apiEndpoint: The ServiceNow API base endpoint URL
//   - config: ClientConfig struct containing OAuth2 and proxy configuration
//
// Returns:
//   - *Client: Configured ServiceNow client instance
//   - error: Error if configuration validation fails
func NewClient(config ClientConfig) (*Client, error) {
	// Validate required configuration parameters
	if config.AuthServerURL == "" {
		return nil, fmt.Errorf("auth server URL is required")
	}
	if config.ClientID == "" {
		return nil, fmt.Errorf("client ID is required")
	}
	if config.ClientSecret == "" {
		return nil, fmt.Errorf("client secret is required")
	}
	if config.ApiEndpoint == "" {
		return nil, fmt.Errorf("API endpoint is required")
	}
	if config.ProxyURL == "" {
		return nil, fmt.Errorf("Proxy URL is required")
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

	if config.Debug {
		c.DebugLogger.EnableDebug()
	}

	// Format all API endpoint URLs with the provided endpoint
	c.urlAppservice = fmt.Sprintf(urlAppservice, config.ApiEndpoint)
	c.urlTag = fmt.Sprintf(urlTag, config.ApiEndpoint)
	c.urlCmdbCi = fmt.Sprintf(urlCmdbCi, config.ApiEndpoint)
	c.urlGroup = fmt.Sprintf(urlGroup, config.ApiEndpoint)
	c.urlVMwareInstance = fmt.Sprintf(urlVMwareInstance, config.ApiEndpoint)
	c.urlLockedShutdown = fmt.Sprintf(urlLockedShutdown, config.ApiEndpoint)
	c.urlLockedRightsize = fmt.Sprintf(urlLockedRightsize, config.ApiEndpoint)
	c.urlVMwareServer = fmt.Sprintf(urlVMwareServer, config.ApiEndpoint, "%s")

	// Configure secure TLS settings
	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: !config.EnableTLSVerify,
	}

	// Configure HTTP transport with optimized settings
	transport := &http.Transport{
		TLSClientConfig:       tlsConfig,
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
	}

	// Configure proxy if provided
	if config.ProxyURL != "" {
		proxyURL, err := url.Parse(config.ProxyURL)
		if err != nil {
			return nil, fmt.Errorf("invalid proxy URL: %w", err)
		}
		transport.Proxy = http.ProxyURL(proxyURL)
		os.Setenv("HTTP_PROXY", config.ProxyURL)
		os.Setenv("HTTPS_PROXY", config.ProxyURL)
		if config.Debug {
			c.DebugPrintf("Using proxy: %s", config.ProxyURL)
			c.DebugPrintf("Set proxy environment variables")
		}
	}

	// Configure OAuth2 client credentials flow
	// ServiceNow uses direct token endpoint without realm structure
	tokenURL := config.AuthServerURL
	oauth2Config := &clientcredentials.Config{
		ClientID:     config.ClientID,
		ClientSecret: config.ClientSecret,
		TokenURL:     tokenURL,
		Scopes:       config.Scopes,
	}

	// Create base HTTP client for OAuth2 token requests (with proxy support)
	baseClient := &http.Client{
		Transport: transport,
		Timeout:   config.RequestTimeout,
	}

	// Create OAuth2-enabled HTTP client with proxy-enabled base client
	ctx := context.WithValue(context.Background(), "oauth2.HTTPClient", baseClient)
	oauthClient := oauth2Config.Client(ctx)
	oauthClient.Timeout = config.RequestTimeout

	// Override transport to maintain TLS configuration and proxy settings with OAuth2 wrapper
	oauthClient.Transport = &oauth2Transport{
		base: transport,
		rt:   oauthClient.Transport,
	}

	c.httpClient = oauthClient
	return c, nil
}

// oauth2Transport wraps the OAuth2 transport with custom TLS configuration
type oauth2Transport struct {
	base *http.Transport
	rt   http.RoundTripper
}

func (t *oauth2Transport) RoundTrip(req *http.Request) (*http.Response, error) {
	return t.rt.RoundTrip(req)
}

// getRequest performs a generic GET request to the ServiceNow API with OAuth2 authentication
func (c *Client) getRequest(requestUrl string) (string, error) {
	// Log the request URL for debugging
	c.DebugPrintf("url: %s", requestUrl)

	// Create HTTP GET request
	req, err := http.NewRequest("GET", requestUrl, nil)
	if err != nil {
		return "", fmt.Errorf("got error %s", err.Error())
	}

	// OAuth2 authentication is handled automatically by the HTTP client
	res, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("got error %s", err.Error())
	}
	defer res.Body.Close()

	c.DebugPrintf("status code: %d", res.StatusCode)

	if res.StatusCode != http.StatusOK {
		return "", fmt.Errorf("HTTP status code: %d", res.StatusCode)
	}

	b, err := io.ReadAll(res.Body)
	if err != nil {
		return "", fmt.Errorf("got error %s", err.Error())
	}

	body := string(b)
	c.DebugPrintf("body: %s", body)

	return body, nil
}

// postRequest performs a generic POST request to the ServiceNow API with OAuth2 authentication
func (c *Client) postRequest(requestUrl string, jsonData []byte) (string, error) {
	c.DebugPrintf("POST url: %s", requestUrl)
	c.DebugPrintf("POST data length: %d", len(jsonData))

	req, err := http.NewRequest("POST", requestUrl, bytes.NewBuffer(jsonData))
	if err != nil {
		return "", fmt.Errorf("failed to create HTTP request: %w", err)
	}

	// Set appropriate headers for JSON communication
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "ServiceNow-EAI-Client/1.0")

	// OAuth2 authentication is handled automatically by the HTTP client
	res, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("got error %s", err.Error())
	}
	defer res.Body.Close()

	c.DebugPrintf("POST status code: %d", res.StatusCode)

	// Accept both 200 and 201 status codes for POST requests
	if res.StatusCode != http.StatusOK && res.StatusCode != http.StatusCreated {
		return "", fmt.Errorf("HTTP status code: %d", res.StatusCode)
	}

	b, err := io.ReadAll(res.Body)
	if err != nil {
		return "", fmt.Errorf("got error %s", err.Error())
	}

	body := string(b)
	c.DebugPrintf("POST response body: %s", body)

	return body, nil
}

// PostData sends JSON data to a ServiceNow endpoint using HTTP POST request with OAuth2 authentication
func (c *Client) PostData(ctx context.Context, endpoint string, jsonData []byte) error {
	if endpoint == "" {
		return fmt.Errorf("endpoint URL is required")
	}
	if len(jsonData) == 0 {
		return fmt.Errorf("JSON data is required")
	}

	_, err := c.postRequest(endpoint, jsonData)
	return err
}

// GetVMwareInstances retrieves a list of VMware instances from the endpoint and returns them as a slice of CmdbCi.
// It fetches all available records by setting a high limit on the API request and parses the JSON response.
func (c *Client) GetVMwareInstances() ([]CmdbCi, error) {
	var allInstances []CmdbCi
	offset := 0
	limit := 1000

	for {
		url := fmt.Sprintf("%s?sysparm_offset=%d&sysparm_limit=%d", c.urlVMwareInstance, offset, limit)
		resp, err := c.getRequest(url)
		if err != nil {
			return nil, err
		}
		resp = strings.TrimSpace(resp)
		if resp == "" {
			break
		}
		var vmwareInstanceResponse VMwareInstance
		err = json.Unmarshal([]byte(resp), &vmwareInstanceResponse)
		if err != nil {
			return nil, err
		}
		if len(vmwareInstanceResponse.Result) == 0 {
			break
		}
		allInstances = append(allInstances, vmwareInstanceResponse.Result...)
		offset += limit
	}
	return allInstances, nil
}

// GetAppServices retrieves all application services from the ServiceNow CMDB.
// This method fetches application service records with a high limit to ensure
// all records are retrieved in a single request.
//
// Returns:
//   - []AppService: A slice containing all application service records
//   - error: Any error that occurred during the API call or JSON parsing
//
// Implementation details:
// - Uses sysparm_limit=100000 to retrieve all records in one request
// - Handles empty responses gracefully by returning an empty slice
// - Unmarshals JSON response into structured AppService objects
func (c *Client) GetAppServices() ([]AppService, error) {
	// Make API request with high limit to get all records
	resp, err := c.getRequest(c.urlAppservice + "?sysparm_limit=100000&sysparm_query=sys_class_name=cmdb_ci_service_discovered^ORsys_class_name=cmdb_ci_service_by_tags^ORsys_class_name=cmdb_ci_service_auto^life_cycle_stage=Operational")
	if err != nil {
		return nil, err
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return []AppService{}, nil // Return empty slice for empty response
	}

	// Parse JSON response into structured data
	var appServiceResponse AppServiceResponse
	err = json.Unmarshal([]byte(resp), &appServiceResponse)
	if err != nil {
		return nil, err
	}

	return appServiceResponse.Result, nil
}

// GetCIsForTag retrieves all Configuration Items (CIs) associated with a specific tag.
// This method is useful for finding all infrastructure components that are tagged
// with a particular label or category.
//
// Parameters:
//   - tag: The tag name to search for (cannot be empty)
//
// Returns:
//   - []TagEntry: A slice containing all CI entries associated with the tag
//   - error: Any error that occurred during the API call or validation
//
// Implementation details:
// - Validates that the tag parameter is not empty
// - URL-escapes the tag parameter to handle special characters safely
// - Uses high limit to retrieve all matching records
// - Handles empty responses by returning an empty slice
func (c *Client) GetCIsForTag(tag string) ([]TagEntry, error) {
	// Validate input parameter
	if tag == "" {
		return nil, fmt.Errorf("tag parameter cannot be empty")
	}

	// Make API request with URL-escaped tag and high limit
	resp, err := c.getRequest(c.urlTag + url.PathEscape(tag) + "?sysparm_limit=100000&sysparm_query=configuration_item.life_cycle_stage!=End%20of%20Life^ORconfiguration_item.life_cycle_stageISEMPTY^configuration_item.sys_class_nameINSTANCEOFcmdb_ci_server^ORconfiguration_item.sys_class_nameINSTANCEOFcmdb_ci_vm_instance")
	if err != nil {
		return nil, err
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return []TagEntry{}, nil // Return empty slice for empty response
	}

	// Parse JSON response into structured data
	var tagResponse TagResponse
	err = json.Unmarshal([]byte(resp), &tagResponse)
	if err != nil {
		return nil, err
	}

	return tagResponse.Result, nil
}

// GetCmdbCI retrieves a specific Configuration Item (CI) from the CMDB by its system ID.
// This method fetches detailed information about a single CI record.
//
// Parameters:
//   - sysId: The system ID of the CI to retrieve (cannot be empty)
//
// Returns:
//   - CmdbCi: The CI record with all its attributes
//   - error: Any error that occurred during the API call or validation
//
// Implementation details:
// - Validates that the sysId parameter is not empty
// - URL-escapes the sysId to handle special characters safely
// - Returns zero value CmdbCi for empty responses
// - Unmarshals single CI record from JSON response
func (c *Client) GetCmdbCI(sysId string) (CmdbCi, error) {
	// Validate input parameter
	if sysId == "" {
		return CmdbCi{}, fmt.Errorf("sysId parameter cannot be empty")
	}

	// Make API request with URL-escaped system ID
	resp, err := c.getRequest(c.urlCmdbCi + url.PathEscape(sysId))
	if err != nil {
		return CmdbCi{}, err
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return CmdbCi{}, nil // Return zero value for empty response
	}

	// Parse JSON response into structured data
	var cmdbCiResponse CmdbCiResponse
	err = json.Unmarshal([]byte(resp), &cmdbCiResponse)
	if err != nil {
		return CmdbCi{}, err
	}

	return cmdbCiResponse.Result, nil
}

// GetFoundationData retrieves foundation data for a specific group by its system ID.
// This method fetches organizational or structural data that serves as the foundation
// for other ServiceNow operations.
//
// Parameters:
//   - sysId: The system ID of the foundation data group to retrieve (cannot be empty)
//
// Returns:
//   - FoundationData: The foundation data record with all its attributes
//   - error: Any error that occurred during the API call or validation
//
// Implementation details:
// - Validates that the sysId parameter is not empty
// - URL-escapes the sysId to handle special characters safely
// - Returns zero value FoundationData for empty responses
// - Unmarshals single foundation data record from JSON response
func (c *Client) GetFoundationData(sysId string) (FoundationData, error) {
	// Validate input parameter
	if sysId == "" {
		return FoundationData{}, fmt.Errorf("sysId parameter cannot be empty")
	}

	// Make API request with URL-escaped system ID
	resp, err := c.getRequest(c.urlGroup + url.PathEscape(sysId))
	if err != nil {
		return FoundationData{}, err
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return FoundationData{}, nil // Return zero value for empty response
	}

	// Parse JSON response into structured data
	var foundationDataResponse FoundationDataResponse
	err = json.Unmarshal([]byte(resp), &foundationDataResponse)
	if err != nil {
		return FoundationData{}, err
	}

	return foundationDataResponse.Result, nil
}

// GetLockedShutdown retrieves all records related to locked shutdown data from the API and returns them as a map.
// The map key is the ci_sys_id and the value is the task_closed_at timestamp.
// If task_closed_at is empty, it indicates an open change request.
func (c *Client) GetLockedShutdown() (map[string]string, error) {
	// Make API request with high limit to get all records
	resp, err := c.getRequest(c.urlLockedShutdown + "?sysparm_limit=100000")
	if err != nil {
		return nil, err
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return make(map[string]string), nil
	}

	// Parse JSON response into structured data
	var lockedShutdownResponse GreenItResponse
	err = json.Unmarshal([]byte(resp), &lockedShutdownResponse)
	if err != nil {
		return nil, err
	}

	// Convert slice to map
	resultMap := make(map[string]string)
	for _, item := range lockedShutdownResponse.Result {
		resultMap[item.CiSysID] = item.TaskClosedAt
	}

	return resultMap, nil
}

// GetLockedRightsize retrieves locked rightsizing data by making an API request and parsing the JSON response.
// It returns a map where the key is ci_sys_id and the value is task_closed_at.
func (c *Client) GetLockedRightsize() (map[string]string, error) {
	// Make API request with high limit to get all records
	resp, err := c.getRequest(c.urlLockedRightsize + "?sysparm_limit=100000")
	if err != nil {
		return nil, err
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return make(map[string]string), nil
	}

	// Parse JSON response into structured data
	var lockedResponse GreenItResponse
	err = json.Unmarshal([]byte(resp), &lockedResponse)
	if err != nil {
		return nil, err
	}

	// Convert slice to map
	resultMap := make(map[string]string)
	for _, item := range lockedResponse.Result {
		resultMap[item.CiSysID] = item.TaskClosedAt
	}

	return resultMap, nil
}

// GetServerForVMwareInstance retrieves the associated server for a specific VMware instance.
func (c *Client) GetServerForVMwareInstance(vmInstanceSysID string) (Server, error) {
	if vmInstanceSysID == "" {
		return Server{}, fmt.Errorf("vmInstanceSysID parameter cannot be empty")
	}

	requestUrl := fmt.Sprintf(c.urlVMwareServer, url.PathEscape(vmInstanceSysID))
	resp, err := c.getRequest(requestUrl)
	if err != nil {
		return Server{}, err
	}

	resp = strings.TrimSpace(resp)
	if resp == "" {
		return Server{}, nil
	}

	var serverResponse ServerResponse
	err = json.Unmarshal([]byte(resp), &serverResponse)
	if err != nil {
		return Server{}, err
	}

	return serverResponse.Result, nil
}
