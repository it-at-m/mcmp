package snow

import (
	"errors"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// MockHttpClient provides a mock implementation of HTTP client for testing purposes.
// This mock allows unit tests to simulate various HTTP response scenarios without
// making actual network requests to external services.
//
// The mock uses a function field (DoFunc) that can be configured by test cases
// to return specific responses or errors, enabling comprehensive testing of
// HTTP client interactions and error handling paths.
type MockHttpClient struct {
	// DoFunc is a configurable function that simulates the http.Client.Do method.
	// Test cases can set this function to return specific HTTP responses or errors
	// to test different scenarios such as successful responses, network errors,
	// invalid JSON, or various HTTP status codes.
	DoFunc func(req *http.Request) (*http.Response, error)
}

// Do executes the configured DoFunc to simulate an HTTP request.
// This method satisfies the HTTP client interface used by the ServiceNow client.
//
// Parameters:
//   - req: The HTTP request to be executed (typically ignored in mock scenarios)
//
// Returns:
//   - *http.Response: The HTTP response as configured by the test case
//   - error: Any error as configured by the test case
func (m *MockHttpClient) Do(req *http.Request) (*http.Response, error) {
	return m.DoFunc(req)
}

// TestGetAppServices validates the GetAppServices method functionality across multiple scenarios.
// This comprehensive test suite covers both successful operations and various error conditions
// to ensure robust error handling and proper data parsing.
//
// Test scenarios covered:
// - Valid JSON response with complete application service data
// - Invalid/malformed JSON response handling
// - Non-200 HTTP status code handling
// - HTTP client network errors
// - Empty response handling
//
// Each test case verifies both the returned data structure and error conditions
// to ensure the method behaves correctly in all situations.
func TestGetAppServices(t *testing.T) {
	// Define comprehensive test cases covering success and failure scenarios
	tests := []struct {
		name           string         // Descriptive name for the test case
		httpResponse   *http.Response // Mock HTTP response to return
		httpError      error          // Mock HTTP error to return
		expectedResult []AppService   // Expected parsed application services
		expectedError  error          // Expected error condition
	}{
		{
			// Test case: Valid response with complete application service data
			// This tests the happy path where ServiceNow returns well-formed JSON
			// containing all required fields for application services
			name: "valid response",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body: io.NopCloser(strings.NewReader(`
{
  "result": [{
    "sys_id": "00000000000000000000000000000001",
    "name": "Service P",
    "number": "SNSVC0000001",
    "change_control": "00000000000000000000000000000002",
    "assignment_group": "00000000000000000000000000000003",
    "used_for": "Production",
    "environment": "Production",
    "owned_by": {
      "sys_id": "00000000000000000000000000000004",
      "name": "Anonymized User A",
      "email": "anonymized.user.a@example.com",
      "department": "Team 1",
      "company": "Example Corp",
      "user_id": "anonymized.user.a",
      "source": null,
      "locked_out": true,
      "active": false
    },
    "service_owner_delegate": {
      "sys_id": "00000000000000000000000000000005",
      "name": "Anonymized User B",
      "email": "anonymized.user.b@example.com",
      "department": "Team 2",
      "company": "Example Corp",
      "user_id": "anonymized.user.b",
      "source": null,
      "locked_out": false,
      "active": true
    },
    "csw_enforced": false,
    "deprecated_do_not_use_for_new_development_business_service_number": [
      "BSN0000001",
      "BSN0000002"
    ]
  }]
}
`)),
			},
			expectedResult: []AppService{
				{
					SysID:          "00000000000000000000000000000001",
					Name:           "Service P",
					Number:         "SNSVC0000001",
					ChangeControl:  "00000000000000000000000000000002",
					AssignmenGroup: "00000000000000000000000000000003",
					UsedFor:        "Production",
					Environment:    "Production",
					CSWEnforced:    false,
					OwnedBy: User{
						SysID:      "00000000000000000000000000000004",
						Name:       "Anonymized User A",
						Email:      "anonymized.user.a@example.com",
						Department: "Team 1",
						Company:    "Example Corp",
						UserID:     "anonymized.user.a",
						LockedOut:  true,
						Active:     false,
					},
					ServiceOwnerDelegate: User{
						SysID:      "00000000000000000000000000000005",
						Name:       "Anonymized User B",
						Email:      "anonymized.user.b@example.com",
						Department: "Team 2",
						Company:    "Example Corp",
						UserID:     "anonymized.user.b",
						LockedOut:  false,
						Active:     true,
					},
					BusinessServiceNumbers: []string{
						"BSN0000001",
						"BSN0000002",
					},
				},
			},
			expectedError: nil,
		},
		{
			// Test case: Invalid JSON response handling
			// This tests the error handling when ServiceNow returns malformed JSON
			// ensuring the application doesn't crash on parsing errors
			name: "invalid JSON response",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(`{"result":[{`)),
			},
			expectedResult: nil,
			expectedError:  errors.New("unexpected end of JSON input"),
		},
		{
			// Test case: Non-200 HTTP status code handling
			// This tests the error handling when ServiceNow API returns server errors
			// ensuring proper error propagation for debugging
			name: "non-200 status code",
			httpResponse: &http.Response{
				StatusCode: http.StatusInternalServerError,
				Body:       io.NopCloser(strings.NewReader(``)),
			},
			expectedResult: nil,
			expectedError:  errors.New("HTTP status code: 500"),
		},
		{
			// Test case: HTTP client network error handling
			// This tests the error handling when network connectivity issues occur
			// ensuring graceful handling of connection failures
			name:           "http client error",
			httpResponse:   nil,
			httpError:      errors.New("http client error"),
			expectedResult: nil,
			expectedError:  errors.New("got error http client error"),
		},
		{
			// Test case: Empty result set handling
			// This tests the handling of valid responses that contain no data
			// ensuring the method returns an empty slice rather than nil
			name: "empty result",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(`{"result":[]}`)),
			},
			expectedResult: []AppService{},
			expectedError:  nil,
		},
	}

	// Execute each test case individually to ensure isolation
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create mock HTTP client with configured response behavior
			// Each test case defines its own response/error behavior
			mockHttpClient := &MockHttpClient{
				DoFunc: func(req *http.Request) (*http.Response, error) {
					return tt.httpResponse, tt.httpError
				},
			}

			// Create ServiceNow client instance with mock HTTP client
			// This ensures all HTTP calls go through our mock instead of real network
			client := &Client{
				httpClient:    mockHttpClient,
				urlAppservice: "http://example.com/appservices",
				DebugLogger:   logging.NewDebugLogger(nil),
				debug:         false,
			}

			// Execute the method under test
			result, err := client.GetAppServices()

			// Validate error handling behavior
			// Check for both presence/absence of errors and error message accuracy
			if (err != nil && tt.expectedError == nil) || (err == nil && tt.expectedError != nil) || (err != nil && err.Error() != tt.expectedError.Error()) {
				t.Errorf("expected error '%v', got '%v'", tt.expectedError, err)
			}

			// Validate result length to ensure correct parsing
			if len(result) != len(tt.expectedResult) {
				t.Errorf("expected result length %d, got %d", len(tt.expectedResult), len(result))
			}

			// Validate specific fields in the result data
			// This ensures the JSON unmarshaling worked correctly
			for i, appService := range result {
				if appService.Name != tt.expectedResult[i].Name {
					t.Errorf("expected service name '%s', got '%s'", tt.expectedResult[i].Name, appService.Name)
				}
			}
		})
	}
}

