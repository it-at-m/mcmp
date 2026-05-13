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
	"strings"
	"time"

	"git.muenchen.de/mcmp/webanwendung/mcmp-eai-common/pkg/logging"
	"golang.org/x/oauth2"
	"golang.org/x/oauth2/clientcredentials"
)

const (
	urlAppservice     = "%s/cmdb/appservice"              // Application service endpoint
	urlTag            = "%s/tag/"                         // Tag management endpoint
	urlCmdbCi         = "%s/cmdb/ci/"                     // CMDB configuration item endpoint
	urlGroup          = "%s/foundation_data/group/"       // Foundation data group endpoint
	urlVMwareInstance = "%s/cmdb/cmdb_ci_vmware_instance" // urlVMwareInstance defines the endpoint for VMware instance configuration items in the CMDB.
	urlChangeNormal   = "%s/change/normal"                // Change management endpoint for Normal Changes
	urlChangeStandard = "%s/change/standard"              // Change management endpoint for Standard Changes
	urlChangeClose    = "/change/%s/close"                // urlChangeClose specifies the endpoint for closing a change record, formatted with base URL and change ID.
	urlQuickDiscovery = "%s/cmdb/quickdiscovery"          // Quick discovery endpoint
	urlChangeAddCI    = "/change/%s/add-ci"               // urlChangeAddCI specifies the endpoint for adding a CMDB configuration item to a change record, formatted with base URL and change ID.
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

	baseUrl, err := extractBaseURL(config.AuthServerURL)
	if err != nil {
		return nil, err
	}
	c.baseUrl = baseUrl
	c.urlAppservice = fmt.Sprintf(urlAppservice, config.ApiEndpoint)
	c.urlTag = fmt.Sprintf(urlTag, config.ApiEndpoint)
	c.urlCmdbCi = fmt.Sprintf(urlCmdbCi, config.ApiEndpoint)
	c.urlGroup = fmt.Sprintf(urlGroup, config.ApiEndpoint)
	c.urlVMwareInstance = fmt.Sprintf(urlVMwareInstance, config.ApiEndpoint)
	c.urlChangeNormal = fmt.Sprintf(urlChangeNormal, config.ApiEndpoint)
	c.urlChangeStandard = fmt.Sprintf(urlChangeStandard, config.ApiEndpoint)
	c.urlChangeClose = config.ApiEndpoint + urlChangeClose
	c.urlQuickDiscovery = fmt.Sprintf(urlQuickDiscovery, config.ApiEndpoint)
	c.urlChangeAddCI = config.ApiEndpoint + urlChangeAddCI

	if config.Debug {
		c.DebugPrintf("Using proxy: %s", config.ProxyURL)
	}
	ctx := context.Background()
	proxyURL, err := url.Parse(config.ProxyURL)
	if err != nil {
		return nil, fmt.Errorf("invalid proxy URL: %w", err)
	}

	tlsConfig := &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: !config.EnableTLSVerify,
	}

	proxyTransport := &http.Transport{
		TLSClientConfig:       tlsConfig,
		MaxIdleConns:          100,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
		Proxy:                 http.ProxyURL(proxyURL),
	}
	proxyClient := &http.Client{Transport: proxyTransport}
	ctx = context.WithValue(ctx, oauth2.HTTPClient, proxyClient)
	oauth2Config := &clientcredentials.Config{
		ClientID:     config.ClientID,
		ClientSecret: config.ClientSecret,
		TokenURL:     config.AuthServerURL,
		Scopes:       config.Scopes,
	}

	c.httpClient = oauth2Config.Client(ctx)
	return c, nil
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
		if res.StatusCode == http.StatusBadRequest {
			body, err := io.ReadAll(res.Body)
			if err != nil {
				return "", fmt.Errorf("HTTP status code: %d, body error: %s", res.StatusCode, err)
			}
			return "", fmt.Errorf("HTTP status code: %d, body: %s", res.StatusCode, body)
		}
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

