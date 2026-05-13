package processor

import (
	"encoding/json"
	"fmt"
	"log"
	"os"

	"github.com/it-at-m/mcmp/mcmp-eai-snow/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-snow/pkg/clients/snow"
)

// SnowClientInterface defines the contract for ServiceNow client operations.
// This interface abstracts the ServiceNow API calls to enable dependency injection
// and facilitate unit testing with mock implementations.
type SnowClientInterface interface {
	// GetAppServices retrieves all application services from ServiceNow
	GetAppServices() ([]snow.AppService, error)
	// GetFoundationData fetches foundation data including group information by system ID
	GetFoundationData(sysId string) (snow.FoundationData, error)
	// GetCIsForTag retrieves configuration items associated with a specific tag
	GetCIsForTag(tag string) ([]snow.TagEntry, error)
	// GetCmdbCI fetches detailed configuration item data from CMDB by system ID
	GetCmdbCI(sysId string) (snow.CmdbCi, error)
	// GetVMwareInstances retrieves a list of VMware instances from the ServiceNow CMDB. Returns a slice of CmdbCi and an error.
	GetVMwareInstances() ([]snow.CmdbCi, error)
	GetLockedShutdown() (map[string]string, error)
	GetLockedRightsize() (map[string]string, error)
	GetServerForVMwareInstance(vmInstanceSysID string) (snow.Server, error)
	// EnableDebug activates debug logging for the ServiceNow client
	EnableDebug()
}

// ServiceProcessor orchestrates the processing of ServiceNow data into MCMP format.
// It maintains internal maps to prevent duplicate processing and ensure data consistency.
// The processor handles complex relationships between users, groups, CIs and app services.
type ServiceProcessor struct {
	// snowClient provides access to ServiceNow API operations
	snowClient SnowClientInterface
	// userMap stores processed users indexed by their system ID to prevent duplicates
	userMap map[string]mcmp.User
	// groupMap stores processed groups indexed by their system ID to prevent duplicates
	groupMap map[string]mcmp.Group
	// ciMap stores processed configuration items indexed by their system ID to prevent duplicates
	ciMap map[string]mcmp.CI
	// appServices holds the final list of processed application services
	appServices []mcmp.AppService
	// debug controls whether debug logging is enabled
	lockedShutdownMap  map[string]string
	lockedRightsizeMap map[string]string
	debug              bool
}

// NewServiceProcessor creates a new instance of ServiceProcessor with initialized maps.
// The processor uses dependency injection for the ServiceNow client to improve testability.
//
// Parameters:
//   - snowClient: Implementation of SnowClientInterface for ServiceNow API access
//   - debug: Boolean flag to enable/disable debug logging
//
// Returns:
//   - *ServiceProcessor: Fully initialized processor ready for data processing
func NewServiceProcessor(snowClient SnowClientInterface, debug bool) *ServiceProcessor {
	return &ServiceProcessor{
		snowClient:  snowClient,
		userMap:     make(map[string]mcmp.User),
		groupMap:    make(map[string]mcmp.Group),
		ciMap:       make(map[string]mcmp.CI),
		appServices: make([]mcmp.AppService, 0),
		debug:       debug,
	}
}