// TestGetCIsForTag validates the GetCIsForTag method functionality across multiple scenarios.
// This comprehensive test suite ensures proper handling of tag-based CI retrieval operations
// and validates error conditions for robustness.
//
// Test scenarios covered:
// - Valid JSON response with tag entry data
// - Invalid/malformed JSON response handling
// - Non-200 HTTP status code handling
// - HTTP client network errors
// - Empty result set handling
//
// The method retrieves Configuration Items (CIs) associated with specific tags,
// which is essential for linking application services to their underlying infrastructure.
func TestGetCIsForTag(t *testing.T) {
	// Define comprehensive test cases for tag-based CI retrieval
	tests := []struct {
		name           string         // Descriptive name for the test case
		tag            string         // Input tag parameter for the method
		httpResponse   *http.Response // Mock HTTP response to return
		httpError      error          // Mock HTTP error to return
		expectedResult []TagEntry     // Expected parsed tag entries
		expectedError  error          // Expected error condition
	}{
		{
			// Test case: Valid response with tag entry data
			// This tests successful retrieval of CIs associated with a service tag
			name: "valid response",
			tag:  "SNSVC0000001",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body: io.NopCloser(strings.NewReader(`
{
  "result": [
    {
      "sys_id": "00000000000000000000000000000006",
      "key": "serviceid",
      "value": "SNSVC0000001",
      "ci": "00000000000000000000000000000007"
    },
    {
      "sys_id": "00000000000000000000000000000008",
      "key": "serviceid",
      "value": "SNSVC0000001",
      "ci": "00000000000000000000000000000009"
    }
  ]
}
`)),
			},
			expectedResult: []TagEntry{
				{
					SysID: "00000000000000000000000000000006",
					Key:   "CI1",
					Value: "CI0001",
					CI:    "00000000000000000000000000000007",
				},
				{
					SysID: "00000000000000000000000000000008",
					Key:   "CI1",
					Value: "CI0001",
					CI:    "00000000000000000000000000000009",
				},
			},
			expectedError: nil,
		},
		{
			// Test case: Invalid JSON response handling
			name: "invalid JSON response",
			tag:  "tag2",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(`{"result":[{`)),
			},
			expectedResult: nil,
			expectedError:  errors.New("unexpected end of JSON input"),
		},
		{
			// Test case: Non-200 HTTP status code handling
			name: "non-200 status code",
			tag:  "tag3",
			httpResponse: &http.Response{
				StatusCode: http.StatusInternalServerError,
				Body:       io.NopCloser(strings.NewReader(``)),
			},
			expectedResult: nil,
			expectedError:  errors.New("HTTP status code: 500"),
		},
		{
			// Test case: HTTP client network error handling
			name:           "http client error",
			tag:            "tag4",
			httpResponse:   nil,
			httpError:      errors.New("http client error"),
			expectedResult: nil,
			expectedError:  errors.New("got error http client error"),
		},
		{
			// Test case: Empty result set handling
			name: "empty result",
			tag:  "tag5",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(`{"result":[]}`)),
			},
			expectedResult: []TagEntry{},
			expectedError:  nil,
		},
	}

	// Execute each test case with proper isolation
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create mock HTTP client for this specific test case
			mockHttpClient := &MockHttpClient{
				DoFunc: func(req *http.Request) (*http.Response, error) {
					return tt.httpResponse, tt.httpError
				},
			}

			// Create ServiceNow client with mock HTTP client
			client := &Client{
				httpClient:  mockHttpClient,
				urlTag:      "http://example.com/tag/",
				DebugLogger: logging.NewDebugLogger(nil),
				debug:       false,
			}

			// Execute the method under test with the specified tag
			result, err := client.GetCIsForTag(tt.tag)

			// Validate error handling behavior
			if (err != nil && tt.expectedError == nil) || (err == nil && tt.expectedError != nil) || (err != nil && err.Error() != tt.expectedError.Error()) {
				t.Errorf("expected error '%v', got '%v'", tt.expectedError, err)
			}

			// Validate result structure and content
			if len(result) != len(tt.expectedResult) {
				t.Errorf("expected result length %d, got %d", len(tt.expectedResult), len(result))
			}

			// Validate specific fields in the tag entries
			for i, entry := range result {
				if entry.CI != tt.expectedResult[i].CI {
					t.Errorf("expected entry ci '%s', got '%s'", tt.expectedResult[i].CI, entry.CI)
				}
			}
		})
	}
}