// CreateNormalChangeTicket CreateChange creates a new change request in ServiceNow.
// This method sends a change request to the ServiceNow API and returns the created change details.
//
// Parameters:
//   - changeRequest: NormalChangeRequest struct containing callback URL and change details
//
// Returns:
//   - ChangeResponse: The created change response with system ID, number, and other details
//   - error: Any error that occurred during the API call or JSON parsing
//
// Implementation details:
// - Validates that the change request is properly structured
// - Serializes the request to JSON
// - Uses the existing postRequest method for HTTP communication
// - Handles both 200 and 201 status codes as success
// - Unmarshals the JSON response into a ChangeResponse struct
func (c *Client) CreateNormalChangeTicket(changeRequest NormalChangeRequest) (ChangeResponse, error) {
	// Validate input parameters
	if changeRequest.CallbackUrl == "" {
		return ChangeResponse{}, fmt.Errorf("callback URL is required")
	}
	if changeRequest.Change.ApplicationService == "" {
		return ChangeResponse{}, fmt.Errorf("application service is required")
	}
	if changeRequest.Change.ShortDescription == "" {
		return ChangeResponse{}, fmt.Errorf("short description is required")
	}
	if changeRequest.Change.Description == "" {
		return ChangeResponse{}, fmt.Errorf("description is required")
	}
	if changeRequest.Change.RequestedBy == "" {
		return ChangeResponse{}, fmt.Errorf("requested by is required")
	}
	if changeRequest.Change.Justification == "" {
		return ChangeResponse{}, fmt.Errorf("justification is required")
	}
	if changeRequest.Change.ImplementationPlan == "" {
		return ChangeResponse{}, fmt.Errorf("implementation plan is required")
	}
	if changeRequest.Change.RiskImpactAnalysis == "" {
		return ChangeResponse{}, fmt.Errorf("risk impact analysis is required")
	}
	if changeRequest.Change.BackoutPlan == "" {
		return ChangeResponse{}, fmt.Errorf("backout plan is required")
	}
	if changeRequest.Variables.Action == "" {
		return ChangeResponse{}, fmt.Errorf("variable action is required")
	}
	// Serialize the change request to JSON
	jsonData, err := json.Marshal(changeRequest)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to marshal change request: %w", err)
	}
	c.DebugPrintf("ServiceNow Change Request: %s\n", jsonData)

	// Make API request using the existing postRequest method
	resp, err := c.postRequest(c.urlChangeNormal, jsonData)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to create change: %w", err)
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return ChangeResponse{}, fmt.Errorf("empty response from ServiceNow")
	}

	// Parse JSON response into structured data
	var changeResponse ChangeResponse
	err = json.Unmarshal([]byte(resp), &changeResponse)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to unmarshal change response: %w", err)
	}
	return changeResponse, nil
}

// CreateStandardChangeTicket CreateChange creates a new change request in ServiceNow.
// This method sends a change request to the ServiceNow API and returns the created change details.
//
// Parameters:
//   - changeRequest: StandardChangeRequest struct containing callback URL and change details
//
// Returns:
//   - ChangeResponse: The created change response with system ID, number, and other details
//   - error: Any error that occurred during the API call or JSON parsing
//
// Implementation details:
// - Validates that the change request is properly structured
// - Serializes the request to JSON
// - Uses the existing postRequest method for HTTP communication
// - Handles both 200 and 201 status codes as success
// - Unmarshals the JSON response into a ChangeResponse struct
func (c *Client) CreateStandardChangeTicket(changeRequest StandardChangeRequest) (ChangeResponse, error) {
	// Validate input parameters
	if changeRequest.CallbackUrl == "" {
		return ChangeResponse{}, fmt.Errorf("callback URL is required")
	}
	if changeRequest.Change.ApplicationService == "" {
		return ChangeResponse{}, fmt.Errorf("application service is required")
	}
	if changeRequest.Change.ShortDescription == "" {
		return ChangeResponse{}, fmt.Errorf("short description is required")
	}
	if changeRequest.Change.Description == "" {
		return ChangeResponse{}, fmt.Errorf("description is required")
	}
	if changeRequest.Change.RequestedBy == "" {
		return ChangeResponse{}, fmt.Errorf("requested by is required")
	}
	if changeRequest.Change.StandardChangeTemplate == "" {
		return ChangeResponse{}, fmt.Errorf("standard change template is required")
	}
	// Serialize the change request to JSON
	jsonData, err := json.Marshal(changeRequest)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to marshal change request: %w", err)
	}
	c.DebugPrintf("ServiceNow Change Request: %s\n", jsonData)

	// Make API request using the existing postRequest method
	resp, err := c.postRequest(c.urlChangeStandard, jsonData)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to create change: %w", err)
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return ChangeResponse{}, fmt.Errorf("empty response from ServiceNow")
	}

	// Parse JSON response into structured data
	var changeResponse ChangeResponse
	err = json.Unmarshal([]byte(resp), &changeResponse)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to unmarshal change response: %w", err)
	}
	return changeResponse, nil
}

