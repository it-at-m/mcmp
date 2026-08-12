package processor

import (
	"encoding/json"
	"os"
	"strings"
	"testing"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/snow"
	"github.com/it-at-m/mcmp/mcmp-eai-snow/pkg/clients/mcmp"
)

// MockSnowClient implements SnowClientInterface for testing purposes.
// It provides a mock implementation of ServiceNow client operations using in-memory data structures.
// This allows for isolated unit testing without requiring actual ServiceNow API connectivity.
type MockSnowClient struct {
	// appServices stores mock application service data for testing
	appServices []snow.AppService
	// foundationData stores mock foundation data (groups) indexed by system ID
	foundationData map[string]snow.FoundationData
	// tagEntries stores mock tag entries indexed by tag name for CI associations
	tagEntries map[string][]snow.TagEntry
	// cmdbCIs stores mock CMDB configuration items indexed by system ID
	cmdbCIs map[string]snow.CmdbCi
}

// NewMockSnowClient creates a new instance of MockSnowClient with initialized data structures.
// All internal maps are initialized to prevent nil pointer exceptions during testing.
//
// Returns:
//   - *MockSnowClient: Fully initialized mock client ready for test data setup
func NewMockSnowClient() *MockSnowClient {
	return &MockSnowClient{
		foundationData: make(map[string]snow.FoundationData),
		tagEntries:     make(map[string][]snow.TagEntry),
		cmdbCIs:        make(map[string]snow.CmdbCi),
	}
}

// GetAppServices returns the mock application services data.
// This method simulates the ServiceNow API call for retrieving application services.
//
// Returns:
//   - []snow.AppService: List of mock application services
//   - error: Always nil in this mock implementation
func (m *MockSnowClient) GetAppServices() ([]snow.AppService, error) {
	return m.appServices, nil
}

func (m *MockSnowClient) GetLockedShutdown() (map[string]string, error) {
	return make(map[string]string), nil
}

func (m *MockSnowClient) GetLockedRightsize() (map[string]string, error) {
	return make(map[string]string), nil
}

func (m *MockSnowClient) GetVMwareInstances() ([]snow.CmdbCi, error) {
	var vmwareInstances []snow.CmdbCi
	for _, ci := range m.cmdbCIs {
		if ci.SysClassName == "cmdb_ci_vmware_instance" ||
			strings.Contains(strings.ToLower(ci.SysClassName), "vmware") {
			vmwareInstances = append(vmwareInstances, ci)
		}
	}
	return vmwareInstances, nil
}

// GetFoundationData retrieves mock foundation data by system ID.
// This method simulates the ServiceNow API call for retrieving group foundation data.
//
// Parameters:
//   - sysId: System ID of the foundation data to retrieve
//
// Returns:
//   - snow.FoundationData: Foundation data if found, empty struct otherwise
//   - error: Always nil in this mock implementation
func (m *MockSnowClient) GetFoundationData(sysId string) (snow.FoundationData, error) {
	if data, exists := m.foundationData[sysId]; exists {
		return data, nil
	}
	return snow.FoundationData{}, nil
}

// GetCIsForTag retrieves mock configuration items associated with a specific tag.
// This method simulates the ServiceNow API call for retrieving CIs by tag.
//
// Parameters:
//   - tag: Tag identifier to search for associated CIs
//
// Returns:
//   - []snow.TagEntry: List of tag entries if found, empty slice otherwise
//   - error: Always nil in this mock implementation
func (m *MockSnowClient) GetCIsForTag(tag string) ([]snow.TagEntry, error) {
	if entries, exists := m.tagEntries[tag]; exists {
		return entries, nil
	}
	return []snow.TagEntry{}, nil
}

// GetCmdbCI retrieves mock CMDB configuration item data by system ID.
// This method simulates the ServiceNow API call for retrieving detailed CI information.
//
// Parameters:
//   - sysId: System ID of the configuration item to retrieve
//
// Returns:
//   - snow.CmdbCi: CMDB CI data if found, empty struct otherwise
//   - error: Always nil in this mock implementation
func (m *MockSnowClient) GetCmdbCI(sysId string) (snow.CmdbCi, error) {
	if ci, exists := m.cmdbCIs[sysId]; exists {
		return ci, nil
	}
	return snow.CmdbCi{}, nil
}

func (m *MockSnowClient) GetKubernetesNamespaceData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetStorageServerData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetStorageVolumeData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetStorageQTreeData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiCloudServiceAccountData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiCloudObjectStorageData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetLbServiceData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetVMwareInstanceData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetServerForVMwareInstance(_ string) (snow.Server, error) {
	return snow.Server{}, nil
}

