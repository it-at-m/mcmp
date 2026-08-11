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
	urlAppservice      = "%s/api/x_lam_lhm_cmp/cmdb/appservice"        // Application service endpoint
	urlTag             = "%s/api/x_lam_lhm_cmp/tag/"                   // Tag management endpoint
	urlCmdbCi          = "%s/api/x_lam_lhm_cmp/cmdb/ci/"               // urlCmdbCi defines the endpoint for Configuration Items in the CMDB module.
	urlGroup           = "%s/api/x_lam_lhm_cmp/foundation_data/group/" // Foundation data group endpoint
	urlLockedShutdown  = "%s/api/x_lam_lhm_cmp/cmdb/cmdb_ci_vmware_instance/locked-shutdown"
	urlLockedRightsize = "%s/api/x_lam_lhm_cmp/cmdb/cmdb_ci_vmware_instance/locked-rightsize"
	urlVMwareServer    = "%s/api/x_lam_lhm_cmp/cmdb/cmdb_ci_vmware_instance/%s/cmdb_ci_server"

	urlCmdbKeyValue       = "%s/api/x_lam_lhm_api_gw/foundation/data/table/cmdb_key_value"
	urlCmdbDataTable      = "%s/api/x_lam_lhm_api_gw/cmdb/data/table/%s"
	urlOraclePdbToServer  = "%s/api/x_lam_lhm_api_gw/view/data/table/x_lam_lhm_api_gw_oracle_pdb_to_server"
	urlDbInstanceToServer = "%s/api/x_lam_lhm_api_gw/view/data/table/x_lam_lhm_api_gw_db_instance_to_server"

	urlIdentifyReconcile = "%s/api/now/identifyreconcile/enhanced"
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
		return nil, fmt.Errorf("proxy URL is required")
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
	c.urlLockedShutdown = fmt.Sprintf(urlLockedShutdown, config.ApiEndpoint)
	c.urlLockedRightsize = fmt.Sprintf(urlLockedRightsize, config.ApiEndpoint)
	c.urlVMwareServer = fmt.Sprintf(urlVMwareServer, config.ApiEndpoint, "%s")
	c.urlCmdbKeyValue = fmt.Sprintf(urlCmdbKeyValue, config.ApiEndpoint)
	c.urlCmdbDataTable = fmt.Sprintf(urlCmdbDataTable, config.ApiEndpoint, "%s")
	c.urlIdentifyReconcile = fmt.Sprintf(urlIdentifyReconcile, config.ApiEndpoint)
	c.urlOraclePdbToServer = fmt.Sprintf(urlOraclePdbToServer, config.ApiEndpoint)
	c.urlDbInstanceToServer = fmt.Sprintf(urlDbInstanceToServer, config.ApiEndpoint)

	// Configure secure TLS settings
	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: !config.EnableTLSVerify,
	}

	// Configure HTTP transport with optimized settings
	transport := &http.Transport{
		TLSClientConfig:       tlsConfig,
		MaxIdleConns:          1000,
		IdleConnTimeout:       900 * time.Second,
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

	cmdbTagClassNames := []string{
		"cmdb_ci_server",
		"cmdb_ci_vm_instance",
		//	"cmdb_ci_storage_volume",
		//	"cmdb_ci_cloud_object_storage",
		//	"cmdb_ci_lb_service",
		//	"cmdb_ci_kubernetes_namespace",
	}
	classQuery := buildTagClassQuery(cmdbTagClassNames)
	query := fmt.Sprintf("configuration_item.life_cycle_stage!=End of Life^ORconfiguration_item.life_cycle_stageISEMPTY%s", classQuery)
	requestUrl := fmt.Sprintf("%s%s?sysparm_limit=100000&sysparm_query=%s",
		c.urlTag,
		url.PathEscape(tag),
		url.QueryEscape(query))
	resp, err := c.getRequest(requestUrl)
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

// GetCmdbDataTable performs a universal GET request to the ServiceNow CMDB data table endpoint.
// It allows querying any table with custom query parameters, fields, and limits.
func (c *Client) getCmdbDataTable(params GetCmdbDataTableParams) ([]map[string]any, error) {
	if params.TableName == "" {
		return nil, fmt.Errorf("table name is required")
	}

	// Build the request URL using the table name
	tableUrl := fmt.Sprintf(c.urlCmdbDataTable, strings.TrimSpace(params.TableName))
	requestUrl, err := url.Parse(tableUrl)
	if err != nil {
		return nil, fmt.Errorf("failed to parse table URL: %w", err)
	}

	var allResults []map[string]any
	offset := 0
	limit := params.Limit
	if limit <= 0 {
		limit = 5000
	}
	for {

		// Set query parameters
		q := requestUrl.Query()
		if params.Query != "" {
			q.Set("sysparm_query", params.Query)
		}
		q.Set("sysparm_limit", fmt.Sprintf("%d", limit))
		q.Set("sysparm_offset", fmt.Sprintf("%d", offset))

		if len(params.Fields) > 0 {
			q.Set("sysparm_fields", strings.Join(params.Fields, ","))
		}
		if params.ExcludeReferenceLink {
			q.Set("sysparm_exclude_reference_link", "true")
		}

		requestUrl.RawQuery = q.Encode()

		// Execute request
		resp, err := c.getRequest(requestUrl.String())
		if err != nil {
			return nil, err
		}

		resp = strings.TrimSpace(resp)
		if resp == "" {
			return []map[string]any{}, nil
		}

		// Parse generic JSON result
		var result struct {
			Result []map[string]any `json:"result"`
		}
		err = json.Unmarshal([]byte(resp), &result)
		if err != nil {
			return nil, fmt.Errorf("failed to unmarshal table response: %w", err)
		}

		if len(result.Result) == 0 {
			break
		}

		allResults = append(allResults, result.Result...)
		offset += limit

		if params.Limit > 0 && len(allResults) >= params.Limit {
			break
		}
	}

	return allResults, nil
}

// fetchCmdbKeyValue performs the actual API request to the cmdb_key_value table.
func (c *Client) fetchCmdbKeyValue(query string, fields []string) ([]map[string]any, error) {
	requestUrl, err := url.Parse(c.urlCmdbKeyValue)
	if err != nil {
		return nil, err
	}

	q := requestUrl.Query()
	q.Set("sysparm_query", query)
	q.Set("sysparm_limit", "1000000")
	q.Set("sysparm_fields", strings.Join(fields, ","))

	requestUrl.RawQuery = q.Encode()

	resp, err := c.getRequest(requestUrl.String())
	if err != nil {
		return nil, err
	}

	resp = strings.TrimSpace(resp)
	if resp == "" {
		return nil, nil
	}

	var result struct {
		Result []map[string]any `json:"result"`
	}
	err = json.Unmarshal([]byte(resp), &result)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	return result.Result, nil
}

// GetCmdbKeyValue retrieves data from the cmdb_key_value table and groups it by Configuration Item SysID.
func (c *Client) getCmdbKeyValue(query string, fields []string) (map[string]map[string]struct{}, error) {
	results, err := c.fetchCmdbKeyValue(query, fields)
	if err != nil {
		return nil, err
	}

	grouped := make(map[string]map[string]struct{})
	for _, res := range results {
		sysID, _ := res["configuration_item.sys_id"].(string)
		if sysID == "" {
			continue
		}

		val, _ := res["value"].(string)
		if _, exists := grouped[sysID]; !exists {
			grouped[sysID] = make(map[string]struct{})
		}
		if val != "" {
			grouped[sysID][val] = struct{}{}
		}
	}

	return grouped, nil
}

func (c *Client) getCmdbKeyValueServiceId(sysClassName string) (map[string]map[string]struct{}, error) {
	query := fmt.Sprintf("key=serviceid^configuration_item.sys_class_nameINSTANCEOF%s^configuration_item.life_cycle_stage!=End of Life^ORconfiguration_item.life_cycle_stageISEMPTY", strings.TrimSpace(sysClassName))
	fields := []string{
		"configuration_item.sys_id",
		"value",
	}

	return c.getCmdbKeyValue(query, fields)
}

func (c *Client) getCmdbStoragegridTenantId(sysClassName string) ([]map[string]any, error) {
	query := fmt.Sprintf("key=storagegrid-tenant-id^configuration_item.sys_class_name=%s", strings.TrimSpace(sysClassName))
	fields := []string{
		"configuration_item.sys_id",
		"value",
		"configuration_item.name",
		"configuration_item.sys_class_name",
	}

	return c.fetchCmdbKeyValue(query, fields)
}

func (c *Client) GetCmdbCiCloudServiceAccountData() ([]ConfigurationItemWithAppServices, error) {
	return c.getConfigurationItemsWithAppServices("cmdb_ci_cloud_service_account", "", nil)
}

func (c *Client) GetCmdbCiCloudObjectStorageData() ([]ConfigurationItemWithAppServices, error) {
	return c.getConfigurationItemsWithAppServices("cmdb_ci_cloud_object_storage", "", nil)
}

func (c *Client) getConfigurationItemsWithAppServices(tableName string, query string, fields []string) ([]ConfigurationItemWithAppServices, error) {
	var rawCIs []map[string]any
	var err error
	if tableName == "cmdb_ci_cloud_service_account" || tableName == "cmdb_ci_cloud_object_storage" {
		rawCIs, err = c.getCmdbStoragegridTenantId(tableName)
		if err != nil {
			return nil, fmt.Errorf("failed to fetch cloud service account or cloud object storage data: %w", err)
		}
	} else {
		// 1. Fetch RAW CI Data
		rawCIs, err = c.getCmdbDataTable(GetCmdbDataTableParams{
			TableName:            tableName,
			Query:                query,
			Fields:               fields,
			ExcludeReferenceLink: true,
		})
		if err != nil {
			return nil, fmt.Errorf("failed to fetch data for table %s: %w", tableName, err)
		}

	}
	// 2. Fetch KeyValue Mappings using the provided loader
	keyValues, err := c.getCmdbKeyValueServiceId(tableName)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch key values for table %s: %w", tableName, err)
	}

	// 3. Aggregate data
	results := make([]ConfigurationItemWithAppServices, 0, len(rawCIs))
	for _, raw := range rawCIs {
		ci := NewConfigurationItemWithAppServices(raw)
		sysID := ci.GetSysID()

		if appServices, ok := keyValues[sysID]; ok {
			for appServiceNumber := range appServices {
				ci.AddAppServiceNumber(appServiceNumber)
			}
		}
		results = append(results, ci)
	}

	return results, nil
}