// ProcessAppServices is the main orchestration method that processes all ServiceNow application services.
// It fetches app services from ServiceNow and processes their associated users, groups, and CIs.
// The method maintains referential integrity by processing dependencies before the main entities.
//
// Processing workflow:
// 1. Fetch all VMware instances from ServiceNow
// 2. Fetch all app services from ServiceNow
// 3. For each app service:
//   - Process owner and delegate users
//   - Process assignment groups (including their managers and members)
//   - Process associated configuration items
//   - Convert to MCMP format and store
//
// 4. Log processing statistics
//
// Returns:
//   - error: nil on success, or an error describing what went wrong
func (sp *ServiceProcessor) ProcessAppServices() error {
	var err error
	sp.lockedShutdownMap, err = sp.snowClient.GetLockedShutdown()
	if err != nil {
		log.Printf("Error loading locked shutdown data: %v", err)
	}
	sp.lockedRightsizeMap, err = sp.snowClient.GetLockedRightsize()
	if err != nil {
		log.Printf("Error loading locked rightsizing data: %v", err)
	}
	// Fetch all VMware instances from ServiceNow
	snowVMwareInstances, err := sp.snowClient.GetVMwareInstances()
	if err != nil {
		return fmt.Errorf("error loading cmdb_ci_vmware_instance services: %w", err)
	}
	for _, snowVMwareInstance := range snowVMwareInstances {

		// Convert ServiceNow CI format to MCMP format and cache
		mcmpCI := sp.convertSnowCIToMcmpCI(snowVMwareInstance)
		server, err := sp.snowClient.GetServerForVMwareInstance(snowVMwareInstance.SysId)
		if err != nil {
			sp.debugPrintf("Warning: could not fetch server for VMware instance %s: %v", snowVMwareInstance.Name, err)
		} else if server.SysID != "" {
			mcmpCI.ServerSysID = server.SysID
		} else {
			sp.debugPrintf("Info: No server relation found for VMware instance %s", snowVMwareInstance.Name)
		}
		sp.ciMap[snowVMwareInstance.SysId] = mcmpCI
		sp.debugPrintf("CI %s stored in ciMap", snowVMwareInstance.Name)
	}

	// Fetch all application services from ServiceNow
	snowAppServices, err := sp.snowClient.GetAppServices()
	if err != nil {
		return fmt.Errorf("error loading app services: %w", err)
	}

	sp.debugPrintf("Processing %d app services", len(snowAppServices))

	// Process each application service and its dependencies
	for _, snowAppService := range snowAppServices {
		sp.debugPrintf("Processing app service: %s (Number: %s)", snowAppService.Name, snowAppService.Number)

		// Process the service owner user if present
		// This ensures user data is available when converting the app service
		if snowAppService.OwnedBy.SysID != "" {
			sp.processUser(snowAppService.OwnedBy)
		}

		// Process the service owner delegate user if present
		// Delegates can perform owner functions and need to be tracked
		if snowAppService.ServiceOwnerDelegate.SysID != "" {
			sp.processUser(snowAppService.ServiceOwnerDelegate)
		}

		// Process the assignment group including its manager and members
		// Groups are essential for understanding service ownership structure
		if snowAppService.AssignmenGroup != "" {
			if err := sp.processAssignmentGroup(snowAppService.AssignmenGroup); err != nil {
				sp.debugPrintf("Error processing assignment group %s: %v", snowAppService.AssignmenGroup, err)
			}
		}

		// Process configuration items associated with this app service
		// CIs represent the technical infrastructure supporting the service
		var ciSysIDs []string
		if snowAppService.Number != "" {
			ciSysIDs, err = sp.processCIsForAppService(snowAppService.Number)
			if err != nil {
				sp.debugPrintf("Error processing CIs for app service %s: %v", snowAppService.Number, err)
			}
		}

		// Convert ServiceNow format to MCMP format and store
		mcmpAppService := sp.convertSnowAppServiceToMcmpAppService(snowAppService, ciSysIDs)
		sp.appServices = append(sp.appServices, mcmpAppService)
	}

	// Log final processing statistics for monitoring and debugging
	sp.debugPrintf("Processing completed. UserMap: %d, GroupMap: %d, CIMap: %d, AppServices: %d",
		len(sp.userMap), len(sp.groupMap), len(sp.ciMap), len(sp.appServices))

	return nil
}