func (m *MockSnowClient) GetCmdbCiServerData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetPackageRepositoryData() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiDbOraPdbInstance() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiDbOraInstance() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiDbMySQLInstance() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiDbPostgreSQLInstance() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiDbMongoDbInstance() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetCmdbCiDbMSSQLInstance() ([]snow.ConfigurationItemWithAppServices, error) {
	return nil, nil
}

func (m *MockSnowClient) GetDbInstanceToServerMapping() (map[string]map[string]struct{}, error) {
	return make(map[string]map[string]struct{}), nil
}

func (m *MockSnowClient) GetOraclePdbToServerMapping() (map[string]map[string]struct{}, map[string]map[string]struct{}, error) {
	return make(map[string]map[string]struct{}), make(map[string]map[string]struct{}), nil
}

func (m *MockSnowClient) GetKubernetesNamespaceKeyValues() (map[string]snow.ConfigurationItemWithAppServices, error) {
	return make(map[string]snow.ConfigurationItemWithAppServices), nil
}

func (m *MockSnowClient) GetStorageVolumeKeyValues() (map[string]snow.ConfigurationItemWithAppServices, error) {
	return make(map[string]snow.ConfigurationItemWithAppServices), nil
}

func (m *MockSnowClient) GetLbServiceKeyValues() (map[string]snow.ConfigurationItemWithAppServices, error) {
	return make(map[string]snow.ConfigurationItemWithAppServices), nil
}

// EnableDebug is a no-op method for the mock implementation.
// This method exists to satisfy the SnowClientInterface contract.
func (m *MockSnowClient) EnableDebug() {}