// TestGetCmdbCI validates the GetCmdbCI method functionality for retrieving detailed configuration item data.
// This test ensures proper handling of CMDB CI retrieval operations and validates comprehensive error scenarios.
//
// Test scenarios covered:
// - Valid JSON response with complete CMDB CI data
// - Invalid/malformed JSON response handling
// - Non-200 HTTP status code handling
// - HTTP client network errors
// - Empty response handling
// - Empty system ID parameter validation
//
// CMDB CIs contain detailed technical information about infrastructure components
// and are critical for understanding the technical foundation of application services.
func TestGetCmdbCI(t *testing.T) {
	// Define comprehensive test cases for CMDB CI retrieval operations
	tests := []struct {
		name           string         // Descriptive name for the test case
		sysID          string         // Input system ID parameter
		httpResponse   *http.Response // Mock HTTP response to return
		httpError      error          // Mock HTTP error to return
		expectedResult CmdbCi         // Expected parsed CMDB CI data
		expectedError  error          // Expected error condition
	}{
		{
			// Test case: Valid response with complete CMDB CI data
			// This tests successful retrieval of detailed configuration item information
			name:  "valid response",
			sysID: "00000000000000000000000000000010",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body: io.NopCloser(strings.NewReader(`
{
  "result": {
    "firewall_status": "Intranet",
    "os_address_width": "64",
    "attested_date": "",
    "operational_status": "6",
    "os_service_pack": "",
    "cpu_core_thread": "1",
    "cpu_manufacturer": {
      "link": "https://snow.example.com/api/now/table/core_company/00000000000000000000000000000011",
      "value": "00000000000000000000000000000011"
    },
    "sys_updated_on": "2025-04-12 07:51:06",
    "allotted_electric_power_unit": "",
    "discovery_source": "VR-Qualys",
    "first_discovered": "2020-09-14 10:51:40",
    "due_in": "",
    "used_for": "Production",
    "gl_account": "",
    "invoice_number": "",
    "sys_created_by": "mid.user",
    "ram": "4096",
    "warranty_expiration": "",
    "cpu_name": "Intel(R) Xeon(R) CPU E5-2698 v3 @ 2.30GHz",
    "cpu_speed": "2295",
    "owned_by": "",
    "checked_out": "",
    "classification": "Production",
    "disk_space": "420",
    "sys_domain_path": "/",
    "business_unit": "",
    "object_id": "AQUAAAAAAAUVAAAAFFKfpdAwM8xbt9EJGesFAA==",
    "maintenance_schedule": "",
    "created_by_qualys": "false",
    "cost_center": "",
    "attested_by": "",
    "dns_domain": "example.com",
    "assigned": "",
    "life_cycle_stage": {
      "link": "https://snow.example.com/api/now/table/life_cycle_stage?name=End+of+Life",
      "value": "End of Life"
    },
    "purchase_date": "",
    "cd_speed": "",
    "short_description": "",
    "floppy": "",
    "managed_by": "",
    "allotted_electric_power": "",
    "os_domain": "",
    "can_print": "false",
    "last_discovered": "2025-04-12 07:51:06",
    "sys_class_name": "cmdb_ci_win_server",
    "cpu_count": "2",
    "manufacturer": {
      "link": "https://snow.example.com/api/now/table/core_company/00000000000000000000000000000012",
      "value": "00000000000000000000000000000012"
    },
    "life_cycle_stage_status": {
      "link": "https://snow.example.com/api/now/table/life_cycle_stage_status?name=Retired",
      "value": "Retired"
    },
    "vendor": "",
    "model_number": "",
    "assigned_to": "",
    "start_date": "",
    "os_version": "10.0.14393",
    "serial_number": "VMware-00 00 11 22 33 44 55 66-77 88 99 00 aa bb cc dd",
    "cd_rom": "false",
    "support_group": "",
    "correlation_id": "",
    "unverified": "false",
    "attributes": "",
    "asset": {
      "link": "https://snow.example.com/api/now/table/alm_asset/00000000000000000000000000000013",
      "value": "00000000000000000000000000000013"
    },
    "cpu_core_count": "1",
    "form_factor": "",
    "skip_sync": "false",
    "product_instance_id": "",
    "u_internet_facing_desired": "false",
    "most_frequent_user": "",
    "attestation_score": "",
    "sys_updated_by": "VR.System",
    "sys_created_on": "2020-09-15 01:37:00",
    "x_lam_lhm_charging_exclude_from_billing": "false",
    "cpu_type": "GenuineIntel",
    "sys_domain": {
      "link": "https://snow.example.com/api/now/table/sys_user_group/global",
      "value": "global"
    },
    "install_date": "",
    "asset_tag": "12345678",
    "dr_backup": "",
    "hardware_substatus": "",
    "u_work_notes": "",
    "fqdn": "linuxk001.example.com",
    "sn_vul_qualys_id": "131233004",
    "change_control": {
      "link": "https://snow.example.com/api/now/table/sys_user_group/00000000000000000000000000000014",
      "value": "00000000000000000000000000000014"
    },
    "internet_facing": "false",
    "delivery_date": "",
    "hardware_status": "retired",
    "install_status": "7",
    "supported_by": "",
    "sn_vul_qualys_host_id": "585be2d3-0ce8-4b1e-9dd3-3979793f2b85",
    "name": "linuxk001",
    "subcategory": "Computer",
    "default_gateway": "10.165.17.1",
    "chassis_type": "Other",
    "virtual": "true",
    "assignment_group": "",
    "u_account": "",
    "managed_by_group": {
      "link": "https://snow.example.com/api/now/table/sys_user_group/00000000000000000000000000000015",
      "value": "00000000000000000000000000000015"
    },
    "sys_id": "00000000000000000000000000000016",
    "cluster_id": "",
    "po_number": "",
    "checked_in": "2025-01-18 00:05:46",
    "sys_class_path": "/!!/!2/!(/!!/!#",
    "mac_address": "",
    "company": "",
    "justification": "",
    "department": "",
    "cluster_name": "",
    "comments": "Status changes initiated by remediation for staleness",
    "cost": "",
    "os": "Windows 2016 Datacenter",
    "attestation_status": "Not Yet Reviewed",
    "sys_mod_count": "209",
    "x_seon_threat_sentinelone_agent_id": "",
    "monitor": "false",
    "ip_address": "10.165.17.147",
    "model_id": {
      "link": "https://snow.example.com/api/now/table/cmdb_model/00000000000000000000000000000017",
      "value": "00000000000000000000000000000017"
    },
    "duplicate_of": "",
    "sys_tags": "",
    "cost_cc": "USD",
    "order_date": "",
    "schedule": "",
    "environment": "",
    "due": "",
    "u_customer_account": {
      "link": "https://snow.example.com/api/now/table/customer_account/00000000000000000000000000000018",
      "value": "00000000000000000000000000000018"
    },
    "attested": "false",
    "location": "",
    "u_exclude_from_sam": "false",
    "category": "CN=Computer,CN=Schema,CN=Configuration,D",
    "fault_count": "0",
    "host_name": "linuxk001",
    "lease_id": ""
  }
}
`)),
			},
			expectedResult: CmdbCi{
				AssetTag:       "12345678",
				SysId:          "00000000000000000000000000000016",
				HardwareStatus: "retired",
				Os:             "Windows 2016 Datacenter",
				Fqdn:           "linuxk001.example.com",
				OsVersion:      "10.0.14393",
				SerialNumber:   "VMware-00 00 11 22 33 44 55 66-77 88 99 00 aa bb cc dd",
				LastDiscovered: "2025-04-12 07:51:06",
				SysClassName:   "cmdb_ci_win_server",
				IpAddress:      "10.165.17.147",
				HostName:       "linuxk001",
				Name:           "linuxk001",
			},
			expectedError: nil,
		},
		{
			// Test case: Invalid JSON response handling
			name:  "invalid JSON response",
			sysID: "00000000000000000000000000000011",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(`{"result":{`)),
			},
			expectedResult: CmdbCi{},
			expectedError:  errors.New("unexpected end of JSON input"),
		},
		{
			// Test case: Non-200 HTTP status code handling
			name:  "non-200 status code",
			sysID: "00000000000000000000000000000012",
			httpResponse: &http.Response{
				StatusCode: http.StatusInternalServerError,
				Body:       io.NopCloser(strings.NewReader(``)),
			},
			expectedResult: CmdbCi{},
			expectedError:  errors.New("HTTP status code: 500"),
		},
		{
			// Test case: HTTP client network error handling
			name:           "http client error",
			sysID:          "00000000000000000000000000000013",
			httpResponse:   nil,
			httpError:      errors.New("http client error"),
			expectedResult: CmdbCi{},
			expectedError:  errors.New("got error http client error"),
		},
		{
			// Test case: Empty response handling
			name:  "empty response",
			sysID: "00000000000000000000000000000014",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(``)),
			},
			expectedResult: CmdbCi{},
			expectedError:  nil,
		},
		{
			// Test case: Empty system ID parameter validation
			// This ensures the method validates input parameters properly
			name:           "empty sysId",
			sysID:          "",
			httpResponse:   nil,
			httpError:      nil,
			expectedResult: CmdbCi{},
			expectedError:  errors.New("sysId parameter cannot be empty"),
		},
	}

	// Execute each test case with proper isolation
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create mock HTTP client for this specific test case
			mockHttpClient := &MockHttpClient{
				DoFunc: func(req *http.Request) (*http.Response, error) {
					return tt.httpResponse, tt.httpError
				},
			}

			// Create ServiceNow client with mock HTTP client
			client := &Client{
				httpClient:  mockHttpClient,
				urlCmdbCi:   "http://example.com/cmdbci/",
				DebugLogger: logging.NewDebugLogger(nil),
				debug:       false,
			}

			// Execute the method under test with the specified system ID
			result, err := client.GetCmdbCI(tt.sysID)

			// Validate error handling behavior
			if (err != nil && tt.expectedError == nil) || (err == nil && tt.expectedError != nil) || (err != nil && err.Error() != tt.expectedError.Error()) {
				t.Errorf("expected error '%v', got '%v'", tt.expectedError, err)
			}

			// Validate the complete result structure
			if result != tt.expectedResult {
				t.Errorf("expected result %+v, got %+v", tt.expectedResult, result)
			}
		})
	}
}