// processAssignmentGroup processes a ServiceNow assignment group and its related entities.
// It fetches foundation data which includes group details, manager, and member information.
// The method uses caching to avoid reprocessing the same group multiple times.
//
// Processing steps:
// 1. Check if group is already processed (cached)
// 2. Fetch foundation data from ServiceNow
// 3. Process group manager user
// 4. Process all group member users
// 5. Create and cache MCMP group structure
//
// Parameters:
//   - assignmentGroup: System ID of the assignment group to process
//
// Returns:
//   - error: nil on success, or an error if foundation data cannot be retrieved
func (sp *ServiceProcessor) processAssignmentGroup(assignmentGroup string) error {
	// Check cache to avoid duplicate processing
	if _, exists := sp.groupMap[assignmentGroup]; exists {
		sp.debugPrintf("Assignment group %s already exists in groupMap", assignmentGroup)
		return nil
	}

	// Fetch comprehensive group data from ServiceNow
	foundationData, err := sp.snowClient.GetFoundationData(assignmentGroup)
	if err != nil {
		return fmt.Errorf("error loading foundation data for %s: %w", assignmentGroup, err)
	}

	// Process group manager if present
	// Managers have special privileges and must be tracked as users
	var managerSysID string
	if foundationData.Manager.SysID != "" {
		sp.processUser(foundationData.Manager)
		managerSysID = foundationData.Manager.SysID
	}

	// Process all group members
	// Pre-allocate slice for efficiency when member count is known
	memberSysIDs := make([]string, 0, len(foundationData.Members))
	for _, member := range foundationData.Members {
		sp.processUser(member)
		memberSysIDs = append(memberSysIDs, member.SysID)
	}

	// Create MCMP group structure with processed data
	group := mcmp.Group{
		SysID:   foundationData.SysID,
		Name:    foundationData.Name,
		Manager: managerSysID,
		Members: memberSysIDs,
	}

	// Cache the processed group to prevent duplicate processing
	sp.groupMap[assignmentGroup] = group
	sp.debugPrintf("Assignment group %s stored in groupMap", assignmentGroup)

	return nil
}

// processUser processes a ServiceNow user and converts it to MCMP format.
// It uses caching to ensure each user is only processed once, improving performance
// and preventing duplicate entries in the final dataset.
//
// Parameters:
//   - user: ServiceNow user object to process
func (sp *ServiceProcessor) processUser(user snow.User) {
	// Skip processing if user has no system ID (invalid user)
	if user.SysID == "" {
		return
	}

	// Check cache to avoid duplicate processing
	if _, exists := sp.userMap[user.SysID]; exists {
		sp.debugPrintf("User %s already exists in userMap", user.Name)
		return
	}

	// Convert ServiceNow user format to MCMP format
	mcmpUser := sp.convertSnowUserToMcmpUser(user)
	// Cache the processed user
	sp.userMap[user.SysID] = mcmpUser
	sp.debugPrintf("User %s stored in userMap", user.Name)
}

// processCIsForAppService processes all configuration items associated with an application service.
// It uses the app service number as a tag to find related CIs in ServiceNow's tagging system.
// The method fetches detailed CI information and caches it to prevent duplicate processing.
//
// Processing workflow:
// 1. Fetch tag entries for the app service number
// 2. For each tag entry containing a CI reference:
//   - Add CI system ID to the result list
//   - Check if CI is already processed (cached)
//   - Fetch detailed CI data from CMDB
//   - Convert to MCMP format and cache
//
// Parameters:
//   - appServiceNumber: The application service number used as a tag identifier
//
// Returns:
//   - []string: List of CI system IDs associated with the app service
//   - error: nil on success, or an error if tag entries cannot be retrieved
func (sp *ServiceProcessor) processCIsForAppService(appServiceNumber string) ([]string, error) {
	var ciSysIDs []string

	// Fetch tag entries using app service number as tag
	tagEntries, err := sp.snowClient.GetCIsForTag(appServiceNumber)
	if err != nil {
		return ciSysIDs, fmt.Errorf("error loading tag entries for %s: %w", appServiceNumber, err)
	}

	sp.debugPrintf("Found tag entries for %s: %d", appServiceNumber, len(tagEntries))

	// Process each tag entry that contains a CI reference
	for _, tagEntry := range tagEntries {
		// Skip entries without CI reference
		if tagEntry.CI == "" {
			continue
		}

		// Add CI system ID to result list (always done, even if CI is cached)
		ciSysIDs = append(ciSysIDs, tagEntry.CI)

		// Check cache to avoid duplicate CI processing
		if _, exists := sp.ciMap[tagEntry.CI]; exists {
			sp.debugPrintf("CI %s already exists in ciMap", tagEntry.CI)
			continue
		}

		// Fetch detailed CI data from ServiceNow CMDB
		cmdbCI, err := sp.snowClient.GetCmdbCI(tagEntry.CI)
		if err != nil {
			// Log error but continue processing other CIs
			sp.debugPrintf("Error loading CI %s: %v", tagEntry.CI, err)
			continue
		}

		// Convert ServiceNow CI format to MCMP format and cache
		mcmpCI := sp.convertSnowCIToMcmpCI(cmdbCI)
		sp.ciMap[tagEntry.CI] = mcmpCI
		sp.debugPrintf("CI %s stored in ciMap", cmdbCI.Name)
	}

	return ciSysIDs, nil
}