func (c *Client) CloseChangeTicket(changeSysId string, changeCloseRequest ChangeCloseRequest) (ChangeResponse, error) {
	// Validate input parameters
	if changeCloseRequest.CloseCode != "successful" && changeCloseRequest.CloseCode != "successful_issues" && changeCloseRequest.CloseCode != "unsuccessful" {
		return ChangeResponse{}, fmt.Errorf("invalid close code")
	}
	if changeCloseRequest.CloseNotes == "" {
		return ChangeResponse{}, fmt.Errorf("close note is required")
	}
	if changeCloseRequest.ActualStartDate == "" {
		return ChangeResponse{}, fmt.Errorf("actual start date is required")
	}
	if changeCloseRequest.ActualEndDate == "" {
		return ChangeResponse{}, fmt.Errorf("actual end date is required")
	}

	// Serialize the change request to JSON
	jsonData, err := json.Marshal(changeCloseRequest)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to marshal change close request: %w", err)
	}
	c.DebugPrintf("ServiceNow Change Close Request: %s\n", jsonData)

	// Make API request using the existing postRequest method
	changeCloseUrl := fmt.Sprintf(c.urlChangeClose, changeSysId)
	resp, err := c.postRequest(changeCloseUrl, jsonData)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to close change: %w", err)
	}

	// Clean up response string
	resp = strings.TrimSpace(resp)
	if resp == "" {
		return ChangeResponse{}, fmt.Errorf("empty response from ServiceNow")
	}

	// Parse JSON response into structured data
	var changeResponse ChangeResponse
	err = json.Unmarshal([]byte(resp), &changeResponse)
	if err != nil {
		return ChangeResponse{}, fmt.Errorf("failed to unmarshal change response: %w", err)
	}
	return changeResponse, nil
}

func (c *Client) AddCiToChangeTicket(changeSysId string, changeAddCiRequest ChangeAddCiRequest) error {
	// Validate input parameters
	if changeSysId == "" {
		return fmt.Errorf("change sys_id is required")
	}
	if changeAddCiRequest.CI == "" {
		return fmt.Errorf("ci is required")
	}

	// Serialize the change request to JSON
	jsonData, err := json.Marshal(changeAddCiRequest)
	c.DebugPrintf("ServiceNow Change Add CI Request: %s\n", jsonData)
	if err != nil {
		return fmt.Errorf("failed to marshal change add ci request: %w", err)
	}
	c.DebugPrintf("ServiceNow Change Add CI Request: %s\n", jsonData)

	// Make API request using the existing postRequest method
	changeAddCiUrl := fmt.Sprintf(c.urlChangeAddCI, changeSysId)
	c.DebugPrintf("ServiceNow Change Add CI URL: %s\n", changeAddCiUrl)
	resp, err := c.postRequest(changeAddCiUrl, jsonData)
	if err != nil {
		return fmt.Errorf("failed to change add ci: %w", err)
	}
	c.DebugPrintf("ServiceNow Change Add CI Response: %s\n", resp)
	return nil
}