// TestGetFoundationData validates the GetFoundationData method functionality for retrieving group information.
// This comprehensive test ensures proper handling of foundation data retrieval and validates error scenarios.
//
// Test scenarios covered:
// - Valid JSON response with complete foundation data including manager and members
// - Invalid/malformed JSON response handling
// - Non-200 HTTP status code handling
// - HTTP client network errors
// - Empty response handling
// - Empty system ID parameter validation
//
// Foundation data contains group information including managers and members, which is essential
// for understanding organizational structure and service ownership relationships.
func TestGetFoundationData(t *testing.T) {
	// Define comprehensive test cases for foundation data retrieval
	tests := []struct {
		name           string         // Descriptive name for the test case
		sysId          string         // Input system ID parameter
		httpResponse   *http.Response // Mock HTTP response to return
		httpError      error          // Mock HTTP error to return
		expectedResult FoundationData // Expected parsed foundation data
		expectedError  error          // Expected error condition
	}{
		{
			// Test case: Valid response with complete foundation data
			// This tests successful retrieval of group information including manager and members
			name:  "valid response",
			sysId: "00000000000000000000000000000019",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body: io.NopCloser(strings.NewReader(`
{
  "result": {
    "name": "Service: Test",
    "sys_id": "00000000000000000000000000000020",
    "manager": {
		"sys_id": "00000000000000000000000000000005",
		"name": "Anonymized User A",
		"email": "anonymized.user.a@example.com",
		"department": "Team 1",
		"company": "Example Corp",
		"user_id": "anonymized.user.a",
		"source": null,
		"locked_out": true,
		"active": false
    },
    "members": [
      {
		"sys_id": "00000000000000000000000000000006",
		"name": "Anonymized User B",
		"email": "anonymized.user.b@example.com",
		"department": "Team 2",
		"company": "Example Corp",
		"user_id": "anonymized.user.b",
		"source": null,
		"locked_out": true,
		"active": true
      },
      {
		"sys_id": "00000000000000000000000000000007",
		"name": "Anonymized User C",
		"email": "anonymized.user.c@example.com",
		"department": "Team 3",
		"company": "Example Corp",
		"user_id": "anonymized.user.c",
		"source": null,
		"locked_out": false,
		"active": false
      }
    ]
  }
}`)),
			},
			expectedResult: FoundationData{
				Name:  "Service: Test",
				SysID: "00000000000000000000000000000020",
				Manager: User{
					SysID:      "00000000000000000000000000000005",
					Name:       "Anonymized User A",
					Email:      "anonymized.user.a@example.com",
					Department: "Team 1",
					Company:    "Example Corp",
					UserID:     "anonymized.user.a",
					LockedOut:  true,
					Active:     false,
				},
				Members: []User{
					{
						SysID:      "00000000000000000000000000000006",
						Name:       "Anonymized User B",
						Email:      "anonymized.user.b@example.com",
						Department: "Team 2",
						Company:    "Example Corp",
						UserID:     "anonymized.user.b",
						LockedOut:  true,
						Active:     true,
					}, {
						SysID:      "00000000000000000000000000000007",
						Name:       "Anonymized User C",
						Email:      "anonymized.user.c@example.com",
						Department: "Team 3",
						Company:    "Example Corp",
						UserID:     "anonymized.user.c",
						LockedOut:  false,
						Active:     false,
					},
				},
			},
			expectedError: nil,
		},
		{
			// Test case: Invalid JSON response handling
			name:  "invalid JSON response",
			sysId: "00000000000000000000000000000020",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader(`{"result":[{`)),
			},
			expectedResult: FoundationData{},
			expectedError:  errors.New("unexpected end of JSON input"),
		},
		{
			// Test case: Non-200 HTTP status code handling
			name:  "non-200 status code",
			sysId: "00000000000000000000000000000021",
			httpResponse: &http.Response{
				StatusCode: http.StatusInternalServerError,
				Body:       io.NopCloser(strings.NewReader("")),
			},
			expectedResult: FoundationData{},
			expectedError:  errors.New("HTTP status code: 500"),
		},
		{
			// Test case: HTTP client network error handling
			name:           "http client error",
			sysId:          "00000000000000000000000000000022",
			httpResponse:   nil,
			httpError:      errors.New("http client error"),
			expectedResult: FoundationData{},
			expectedError:  errors.New("got error http client error"),
		},
		{
			// Test case: Empty response handling
			name:  "empty response",
			sysId: "00000000000000000000000000000023",
			httpResponse: &http.Response{
				StatusCode: http.StatusOK,
				Body:       io.NopCloser(strings.NewReader("")),
			},
			expectedResult: FoundationData{},
			expectedError:  nil,
		},
		{
			// Test case: Empty system ID parameter validation
			name:           "empty sysId",
			sysId:          "",
			httpResponse:   nil,
			httpError:      nil,
			expectedResult: FoundationData{},
			expectedError:  errors.New("sysId parameter cannot be empty"),
		},
	}

	// Execute each test case with proper isolation
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Create mock HTTP client for this specific test case
			mockHttpClient := &MockHttpClient{
				DoFunc: func(req *http.Request) (*http.Response, error) {
					return tt.httpResponse, tt.httpError
				},
			}

			// Create ServiceNow client with mock HTTP client
			client := &Client{
				httpClient:  mockHttpClient,
				urlCmdbCi:   "http://example.com/foundationdata/",
				DebugLogger: logging.NewDebugLogger(nil),
				debug:       false,
			}

			// Execute the method under test with the specified system ID
			result, err := client.GetFoundationData(tt.sysId)

			// Validate error handling behavior
			if (err != nil && tt.expectedError == nil) || (err == nil && tt.expectedError != nil) || (err != nil && err.Error() != tt.expectedError.Error()) {
				t.Errorf("expected error '%v', got '%v'", tt.expectedError, err)
			}

			// Validate basic result structure fields
			if result.Name != tt.expectedResult.Name {
				t.Errorf("expected Name '%s', got '%s'", tt.expectedResult.Name, result.Name)
			}
			if result.SysID != tt.expectedResult.SysID {
				t.Errorf("expected SysID '%s', got '%s'", tt.expectedResult.SysID, result.SysID)
			}

			// Validate manager user information using helper function
			if !compareUsers(result.Manager, tt.expectedResult.Manager) {
				t.Errorf("expected Manager %+v, got %+v", tt.expectedResult.Manager, result.Manager)
			}

			// Validate members list structure and content
			if len(result.Members) != len(tt.expectedResult.Members) {
				t.Errorf("expected %d members, got %d", len(tt.expectedResult.Members), len(result.Members))
				return
			}

			// Validate each member's information individually
			for i, member := range result.Members {
				if !compareUsers(member, tt.expectedResult.Members[i]) {
					t.Errorf("member at index %d differs: expected %+v, got %+v", i, tt.expectedResult.Members[i], member)
				}
			}
		})
	}
}

// compareUsers is a helper function that performs deep comparison of User structures.
// This function compares all fields of two User instances to determine equality.
// It's used by test cases to validate that user data was parsed correctly from JSON responses.
//
// Parameters:
//   - a: First user to compare
//   - b: Second user to compare
//
// Returns:
//   - bool: true if all fields match exactly, false otherwise
//
// Compared fields:
// - SysID: Unique system identifier
// - Name: Full name of the user
// - Email: User's email address
// - Department: Department/organizational unit
// - Company: Company/organization name
// - UserID: Login identifier
// - LockedOut: Account lockout status
// - Active: Account active status
func compareUsers(a, b User) bool {
	return a.SysID == b.SysID &&
		a.Name == b.Name &&
		a.Email == b.Email &&
		a.Department == b.Department &&
		a.Company == b.Company &&
		a.UserID == b.UserID &&
		a.LockedOut == b.LockedOut &&
		a.Active == b.Active
}