// convertSnowUserToMcmpUser converts a ServiceNow user object to MCMP user format.
// This conversion focuses on essential user attributes needed for MCMP integration.
//
// Parameters:
//   - snowUser: ServiceNow user object to convert
//
// Returns:
//   - mcmp.User: Converted user in MCMP format
func (sp *ServiceProcessor) convertSnowUserToMcmpUser(snowUser snow.User) mcmp.User {
	return mcmp.User{
		SysID:      snowUser.SysID,
		UserID:     snowUser.UserID,
		Department: snowUser.Department,
		Name:       snowUser.Name,
		Email:      snowUser.Email,
	}
}

// convertSnowCIToMcmpCI converts a ServiceNow configuration item to MCMP CI format.
// This conversion maps ServiceNow CMDB fields to their MCMP equivalents, preserving
// essential infrastructure information for asset management and monitoring.
// It also enriches the CI with locked status information for Shutdown and Rightsizing.
func (sp *ServiceProcessor) convertSnowCIToMcmpCI(snowCI snow.CmdbCi) mcmp.CI {
	mcmpCI := mcmp.CI{
		Name:           snowCI.Name,
		SysID:          snowCI.SysId,
		SerialNumber:   snowCI.SerialNumber,
		SysClassName:   snowCI.SysClassName,
		IPAddress:      snowCI.IpAddress,
		FQDN:           snowCI.Fqdn,
		OS:             snowCI.Os,
		OSVersion:      snowCI.OsVersion,
		HardwareStatus: snowCI.HardwareStatus,
		LastDiscovered: snowCI.LastDiscovered,
		VmInstanceUUID: snowCI.VmInstanceUUID,
		MacAddress:     snowCI.MacAddress,
	}

	// Check for locked shutdown status
	if closedAt, exists := sp.lockedShutdownMap[snowCI.SysId]; exists {
		mcmpCI.ShutdownTaskClosedAt = closedAt
		// If closedAt is empty, the change is still open and the CI is locked
		mcmpCI.LockedShutdown = closedAt == ""
	}

	// Check for locked rightsize status
	if closedAt, exists := sp.lockedRightsizeMap[snowCI.SysId]; exists {
		mcmpCI.RightsizeTaskClosedAt = closedAt
		// If closedAt is empty, the change is still open and the CI is locked
		mcmpCI.LockedRightsize = closedAt == ""
	}

	return mcmpCI
}

// convertSnowAppServiceToMcmpAppService converts a ServiceNow application service to MCMP format.
// This conversion creates the final app service structure with references to processed users,
// groups, and CIs using their system IDs for referential integrity.
//
// Parameters:
//   - snowAppService: ServiceNow application service object to convert
//   - ciSysIDs: List of CI system IDs associated with this app service
//
// Returns:
//   - mcmp.AppService: Converted application service in MCMP format
func (sp *ServiceProcessor) convertSnowAppServiceToMcmpAppService(snowAppService snow.AppService, ciSysIDs []string) mcmp.AppService {
	return mcmp.AppService{
		SysID:                  snowAppService.SysID,
		Name:                   snowAppService.Name,
		Number:                 snowAppService.Number,
		Group:                  snowAppService.AssignmenGroup,
		UsedFor:                snowAppService.UsedFor,
		Environment:            snowAppService.Environment,
		CSWEnforced:            snowAppService.CSWEnforced,
		OwnedBy:                snowAppService.OwnedBy.SysID,
		ServiceOwnerDelegate:   snowAppService.ServiceOwnerDelegate.SysID,
		BusinessServiceNumbers: snowAppService.BusinessServiceNumbers,
		CIs:                    ciSysIDs,
	}
}