// setupTestData creates and configures a MockSnowClient with comprehensive test data.
// This function sets up a realistic data scenario including users, groups, app services,
// and configuration items with proper relationships between them.
//
// Test data structure:
// - 4 users representing different departments and roles
// - 2 application services (production and test environments)
// - 2 foundation groups with managers and members
// - 4 configuration items representing different infrastructure types
// - Tag entries linking app services to their associated CIs
//
// Returns:
//   - *MockSnowClient: Fully configured mock client with test data
func setupTestData() *MockSnowClient {
	mock := NewMockSnowClient()

	// Create test users representing different departments and roles
	// User 1: IT department user who will be a group manager and service owner
	user1 := snow.User{
		SysID:      "user-001",
		Name:       "Max Mustermann",
		Email:      "max.mustermann@example.com",
		Department: "IT",
		Company:    "Test Company",
		UserID:     "mmustermann",
		LockedOut:  false,
		Active:     true,
	}

	// User 2: HR department user who will be a service owner delegate
	user2 := snow.User{
		SysID:      "user-002",
		Name:       "Anna Schmidt",
		Email:      "anna.schmidt@example.com",
		Department: "HR",
		Company:    "Test Company",
		UserID:     "aschmidt",
		LockedOut:  false,
		Active:     true,
	}

	// User 3: Finance department user who will be a group manager
	user3 := snow.User{
		SysID:      "user-003",
		Name:       "Peter Weber",
		Email:      "peter.weber@example.com",
		Department: "Finance",
		Company:    "Test Company",
		UserID:     "pweber",
		LockedOut:  false,
		Active:     true,
	}

	// User 4: Operations department user who will be a group member
	user4 := snow.User{
		SysID:      "user-004",
		Name:       "Maria Müller",
		Email:      "maria.mueller@example.com",
		Department: "Operations",
		Company:    "Test Company",
		UserID:     "mmueller",
		LockedOut:  false,
		Active:     true,
	}

	// Create test application services representing different environments
	// App Service 1: Production environment with CSW enforcement
	appService1 := snow.AppService{
		SysID:                  "app-001",
		Name:                   "Test App Service 1",
		Number:                 "APP001",
		AssignmenGroup:         "group-001",
		UsedFor:                "Production",
		Environment:            "PROD",
		CSWEnforced:            true,
		OwnedBy:                user1,
		ServiceOwnerDelegate:   user2,
		BusinessServiceNumbers: []string{"BSN001", "BSN002"},
	}

	// App Service 2: Test environment without CSW enforcement
	appService2 := snow.AppService{
		SysID:                  "app-002",
		Name:                   "Test App Service 2",
		Number:                 "APP002",
		AssignmenGroup:         "group-002",
		UsedFor:                "Testing",
		Environment:            "TEST",
		CSWEnforced:            false,
		OwnedBy:                user3,
		ServiceOwnerDelegate:   user4,
		BusinessServiceNumbers: []string{"BSN003"},
	}

	// Store app services in mock client
	mock.appServices = []snow.AppService{appService1, appService2}

	// Create foundation data representing organizational groups
	// Foundation Data 1: IT Operations group with manager and members
	foundationData1 := snow.FoundationData{
		Name:    "IT Operations Group",
		SysID:   "group-001",
		Manager: user1,
		Members: []snow.User{user1, user2},
	}

	// Foundation Data 2: Development group with different manager and members
	foundationData2 := snow.FoundationData{
		Name:    "Development Group",
		SysID:   "group-002",
		Manager: user3,
		Members: []snow.User{user3, user4},
	}

	// Store foundation data in mock client indexed by group ID
	mock.foundationData["group-001"] = foundationData1
	mock.foundationData["group-002"] = foundationData2

	// Create tag entries linking app services to configuration items
	// Tag entries for APP001 linking to server and database CIs
	tagEntries1 := []snow.TagEntry{
		{
			SysID: "tag-001",
			Key:   "serviceid",
			Value: "APP001",
			CI:    "ci-001",
		},
		{
			SysID: "tag-002",
			Key:   "serviceid",
			Value: "APP001",
			CI:    "ci-002",
		},
	}

	// Tag entries for APP002 linking to database and load balancer CIs
	tagEntries2 := []snow.TagEntry{
		{
			SysID: "tag-003",
			Key:   "serviceid",
			Value: "APP002",
			CI:    "ci-003",
		},
		{
			SysID: "tag-004",
			Key:   "serviceid",
			Value: "APP002",
			CI:    "ci-004",
		},
	}

	// Store tag entries in mock client indexed by app service number
	mock.tagEntries["APP001"] = tagEntries1
	mock.tagEntries["APP002"] = tagEntries2

	// Create configuration items representing different types of infrastructure
	// CI 1: Linux server with Ubuntu operating system
	ci1 := snow.CmdbCi{
		SysId:          "ci-001",
		Name:           "Server-001",
		SysClassName:   "cmdb_ci_server",
		Fqdn:           "server001.example.com",
		SerialNumber:   "SN001",
		HardwareStatus: "In use",
		Os:             "Linux",
		OsVersion:      "Ubuntu 20.04",
		IpAddress:      "192.168.1.10",
		HostName:       "server001",
		LastDiscovered: "2024-01-15 10:30:00",
	}

	// CI 2: Windows server for application hosting
	ci2 := snow.CmdbCi{
		SysId:          "ci-002",
		Name:           "Server-002",
		SysClassName:   "cmdb_ci_server",
		Fqdn:           "server002.example.com",
		SerialNumber:   "SN002",
		HardwareStatus: "In use",
		Os:             "Windows",
		OsVersion:      "Windows Server 2019",
		IpAddress:      "192.168.1.11",
		HostName:       "server002",
		LastDiscovered: "2024-01-15 10:35:00",
	}

	// CI 3: Database server for data storage
	ci3 := snow.CmdbCi{
		SysId:          "ci-003",
		Name:           "Database-001",
		SysClassName:   "cmdb_ci_database",
		Fqdn:           "db001.example.com",
		SerialNumber:   "SN003",
		HardwareStatus: "In use",
		Os:             "Linux",
		OsVersion:      "CentOS 8",
		IpAddress:      "192.168.1.20",
		HostName:       "db001",
		LastDiscovered: "2024-01-15 11:00:00",
	}

	// CI 4: Load balancer for traffic distribution
	ci4 := snow.CmdbCi{
		SysId:          "ci-004",
		Name:           "LoadBalancer-001",
		SysClassName:   "cmdb_ci_lb",
		Fqdn:           "lb001.example.com",
		SerialNumber:   "SN004",
		HardwareStatus: "In use",
		Os:             "Linux",
		OsVersion:      "Alpine 3.14",
		IpAddress:      "192.168.1.30",
		HostName:       "lb001",
		LastDiscovered: "2024-01-15 11:15:00",
	}

	// Store configuration items in mock client indexed by system ID
	mock.cmdbCIs["ci-001"] = ci1
	mock.cmdbCIs["ci-002"] = ci2
	mock.cmdbCIs["ci-003"] = ci3
	mock.cmdbCIs["ci-004"] = ci4

	return mock
}