// GetOraclePdbToServerMapping retrieves the mapping between Oracle PDBs, Oracle Instances, and Servers.
// It returns two maps:
// Map1: key orapdb_sys_id, value = set (map[string]struct{}) of orainstance_sys_id
// Map2: key orapdb_sys_id, value = set (map[string]struct{}) of server_sys_id
func (c *Client) GetOraclePdbToServerMapping() (map[string]map[string]struct{}, map[string]map[string]struct{}, error) {
	requestUrl := c.urlOraclePdbToServer + "?sysparm_fields=orapdb_sys_id,orainstance_sys_id,server_sys_id"
	resp, err := c.getRequest(requestUrl)
	if err != nil {
		return nil, nil, err
	}

	resp = strings.TrimSpace(resp)
	if resp == "" {
		return make(map[string]map[string]struct{}), make(map[string]map[string]struct{}), nil
	}

	var response OraclePdbToServerResponse
	err = json.Unmarshal([]byte(resp), &response)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to unmarshal oracle pdb mapping: %w", err)
	}

	pdbToInstance := make(map[string]map[string]struct{})
	pdbToServer := make(map[string]map[string]struct{})

	for _, item := range response.Result {
		if item.OraPdbSysID == "" {
			continue
		}

		// Fill PDB to Instance Map
		if item.OraInstanceSysID != "" {
			if _, ok := pdbToInstance[item.OraPdbSysID]; !ok {
				pdbToInstance[item.OraPdbSysID] = make(map[string]struct{})
			}
			pdbToInstance[item.OraPdbSysID][item.OraInstanceSysID] = struct{}{}
		}

		// Fill PDB to Server Map
		if item.ServerSysID != "" {
			if _, ok := pdbToServer[item.OraPdbSysID]; !ok {
				pdbToServer[item.OraPdbSysID] = make(map[string]struct{})
			}
			pdbToServer[item.OraPdbSysID][item.ServerSysID] = struct{}{}
		}
	}

	return pdbToInstance, pdbToServer, nil
}