// debugPrintf provides conditional debug logging functionality.
// Messages are only logged when debug mode is enabled, reducing noise in production.
//
// Parameters:
//   - format: Printf-style format string
//   - a: Variable arguments for format string substitution
func (sp *ServiceProcessor) debugPrintf(format string, a ...interface{}) {
	if sp.debug {
		log.Printf(format, a...)
	}
}

// GetSnowData aggregates all processed data into a single MCMP SnowData structure.
// This method converts the internal maps to slices for JSON serialization and export.
// It's the primary method for retrieving the complete processed dataset.
//
// Returns:
//   - mcmp.SnowData: Complete dataset containing all processed users, groups, CIs, and app services
func (sp *ServiceProcessor) GetSnowData() mcmp.SnowData {
	// Convert user map to slice for JSON serialization
	// Pre-allocate slice for efficiency
	users := make([]mcmp.User, 0, len(sp.userMap))
	for _, user := range sp.userMap {
		users = append(users, user)
	}

	// Convert group map to slice for JSON serialization
	groups := make([]mcmp.Group, 0, len(sp.groupMap))
	for _, group := range sp.groupMap {
		groups = append(groups, group)
	}

	// Convert CI map to slice for JSON serialization
	cis := make([]mcmp.CI, 0, len(sp.ciMap))
	for _, ci := range sp.ciMap {
		cis = append(cis, ci)
	}

	// Return complete aggregated dataset
	return mcmp.SnowData{
		Users:       users,
		Groups:      groups,
		CmdbCIs:     cis,
		AppServices: sp.appServices,
	}
}

// ExportSnowDataAsJSON exports all processed data as a formatted JSON string.
// The JSON is indented for human readability and can be used for API calls or file export.
//
// Returns:
//   - string: JSON representation of all processed data
//   - error: nil on success, or an error if JSON marshaling fails
func (sp *ServiceProcessor) ExportSnowDataAsJSON() (string, error) {
	snowData := sp.GetSnowData()
	jsonData, err := json.MarshalIndent(snowData, "", "  ")
	if err != nil {
		return "", fmt.Errorf("error during JSON marshal: %w", err)
	}
	return string(jsonData), nil
}

// ExportSnowDataToFile exports all processed data to a JSON file.
// This method is useful for data persistence, backup, or integration with external systems.
//
// Parameters:
//   - filename: Path and name of the file to create/overwrite
//
// Returns:
//   - error: nil on success, or an error if JSON creation or file writing fails
func (sp *ServiceProcessor) ExportSnowDataToFile(filename string) error {
	// Generate JSON representation of the data
	jsonString, err := sp.ExportSnowDataAsJSON()
	if err != nil {
		return fmt.Errorf("error creating JSON: %w", err)
	}

	// Write JSON data to file with appropriate permissions (owner read/write, group/others read)
	err = os.WriteFile(filename, []byte(jsonString), 0o644)
	if err != nil {
		return fmt.Errorf("error writing file %s: %w", filename, err)
	}

	sp.debugPrintf("SnowData successfully exported to file %s", filename)
	return nil
}

// GetUserMap returns the internal user map for direct access.
// This method provides access to processed users indexed by their system ID.
//
// Returns:
//   - map[string]mcmp.User: Map of processed users indexed by system ID
func (sp *ServiceProcessor) GetUserMap() map[string]mcmp.User {
	return sp.userMap
}

// GetGroupMap returns the internal group map for direct access.
// This method provides access to processed groups indexed by their system ID.
//
// Returns:
//   - map[string]mcmp.Group: Map of processed groups indexed by system ID
func (sp *ServiceProcessor) GetGroupMap() map[string]mcmp.Group {
	return sp.groupMap
}

// GetCIMap returns the internal CI map for direct access.
// This method provides access to processed configuration items indexed by their system ID.
//
// Returns:
//   - map[string]mcmp.CI: Map of processed CIs indexed by system ID
func (sp *ServiceProcessor) GetCIMap() map[string]mcmp.CI {
	return sp.ciMap
}

// GetAppServices returns the list of processed application services.
// This method provides access to the final processed app services list.
//
// Returns:
//   - []mcmp.AppService: List of processed application services
func (sp *ServiceProcessor) GetAppServices() []mcmp.AppService {
	return sp.appServices
}