// TestServiceProcessor_ProcessAppServices tests the main processing functionality of ServiceProcessor.
// This comprehensive test validates that the processor correctly handles application services
// and their associated entities (users, groups, CIs) through multiple sub-tests.
//
// Test coverage includes:
// - User map validation and user data integrity
// - Group map validation and group relationship verification
// - CI map validation and configuration item data accuracy
// - App service processing and relationship mapping
func TestServiceProcessor_ProcessAppServices(t *testing.T) {
	// Setup test environment with mock data
	mockClient := setupTestData()
	processor := NewServiceProcessor(mockClient, false)

	// Execute the main processing method
	err := processor.ProcessAppServices()
	if err != nil {
		t.Fatalf("ProcessAppServices() error: %v", err)
	}

	// Sub-test: Validate user map processing and data integrity
	t.Run("User Map Validation", func(t *testing.T) {
		expectedUserCount := 4
		userMap := processor.GetUserMap()
		if len(userMap) != expectedUserCount {
			t.Errorf("Expected %d users, got %d", expectedUserCount, len(userMap))
		}

		// Validate specific user data accuracy
		if user, exists := userMap["user-001"]; !exists {
			t.Error("User user-001 not found")
		} else {
			if user.UserID != "mmustermann" {
				t.Errorf("User user-001 UserID expected 'mmustermann', got '%s'", user.UserID)
			}
		}

		if user, exists := userMap["user-002"]; !exists {
			t.Error("User user-002 not found")
		} else {
			if user.Department != "HR" {
				t.Errorf("User user-002 Department expected 'HR', got '%s'", user.Department)
			}
		}
	})

	// Sub-test: Validate group map processing and relationship mapping
	t.Run("Group Map Validation", func(t *testing.T) {
		expectedGroupCount := 2
		groupMap := processor.GetGroupMap()
		if len(groupMap) != expectedGroupCount {
			t.Errorf("Expected %d groups, got %d", expectedGroupCount, len(groupMap))
		}

		// Validate first group data and relationships
		if group, exists := groupMap["group-001"]; !exists {
			t.Error("Group group-001 not found")
		} else {
			if group.Name != "IT Operations Group" {
				t.Errorf("Group group-001 Name expected 'IT Operations Group', got '%s'", group.Name)
			}
			if group.Manager != "user-001" {
				t.Errorf("Group group-001 Manager expected 'user-001', got '%s'", group.Manager)
			}
			if len(group.Members) != 2 {
				t.Errorf("Group group-001 expected 2 members, got %d", len(group.Members))
			}
		}

		// Validate second group data and relationships
		if group, exists := groupMap["group-002"]; !exists {
			t.Error("Group group-002 not found")
		} else {
			if group.Name != "Development Group" {
				t.Errorf("Group group-002 Name expected 'Development Group', got '%s'", group.Name)
			}
			if group.Manager != "user-003" {
				t.Errorf("Group group-002 Manager expected 'user-003', got '%s'", group.Manager)
			}
		}
	})

	// Sub-test: Validate CI map processing and configuration item data
	t.Run("CI Map Validation", func(t *testing.T) {
		expectedCICount := 4
		ciMap := processor.GetCIMap()
		if len(ciMap) != expectedCICount {
			t.Errorf("Expected %d CIs, got %d", expectedCICount, len(ciMap))
		}

		// Validate specific CI data accuracy
		if ci, exists := ciMap["ci-001"]; !exists {
			t.Error("CI ci-001 not found")
		} else {
			if ci.FQDN != "server001.example.com" {
				t.Errorf("CI ci-001 FQDN expected 'server001.example.com', got '%s'", ci.FQDN)
			}
			if ci.OS != "Linux" {
				t.Errorf("CI ci-001 OS expected 'Linux', got '%s'", ci.OS)
			}
		}

		if ci, exists := ciMap["ci-003"]; !exists {
			t.Error("CI ci-003 not found")
		} else {
			if ci.SysClassName != "cmdb_ci_database" {
				t.Errorf("CI ci-003 SysClassName expected 'cmdb_ci_database', got '%s'", ci.SysClassName)
			}
		}
	})

	// Sub-test: Validate app service processing and CI associations
	t.Run("AppServices Validation", func(t *testing.T) {
		expectedAppServiceCount := 2
		appServices := processor.GetAppServices()
		if len(appServices) != expectedAppServiceCount {
			t.Errorf("Expected %d app services, got %d", expectedAppServiceCount, len(appServices))
		}

		// Validate first app service data and CI associations
		appService1 := appServices[0]
		if appService1.Name != "Test App Service 1" {
			t.Errorf("AppService 1 Name expected 'Test App Service 1', got '%s'", appService1.Name)
		}
		if appService1.Group != "group-001" {
			t.Errorf("AppService 1 Group expected 'group-001', got '%s'", appService1.Group)
		}
		if len(appService1.ServerCIs) != 2 {
			t.Errorf("AppService 1 expected 2 CIs, got %d", len(appService1.ServerCIs))
		}

		// Validate second app service data and CI associations
		appService2 := appServices[1]
		if appService2.Environment != "TEST" {
			t.Errorf("AppService 2 Environment expected 'TEST', got '%s'", appService2.Environment)
		}
		if len(appService2.ServerCIs) != 2 {
			t.Errorf("AppService 2 expected 2 CIs, got %d", len(appService2.ServerCIs))
		}
	})
}