// GetDbInstanceToServerMapping retrieves the mapping between Database Instances and Servers.
// It returns a map where the key is dbinstance_sys_id and the value is a set (map[string]struct{}) of server_sys_id.
func (c *Client) GetDbInstanceToServerMapping() (map[string]map[string]struct{}, error) {
	requestUrl := c.urlDbInstanceToServer + "?sysparm_fields=dbinstance_sys_id,server_sys_id"
	resp, err := c.getRequest(requestUrl)
	if err != nil {
		return nil, err
	}

	resp = strings.TrimSpace(resp)
	if resp == "" {
		return make(map[string]map[string]struct{}), nil
	}

	var response DbInstanceToServerResponse
	err = json.Unmarshal([]byte(resp), &response)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal db instance mapping: %w", err)
	}

	dbToServers := make(map[string]map[string]struct{})

	for _, item := range response.Result {
		if item.DbInstanceSysID == "" {
			continue
		}

		if item.ServerSysID != "" {
			if _, ok := dbToServers[item.DbInstanceSysID]; !ok {
				dbToServers[item.DbInstanceSysID] = make(map[string]struct{})
			}
			dbToServers[item.DbInstanceSysID][item.ServerSysID] = struct{}{}
		}
	}

	return dbToServers, nil
}

// GetKubernetesNamespaceData retrieves data for Kubernetes Namespaces.
func (c *Client) GetPackageRepositoryData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
	}

	return c.getConfigurationItemsWithAppServices(
		"x_lam_lhm_packag_0_cmdb_ci_package_repository",
		"life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

// GetKubernetesNamespaceData retrieves data for Kubernetes Namespaces.
func (c *Client) GetKubernetesNamespaceData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"k8s_uid",
		"environment",
		"cluster.name",
		"cluster.sys_id",
		"cluster.sys_class_name",
		"cluster.last_discovered",
		"cluster.life_cycle_stage",
		"cluster.life_cycle_stage_status",
		"cluster.k8s_uid",
		"cluster.environment",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_kubernetes_namespace",
		"life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

// GetStorageServerData retrieves data for Storage Volumes.
func (c *Client) GetStorageServerData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"serial_number",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_server",
		"sys_class_name=cmdb_ci_storage_server^serial_numberISNOTEMPTY^duplicate_ofISEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

// GetStorageVolumeData retrieves data for Storage Volumes.
func (c *Client) GetStorageVolumeData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"storage_type",
		"cluster_id",
		"volume_id",
		"computer.serial_number",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_storage_volume",
		"storage_typeISEMPTY^sys_class_name=cmdb_ci_storage_volume^volume_idISNOTEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

// GetStorageQTreeData retrieves data for Storage Volumes.
func (c *Client) GetStorageQTreeData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"storage_type",
		"cluster_id",
		"volume_id",
		"object_id",
		"computer.serial_number",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_storage_volume",
		"storage_type=QTree^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

// GetLbServiceData retrieves data for Loadbalancer Services.
func (c *Client) GetLbServiceData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_lb_service",
		"life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetVMwareInstanceData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"operational_status",
		"vm_instance_uuid",
		"state",
		"bios_uuid",
		"object_id",
		"vcenter_uuid",
		"template",
		"fqdn",
		"ip_address",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_vmware_instance",
		"life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetCmdbCiServerData() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"operational_status",
		"dns_domain",
		"manufacturer",
		"os_version",
		"serial_number",
		"install_date",
		"fqdn",
		"hardware_status",
		"install_status",
		"default_gateway",
		"company",
		"os",
		"ip_address",
		"model_id",
		"environment",
		"host_name",
		"mac_address",
		"os_domain",
		"virtual",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_server",
		"life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetCmdbCiDbOraPdbInstance() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"sid",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_db_ora_pdb_instance",
		"duplicate_ofISEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetCmdbCiDbOraInstance() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"install_directory",
		"running_process_command",
		"sid",
		"tcp_port",
		"version",
		"pfile",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_db_ora_instance",
		"duplicate_ofISEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetCmdbCiDbMySQLInstance() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"install_directory",
		"running_process_command",
		"config_file",
		"tcp_port",
		"version",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_db_mysql_instance",
		"duplicate_ofISEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetCmdbCiDbPostgreSQLInstance() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"install_directory",
		"running_process_command",
		"config_file",
		"tcp_port",
		"version",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_db_postgresql_instance",
		"duplicate_ofISEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetCmdbCiDbMongoDbInstance() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"running_process_command",
		"config_file",
		"tcp_port",
		"version",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_db_mongodb_instance",
		"duplicate_ofISEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

func (c *Client) GetCmdbCiDbMSSQLInstance() ([]ConfigurationItemWithAppServices, error) {
	fields := []string{
		"sys_id",
		"name",
		"sys_class_name",
		"last_discovered",
		"life_cycle_stage",
		"life_cycle_stage_status",
		"install_directory",
		"running_process_command",
		"tcp_port",
		"version",
		"instance_name",
	}

	return c.getConfigurationItemsWithAppServices(
		"cmdb_ci_db_mssql_instance",
		"duplicate_ofISEMPTY^life_cycle_stageISEMPTY^ORlife_cycle_stage=Operational",
		fields,
	)
}

// IdentifyReconcile sends a payload to the ServiceNow Identify and Reconcile API.
// This endpoint allows creating or updating Configuration Items (CIs) based on identification rules.
func (c *Client) IdentifyReconcile(ctx context.Context, payload IdentifyReconcilePayload) (*IdentifyReconcileResponse, error) {
	jsonData, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal identify reconcile payload: %w", err)
	}

	respBody, err := c.postRequest(c.urlIdentifyReconcile, jsonData)
	if err != nil {
		return nil, err
	}

	var response IdentifyReconcileResponse
	err = json.Unmarshal([]byte(respBody), &response)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal identify reconcile response: %w", err)
	}

	return &response, nil
}