func (c *Client) QuickDiscovery(quickDiscoveryRequest QuickDiscoveryRequest) error {
	if quickDiscoveryRequest.CallbackURL == "" {
		return fmt.Errorf("callback URL is required")
	}
	if quickDiscoveryRequest.DiscoveryIP == "" {
		return fmt.Errorf("discovery ip is required")
	}

	// Serialize the change request to JSON
	jsonData, err := json.Marshal(quickDiscoveryRequest)
	if err != nil {
		return fmt.Errorf("failed to marshal quick discovery request: %w", err)
	}
	c.DebugPrintf("ServiceNow Quick Discovery Request: %s\n", jsonData)

	// Make API request using the existing postRequest method
	resp, err := c.postRequest(c.urlQuickDiscovery, jsonData)
	if err != nil {
		return fmt.Errorf("failed to post quick discovery request: %w", err)
	}
	c.DebugPrintf("ServiceNow Quick Discovery Response: %s\n", resp)
	return nil
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

func (c *Client) FindVMwareInstance(uuid string) ([]CmdbCi, error) {
	limit := 1000

	url := fmt.Sprintf("%s?sysparm_limit=%d&bios_uuid="+uuid, c.urlVMwareInstance, limit)
	resp, err := c.getRequest(url)
	if err != nil {
		return nil, err
	}
	resp = strings.TrimSpace(resp)
	var vmwareInstanceResponse VMwareInstance
	err = json.Unmarshal([]byte(resp), &vmwareInstanceResponse)
	if err != nil {
		return nil, err
	}
	return vmwareInstanceResponse.Result, nil
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

func (c *Client) PostTag(tag string, ciSysId string) error {
	if tag == "" {
		return fmt.Errorf("tag parameter cannot be empty")
	}
	if ciSysId == "" {
		return fmt.Errorf("ciSysId parameter cannot be empty")
	}
	tagRequest := TagPostRequest{
		Key: "serviceid",
		CI:  ciSysId,
	}

	// Serialize the change request to JSON
	jsonData, err := json.Marshal(tagRequest)
	if err != nil {
		return fmt.Errorf("failed to marshal tag request: %w", err)
	}
	c.DebugPrintf("ServiceNow Tag Request: %s\n", jsonData)

	// Make API request using the existing postRequest method
	resp, err := c.postRequest(c.urlTag+url.PathEscape(tag), jsonData)
	if err != nil {
		return fmt.Errorf("failed to post tag request: %w", err)
	}
	c.DebugPrintf("ServiceNow Tag Response: %s\n", resp)
	return nil
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

func extractBaseURL(fullURL string) (string, error) {
	// URL parsen
	parsedURL, err := url.Parse(fullURL)
	if err != nil {
		return "", fmt.Errorf("error parsing the URL: %w", err)
	}

	// Base-URL zusammensetzen (Schema + Host)
	baseURL := fmt.Sprintf("%s://%s", parsedURL.Scheme, parsedURL.Host)

	return baseURL, nil
}

// GetChangeURL generates the URL for a ServiceNow change request based on its system ID.
// This method constructs the web interface URL for accessing a change request in ServiceNow.
//
// Parameters:
//   - sysId: The system ID of the change request (cannot be empty)
//
// Returns:
//   - string: The complete URL to the change request in ServiceNow web interface
//   - error: Any error that occurred during validation
//
// Implementation details:
// - Validates that the sysId parameter is not empty
// - Uses the baseUrl from the client configuration
// - Constructs the URL using ServiceNow's standard change request path
func (c *Client) GetChangeURL(sysId string) (string, error) {
	// Validate input parameter
	if sysId == "" {
		return "", fmt.Errorf("sysId parameter cannot be empty")
	}

	// Construct the change URL using the base URL and standard ServiceNow path
	changeURL := fmt.Sprintf("%s/nav_to.do?uri=change_request.do?sys_id=%s", c.baseUrl, sysId)

	return changeURL, nil
}