// TestServiceProcessor_GetSnowData tests the snow data aggregation functionality.
// This test verifies that the processor correctly aggregates all processed data
// into a single SnowData structure with proper counts and data integrity.
func TestServiceProcessor_GetSnowData(t *testing.T) {
	// Setup test environment and execute processing
	mockClient := setupTestData()
	processor := NewServiceProcessor(mockClient, false)

	err := processor.ProcessAppServices()
	if err != nil {
		t.Fatalf("ProcessAppServices() error: %v", err)
	}

	// Get aggregated snow data
	snowData := processor.GetSnowData()

	// Validate aggregated data counts
	if len(snowData.Users) != 4 {
		t.Errorf("SnowData Users expected 4, got %d", len(snowData.Users))
	}
	if len(snowData.Groups) != 2 {
		t.Errorf("SnowData Groups expected 2, got %d", len(snowData.Groups))
	}
	if len(snowData.CmdbCIs) != 4 {
		t.Errorf("SnowData CmdbCIs expected 4, got %d", len(snowData.CmdbCIs))
	}
	if len(snowData.AppServices) != 2 {
		t.Errorf("SnowData AppServices expected 2, got %d", len(snowData.AppServices))
	}
}

// TestServiceProcessor_ExportSnowDataAsJSON tests the JSON export functionality.
// This test verifies that the processor can correctly serialize processed data
// to JSON format and that the resulting JSON is valid and contains expected data.
func TestServiceProcessor_ExportSnowDataAsJSON(t *testing.T) {
	// Setup test environment and execute processing
	mockClient := setupTestData()
	processor := NewServiceProcessor(mockClient, false)

	err := processor.ProcessAppServices()
	if err != nil {
		t.Fatalf("ProcessAppServices() error: %v", err)
	}

	// Export data as JSON string
	jsonString, err := processor.ExportSnowDataAsJSON()
	if err != nil {
		t.Fatalf("ExportSnowDataAsJSON() error: %v", err)
	}

	// Validate JSON string is not empty
	if len(jsonString) == 0 {
		t.Error("JSON string is empty")
	}

	// Validate JSON can be unmarshalled and contains expected data
	var snowData mcmp.SnowData
	err = json.Unmarshal([]byte(jsonString), &snowData)
	if err != nil {
		t.Fatalf("JSON unmarshal error: %v", err)
	}

	if len(snowData.Users) != 4 {
		t.Errorf("JSON SnowData Users expected 4, got %d", len(snowData.Users))
	}
}

// TestServiceProcessor_ExportSnowDataToFile tests the file export functionality.
// This test verifies that the processor can write processed data to a JSON file
// and that the file contains valid JSON with the expected data structure.
func TestServiceProcessor_ExportSnowDataToFile(t *testing.T) {
	// Setup test environment and execute processing
	mockClient := setupTestData()
	processor := NewServiceProcessor(mockClient, false)

	err := processor.ProcessAppServices()
	if err != nil {
		t.Fatalf("ProcessAppServices() error: %v", err)
	}

	// Define test file name and ensure cleanup
	testFileName := "test_snowdata_export.json"
	defer os.Remove(testFileName)

	// Export data to file
	err = processor.ExportSnowDataToFile(testFileName)
	if err != nil {
		t.Fatalf("ExportSnowDataToFile() error: %v", err)
	}

	// Verify file was created
	if _, err := os.Stat(testFileName); os.IsNotExist(err) {
		t.Fatalf("Export file %s was not created", testFileName)
	}

	// Read and validate file contents
	fileContent, err := os.ReadFile(testFileName)
	if err != nil {
		t.Fatalf("Error reading export file: %v", err)
	}

	// Validate file contains valid JSON with expected data
	var snowData mcmp.SnowData
	err = json.Unmarshal(fileContent, &snowData)
	if err != nil {
		t.Fatalf("JSON unmarshal of export file error: %v", err)
	}

	if len(snowData.AppServices) != 2 {
		t.Errorf("Export file SnowData AppServices expected 2, got %d", len(snowData.AppServices))
	}
}