// IdentifyReconcilePackageRepository sends a specific payload for a package repository to ServiceNow.
// It sets the provided name and uses the current time as source_recency_timestamp.
func (c *Client) IdentifyReconcilePackageRepository(ctx context.Context, repositoryName string) (*IdentifyReconcileResponse, error) {
	if repositoryName == "" {
		return nil, fmt.Errorf("repository name is required")
	}

	payload := IdentifyReconcilePayload{
		Items: []IdentifyReconcileItem{
			{
				ClassName: "x_lam_lhm_packag_0_cmdb_ci_package_repository",
				Lookup:    []any{},
				Values: map[string]any{
					"name":                    repositoryName,
					"life_cycle_stage":        "Operational",
					"life_cycle_stage_status": "In Use",
				},
				InternalID: "package_repo",
				SysObjectSourceInfo: &SysObjectSourceInfo{
					SourceName:             "MCMP",
					SourceRecencyTimestamp: time.Now().Format("2006-01-02 15:04:05"),
				},
			},
		},
	}

	return c.IdentifyReconcile(ctx, payload)
}

func buildTagClassQuery(classes []string) string {
	if len(classes) == 0 {
		return ""
	}
	var queries []string
	for _, class := range classes {
		queries = append(queries, fmt.Sprintf("configuration_item.sys_class_nameINSTANCEOF%s", class))
	}
	return "^" + strings.Join(queries, "^OR")
}
