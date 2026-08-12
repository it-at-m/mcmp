package processor

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"regexp"
	"sort"
	"strings"

	"github.com/google/uuid"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/snow"
	"github.com/it-at-m/mcmp/mcmp-eai-snow/pkg/clients/mcmp"
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
	//	GetVMwareInstances() ([]snow.CmdbCi, error)
	GetLockedShutdown() (map[string]string, error)
	GetLockedRightsize() (map[string]string, error)
	GetServerForVMwareInstance(vmInstanceSysID string) (snow.Server, error)
	GetKubernetesNamespaceData() ([]snow.ConfigurationItemWithAppServices, error)
	GetStorageServerData() ([]snow.ConfigurationItemWithAppServices, error)
	GetStorageVolumeData() ([]snow.ConfigurationItemWithAppServices, error)
	GetStorageQTreeData() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiCloudServiceAccountData() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiCloudObjectStorageData() ([]snow.ConfigurationItemWithAppServices, error)
	GetLbServiceData() ([]snow.ConfigurationItemWithAppServices, error)
	GetVMwareInstanceData() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiServerData() ([]snow.ConfigurationItemWithAppServices, error)
	GetPackageRepositoryData() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiDbOraPdbInstance() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiDbOraInstance() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiDbMySQLInstance() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiDbPostgreSQLInstance() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiDbMongoDbInstance() ([]snow.ConfigurationItemWithAppServices, error)
	GetCmdbCiDbMSSQLInstance() ([]snow.ConfigurationItemWithAppServices, error)
	GetDbInstanceToServerMapping() (map[string]map[string]struct{}, error)
	GetOraclePdbToServerMapping() (map[string]map[string]struct{}, map[string]map[string]struct{}, error)

	// EnableDebug activates debug logging for the ServiceNow client
	EnableDebug()
}

type (
	// ServiceProcessor orchestrates the processing of ServiceNow data into MCMP format.
	// It maintains internal maps to prevent duplicate processing and ensure data consistency.
	// The processor handles complex relationships between users, groups, CIs and app services.
	ServiceProcessor struct {
		// snowClient provides access to ServiceNow API operations
		snowClient SnowClientInterface

		// userMap stores processed users indexed by their system ID to prevent duplicates
		userMap map[string]*mcmp.User

		// groupMap stores processed groups indexed by their system ID to prevent duplicates
		groupMap map[string]*mcmp.Group

		// ciMap stores processed configuration items indexed by their system ID to prevent duplicates
		ciMap map[string]*mcmp.ServerCI

		// appServices holds the final list of processed application services
		appServices []mcmp.AppService

		k8sClusters         map[string]*mcmp.KubernetesClusterCI
		storageServers      map[string]*mcmp.ServerCI
		storageVolumes      map[string]*mcmp.StorageCI
		storageQTrees       map[string]*mcmp.StorageCI
		storageAccounts     map[string]*mcmp.CloudObjectCI
		storageBuckets      map[string]*mcmp.CloudObjectCI
		lbServices          map[string]*mcmp.LbServiceCI
		packageRepositories map[string]*mcmp.PackageRepositoryCI
		dbInstances         map[string]*mcmp.DatabaseCI
		dbPdbInstances      map[string]*mcmp.DatabaseCI
		lockedShutdownMap   map[string]string
		lockedRightsizeMap  map[string]string
		// debug controls whether debug logging is enabled
		debug bool
	}
)

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
		snowClient:          snowClient,
		userMap:             make(map[string]*mcmp.User),
		groupMap:            make(map[string]*mcmp.Group),
		ciMap:               make(map[string]*mcmp.ServerCI),
		k8sClusters:         make(map[string]*mcmp.KubernetesClusterCI),
		storageServers:      make(map[string]*mcmp.ServerCI),
		storageVolumes:      make(map[string]*mcmp.StorageCI),
		storageQTrees:       make(map[string]*mcmp.StorageCI),
		storageAccounts:     make(map[string]*mcmp.CloudObjectCI),
		storageBuckets:      make(map[string]*mcmp.CloudObjectCI),
		lbServices:          make(map[string]*mcmp.LbServiceCI),
		packageRepositories: make(map[string]*mcmp.PackageRepositoryCI),
		dbInstances:         make(map[string]*mcmp.DatabaseCI),
		dbPdbInstances:      make(map[string]*mcmp.DatabaseCI),
		appServices:         make([]mcmp.AppService, 0),
		debug:               debug,
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
	err = sp.ProcessKubernetesNamespaces()
	if err != nil {
		return fmt.Errorf("error processing Kubernetes namespaces: %w", err)
	}
	err = sp.ProcessLbServices()
	if err != nil {
		return fmt.Errorf("error processing load balancer services: %w", err)
	}
	err = sp.ProcessStorageServers()
	if err != nil {
		return fmt.Errorf("error processing storage server: %w", err)
	}
	err = sp.ProcessStorageVolumes()
	if err != nil {
		return fmt.Errorf("error processing storage volumes: %w", err)
	}
	err = sp.ProcessStorageQTree()
	if err != nil {
		return fmt.Errorf("error processing storage QTrees: %w", err)
	}
	err = sp.ProcessStorageS3Accounts()
	if err != nil {
		return fmt.Errorf("error processing storage S3 accounts: %w", err)
	}
	err = sp.ProcessStorageS3Buckets()
	if err != nil {
		return fmt.Errorf("error processing storage S3 buckets: %w", err)
	}
	err = sp.ProcessDatabaseInstances()
	if err != nil {
		return fmt.Errorf("error processing database instances: %w", err)
	}
	err = sp.ProcessDatabasePdbInstances()
	if err != nil {
		return fmt.Errorf("error processing database pdb instances: %w", err)
	}
	err = sp.ProcessPackageRepositories()
	if err != nil {
		return fmt.Errorf("error processing package repositories: %w", err)
	}

	sp.lockedShutdownMap, err = sp.snowClient.GetLockedShutdown()
	if err != nil {
		log.Printf("Error loading locked shutdown data: %v", err)
	}
	sp.lockedRightsizeMap, err = sp.snowClient.GetLockedRightsize()
	if err != nil {
		log.Printf("Error loading locked rightsizing data: %v", err)
	}

	snowVMwareInstances, err := sp.snowClient.GetVMwareInstanceData()
	if err != nil {
		return fmt.Errorf("error loading cmdb_ci_vmware_instance services: %w", err)
	}
	for _, snowVMwareInstance := range snowVMwareInstances {

		// Convert ServiceNow CI format to MCMP format and cache
		mcmpCI := sp.convertConfigurationItemWithAppServicesToMcmoCI(snowVMwareInstance)
		server, err := sp.snowClient.GetServerForVMwareInstance(snowVMwareInstance.GetSysID())
		if err != nil {
			sp.debugPrintf("Warning: could not fetch server for VMware instance %s: %v", snowVMwareInstance.GetName(), err)
		} else if server.SysID != "" {
			mcmpCI.ServerSysID = server.SysID
		} else {
			sp.debugPrintf("Info: No server relation found for VMware instance %s", snowVMwareInstance.GetName())
		}
		sp.ciMap[snowVMwareInstance.GetSysID()] = &mcmpCI
		sp.debugPrintf("CI %s stored in ciMap", snowVMwareInstance.GetName())
	}

	snowServers, err := sp.snowClient.GetCmdbCiServerData()
	if err != nil {
		return fmt.Errorf("error loading cmdb_ci_server services: %w", err)
	}
	for _, snowServer := range snowServers {

		// Convert ServiceNow CI format to MCMP format and cache
		mcmpCI := sp.convertConfigurationItemWithAppServicesToMcmoCI(snowServer)
		sp.ciMap[snowServer.GetSysID()] = &mcmpCI
		sp.debugPrintf("CI %s stored in ciMap", snowServer.GetName())
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
	sp.groupMap[assignmentGroup] = &group
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
	sp.userMap[user.SysID] = &mcmpUser
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
		sp.ciMap[tagEntry.CI] = &mcmpCI
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

func (sp *ServiceProcessor) convertConfigurationItemWithAppServicesToMcmoCI(configurationItemWithAppServices snow.ConfigurationItemWithAppServices) mcmp.ServerCI {
	rawCI := configurationItemWithAppServices.RawCI
	sysID := configurationItemWithAppServices.GetSysID()
	mcmpCI := mcmp.ServerCI{
		Name:           configurationItemWithAppServices.GetName(),
		SysID:          sysID,
		SysClassName:   configurationItemWithAppServices.GetSysClassName(),
		LastDiscovered: configurationItemWithAppServices.GetLastDiscovered(),
		IPAddress:      sp.getStringValue(rawCI, "ip_address"),
		FQDN:           sp.getStringValue(rawCI, "fqdn"),
		VmInstanceUUID: sp.getStringValue(rawCI, "vm_instance_uuid"),

		SerialNumber:   sp.getStringValue(rawCI, "serial_number"),
		OS:             sp.getStringValue(rawCI, "os"),
		OSVersion:      sp.getStringValue(rawCI, "os_version"),
		OsDomain:       sp.getStringValue(rawCI, "os_domain"),
		HardwareStatus: sp.getStringValue(rawCI, "hardware_status"),
		MacAddress:     sp.getStringValue(rawCI, "mac_address"),

		Company:              sp.getStringValue(rawCI, "company"),
		DefaultGateway:       sp.getStringValue(rawCI, "default_gateway"),
		DnsDomain:            sp.getStringValue(rawCI, "dns_domain"),
		Environment:          sp.getStringValue(rawCI, "environment"),
		HostName:             sp.getStringValue(rawCI, "host_name"),
		InstallDate:          sp.getStringValue(rawCI, "install_date"),
		InstallStatus:        sp.getStringValue(rawCI, "install_status"),
		LifeCycleStage:       configurationItemWithAppServices.GetLifeCycleStage(),
		LifeCycleStageStatus: configurationItemWithAppServices.GetLifeCycleStageStatus(),
		Manufacturer:         sp.getStringValue(rawCI, "manufacturer"),
		ModelID:              sp.getStringValue(rawCI, "model_id"),
		OperationalStatus:    sp.getStringValue(rawCI, "operational_status"),
		Virtual:              sp.getStringValue(rawCI, "virtual"),

		BiosUUID:    sp.getStringValue(rawCI, "bios_uuid"),
		ObjectID:    sp.getStringValue(rawCI, "object_id"),
		VcenterUUID: sp.getStringValue(rawCI, "vcenter_uuid"),
		Template:    sp.getStringValue(rawCI, "template"),

		AppServiceNumbers: configurationItemWithAppServices.GetAppServiceNumbers(),
	}
	// Check for locked shutdown status
	if closedAt, exists := sp.lockedShutdownMap[sysID]; exists {
		mcmpCI.ShutdownTaskClosedAt = closedAt
		// If closedAt is empty, the change is still open and the CI is locked
		mcmpCI.LockedShutdown = closedAt == ""
	}

	// Check for locked rightsize status
	if closedAt, exists := sp.lockedRightsizeMap[sysID]; exists {
		mcmpCI.RightsizeTaskClosedAt = closedAt
		// If closedAt is empty, the change is still open and the CI is locked
		mcmpCI.LockedRightsize = closedAt == ""
	}

	return mcmpCI
}

// convertSnowCIToMcmpCI converts a ServiceNow configuration item to MCMP CI format.
// This conversion maps ServiceNow CMDB fields to their MCMP equivalents, preserving
// essential infrastructure information for asset management and monitoring.
// It also enriches the CI with locked status information for Shutdown and Rightsizing.
func (sp *ServiceProcessor) convertSnowCIToMcmpCI(snowCI snow.CmdbCi) mcmp.ServerCI {
	mcmpCI := mcmp.ServerCI{
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
		ServerCIs:              ciSysIDs,
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
	storageVolumes := mapToSlice(sp.storageVolumes)
	storageQTrees := mapToSlice(sp.storageQTrees)
	databaseInstances := mapToSlice(sp.dbInstances)
	databasePdbInstances := mapToSlice(sp.dbPdbInstances)

	// Sort helper for StorageCI
	sortCIs := func(cis []mcmp.StorageCI) {
		sort.Slice(cis, func(i, j int) bool {
			if cis[i].VolumeID != cis[j].VolumeID {
				return cis[i].VolumeID < cis[j].VolumeID
			}
			return cis[i].QTreeID < cis[j].QTreeID
		})
	}

	sortCIs(storageVolumes)
	sortCIs(storageQTrees)

	sort.Slice(databaseInstances, func(i, j int) bool {
		if databaseInstances[i].SysClass != databaseInstances[j].SysClass {
			return databaseInstances[i].SysClass < databaseInstances[j].SysClass
		}
		return databaseInstances[i].Name < databaseInstances[j].Name
	})

	sort.Slice(databasePdbInstances, func(i, j int) bool {
		if databasePdbInstances[i].SysClass != databasePdbInstances[j].SysClass {
			return databasePdbInstances[i].SysClass < databasePdbInstances[j].SysClass
		}
		return databasePdbInstances[i].Name < databasePdbInstances[j].Name
	})

	return mcmp.SnowData{
		Users:                  mapToSlice(sp.userMap),
		Groups:                 mapToSlice(sp.groupMap),
		CmdbCIs:                mapToSlice(sp.ciMap),
		AppServices:            sp.appServices,
		KubernetesClusterCIs:   mapToSlice(sp.k8sClusters),
		StorageServerCIs:       mapToSlice(sp.storageServers),
		StorageVolumeCIs:       mapToSlice(sp.storageVolumes),
		StorageQTreeCIs:        mapToSlice(sp.storageQTrees),
		StorageAccountCIs:      mapToSlice(sp.storageAccounts),
		StorageBucketCIs:       mapToSlice(sp.storageBuckets),
		LbServiceCI:            mapToSlice(sp.lbServices),
		PackageRepositoryCIs:   mapToSlice(sp.packageRepositories),
		DatabaseInstanceCIs:    databaseInstances,
		DatabasePdbInstanceCIs: databasePdbInstances,
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

// GetAppServices returns the list of processed application services.
// This method provides access to the final processed app services list.
//
// Returns:
//   - []mcmp.AppService: List of processed application services
func (sp *ServiceProcessor) GetAppServices() []mcmp.AppService {
	return sp.appServices
}

func (sp *ServiceProcessor) GetUserMap() map[string]*mcmp.User {
	return sp.userMap
}

func (sp *ServiceProcessor) GetGroupMap() map[string]*mcmp.Group {
	return sp.groupMap
}

func (sp *ServiceProcessor) GetCIMap() map[string]*mcmp.ServerCI {
	return sp.ciMap
}

// ProcessKubernetesNamespaces fetches and transforms Kubernetes namespace data from ServiceNow.
func (sp *ServiceProcessor) ProcessKubernetesNamespaces() error {
	dataList, err := sp.snowClient.GetKubernetesNamespaceData()
	if err != nil {
		return fmt.Errorf("error loading kubernetes namespaces: %w", err)
	}

	for _, data := range dataList {
		res := data.RawCI
		sysID := data.GetSysID()
		if sysID == "" {
			continue
		}

		// Check if the cluster has the correct sys_class
		clusterClass := sp.getStringValue(res, "cluster.sys_class_name")
		if clusterClass != "cmdb_ci_kubernetes_cluster" {
			sp.debugPrintf("Skipping namespace %s because cluster class is %s", data.GetName(), clusterClass)
			continue
		}

		clusterSysID := sp.getStringValue(res, "cluster.sys_id")
		if clusterSysID == "" {
			continue
		}

		cluster, exists := sp.k8sClusters[clusterSysID]
		if !exists {
			cluster = &mcmp.KubernetesClusterCI{
				Name:                 sp.getStringValue(res, "cluster.name"),
				SysID:                sp.getStringValue(res, "cluster.sys_id"),
				SysClass:             sp.getStringValue(res, "cluster.sys_class_name"),
				LastDiscovered:       sp.getStringValue(res, "cluster.last_discovered"),
				LifeCycleStage:       sp.getNestedValue(res, "cluster.life_cycle_stage"),
				LifeCycleStageStatus: sp.getNestedValue(res, "cluster.life_cycle_stage_status"),
				K8SUID:               sp.getStringValue(res, "cluster.k8s_uid"),
				Environment:          sp.getStringValue(res, "cluster.environment"),
			}
			sp.k8sClusters[clusterSysID] = cluster
		}

		k8sUID := sp.getStringValue(res, "k8s_uid")
		if k8sUID != "" {
			parts := strings.Split(k8sUID, " ")
			k8sUID = parts[0]
		}
		ns := mcmp.KubernetesNamespaceCI{
			Name:                 data.GetName(),
			SysID:                sysID,
			SysClass:             data.GetSysClassName(),
			LastDiscovered:       data.GetLastDiscovered(),
			LifeCycleStage:       data.GetLifeCycleStage(),
			LifeCycleStageStatus: data.GetLifeCycleStageStatus(),
			K8sUID:               k8sUID,
			Environment:          sp.getStringValue(res, "environment"),
			AppServiceNumbers:    data.GetAppServiceNumbers(),
		}
		cluster.KubernetesNamespaceCIs = append(cluster.KubernetesNamespaceCIs, ns)

	}
	return nil
}

func (sp *ServiceProcessor) ProcessStorageServers() error {
	dataList, err := sp.snowClient.GetStorageServerData()
	if err != nil {
		return fmt.Errorf("error loading storage server: %w", err)
	}

	for _, data := range dataList {
		res := data.RawCI
		sysID := data.GetSysID()
		if sysID == "" {
			continue
		}
		storageServerCI := &mcmp.ServerCI{
			Name:                 data.GetName(),
			SysID:                sysID,
			SysClassName:         data.GetSysClassName(),
			LifeCycleStage:       data.GetLifeCycleStage(),
			LifeCycleStageStatus: data.GetLifeCycleStageStatus(),
			LastDiscovered:       data.GetLastDiscovered(),
			SerialNumber:         sp.getStringValue(res, "serial_number"),
			AppServiceNumbers:    data.GetAppServiceNumbers(),
		}
		sp.storageServers[sysID] = storageServerCI
	}
	return nil
}

// ProcessStorageVolumes fetches and transforms Storage Volume data from ServiceNow.
func (sp *ServiceProcessor) ProcessStorageVolumes() error {
	dataList, err := sp.snowClient.GetStorageVolumeData()
	if err != nil {
		return fmt.Errorf("error loading storage volumes: %w", err)
	}

	for _, data := range dataList {
		res := data.RawCI
		sysID := data.GetSysID()
		if sysID == "" {
			continue
		}
		storageType := sp.getStringValue(res, "storage_type")
		volumeID := sp.getStringValue(res, "volume_id")
		qTreeID := ""
		if storageType == "QTree" && volumeID != "" {
			parts := strings.Split(volumeID, "/")
			volumeID = parts[0]
			if len(parts) > 1 {
				qTreeID = parts[1]
			}
			if len(parts) < 2 {
				fmt.Printf("Invalid volume ID format %s, sys_id %s\n", volumeID, sysID)
			}
		}

		// Skip invalid volume IDs that are not a valid UUID
		if _, err := uuid.Parse(volumeID); err != nil {
			sp.debugPrintf("Skipping volume %s because volumeID %s is not a valid UUID, sys_id %s", data.GetName(), volumeID, sysID)
			continue
		}

		storageVolumeCI := &mcmp.StorageCI{
			Name:                 data.GetName(),
			SysID:                sysID,
			SysClass:             data.GetSysClassName(),
			LifeCycleStage:       data.GetLifeCycleStage(),
			LifeCycleStageStatus: data.GetLifeCycleStageStatus(),
			LastDiscovered:       data.GetLastDiscovered(),
			StorageType:          storageType,
			ClusterID:            sp.getStringValue(res, "cluster_id"),
			VolumeID:             volumeID,
			QTreeID:              qTreeID,
			ObjectID:             sp.getStringValue(res, "object_id"),
			SvmUUID:              sp.getStringValue(res, "computer.serial_number"),
			AppServiceNumbers:    data.GetAppServiceNumbers(),
		}
		sp.storageVolumes[sysID] = storageVolumeCI
	}
	return nil
}

// ProcessStorageVolumes fetches and transforms Storage Volume data from ServiceNow.
func (sp *ServiceProcessor) ProcessStorageQTree() error {
	dataList, err := sp.snowClient.GetStorageQTreeData()
	if err != nil {
		return fmt.Errorf("error loading storage volumes: %w", err)
	}

	for _, data := range dataList {
		res := data.RawCI
		sysID := data.GetSysID()
		if sysID == "" {
			continue
		}
		storageType := sp.getStringValue(res, "storage_type")
		volumeID := sp.getStringValue(res, "volume_id")
		qTreeID := ""
		if storageType == "QTree" && volumeID != "" {
			parts := strings.Split(volumeID, "/")
			volumeID = parts[0]
			if len(parts) > 1 {
				qTreeID = parts[1]
			}
			if len(parts) < 2 {
				fmt.Printf("Invalid volume ID format %s, sys_id %s\n", volumeID, sysID)
			}
		}

		// Skip invalid volume IDs that are not a valid UUID
		if _, err := uuid.Parse(volumeID); err != nil {
			sp.debugPrintf("Skipping QTree %s because volumeID %s is not a valid UUID, sys_id %s", data.GetName(), volumeID, sysID)
			continue
		}

		storageQTreeCI := &mcmp.StorageCI{
			Name:                 data.GetName(),
			SysID:                sysID,
			SysClass:             data.GetSysClassName(),
			LifeCycleStage:       data.GetLifeCycleStage(),
			LifeCycleStageStatus: data.GetLifeCycleStageStatus(),
			LastDiscovered:       data.GetLastDiscovered(),
			StorageType:          storageType,
			ClusterID:            sp.getStringValue(res, "cluster_id"),
			VolumeID:             volumeID,
			QTreeID:              qTreeID,
			ObjectID:             sp.getStringValue(res, "object_id"),
			SvmUUID:              sp.getStringValue(res, "computer.serial_number"),
			AppServiceNumbers:    data.GetAppServiceNumbers(),
		}
		sp.storageQTrees[sysID] = storageQTreeCI
	}
	return nil
}

func (sp *ServiceProcessor) ProcessStorageS3Accounts() error {
	dataList, err := sp.snowClient.GetCmdbCiCloudServiceAccountData()
	if err != nil {
		return fmt.Errorf("failed to fetch cloud service account for table cmdb_ci_cloud_service_account: %w", err)
	}
	for _, data := range dataList {
		res := data.RawCI
		sysID := sp.getStringValue(res, "configuration_item.sys_id")
		if sysID == "" {
			continue
		}
		tenant := &mcmp.CloudObjectCI{
			SysID:             sysID,
			Name:              sp.getStringValue(res, "configuration_item.name"),
			SysClass:          sp.getStringValue(res, "configuration_item.sys_class_name"),
			AccountId:         sp.getStringValue(res, "value"),
			AppServiceNumbers: data.GetAppServiceNumbers(),
		}
		sp.storageAccounts[sysID] = tenant
	}
	return nil
}

func (sp *ServiceProcessor) ProcessStorageS3Buckets() error {
	dataList, err := sp.snowClient.GetCmdbCiCloudObjectStorageData()
	if err != nil {
		return fmt.Errorf("failed to fetch cloud object storage for table cmdb_ci_cloud_object_storage: %w", err)
	}
	for _, data := range dataList {
		res := data.RawCI
		sysID := sp.getStringValue(res, "configuration_item.sys_id")
		if sysID == "" {
			continue
		}
		bucket := &mcmp.CloudObjectCI{
			SysID:             sysID,
			Name:              sp.getStringValue(res, "configuration_item.name"),
			SysClass:          sp.getStringValue(res, "configuration_item.sys_class_name"),
			AccountId:         sp.getStringValue(res, "value"),
			AppServiceNumbers: data.GetAppServiceNumbers(),
		}
		sp.storageBuckets[sysID] = bucket
	}
	return nil
}

// ProcessLbServices fetches and transforms Loadbalancer Service data from ServiceNow.
func (sp *ServiceProcessor) ProcessLbServices() error {
	dataList, err := sp.snowClient.GetLbServiceData()
	if err != nil {
		return fmt.Errorf("error loading lb services: %w", err)
	}

	for _, data := range dataList {
		sysID := data.GetSysID()
		lbServiceCI := &mcmp.LbServiceCI{
			Name:                 data.GetName(),
			SysID:                sysID,
			SysClass:             data.GetSysClassName(),
			LifeCycleStage:       data.GetLifeCycleStage(),
			LifeCycleStageStatus: data.GetLifeCycleStageStatus(),
			LastDiscovered:       data.GetLastDiscovered(),
			AppServiceNumbers:    data.GetAppServiceNumbers(),
		}
		sp.lbServices[sysID] = lbServiceCI
	}
	return nil
}

// ProcessLbServices fetches and transforms Loadbalancer Service data from ServiceNow.
func (sp *ServiceProcessor) ProcessPackageRepositories() error {
	dataList, err := sp.snowClient.GetPackageRepositoryData()
	if err != nil {
		return fmt.Errorf("error loading package repositories: %w", err)
	}

	for _, data := range dataList {
		sysID := data.GetSysID()
		packageRepositoryCI := &mcmp.PackageRepositoryCI{
			Name:                 data.GetName(),
			SysID:                sysID,
			SysClass:             data.GetSysClassName(),
			LifeCycleStage:       data.GetLifeCycleStage(),
			LifeCycleStageStatus: data.GetLifeCycleStageStatus(),
			LastDiscovered:       data.GetLastDiscovered(),
			AppServiceNumbers:    data.GetAppServiceNumbers(),
		}
		sp.packageRepositories[sysID] = packageRepositoryCI
	}
	return nil
}

func (sp *ServiceProcessor) ProcessDatabaseInstances() error {
	dbToServers, err := sp.snowClient.GetDbInstanceToServerMapping()
	if err != nil {
		return fmt.Errorf("error loading db instance mapping: %w", err)
	}

	fetchFuncs := []func() ([]snow.ConfigurationItemWithAppServices, error){
		sp.snowClient.GetCmdbCiDbOraInstance,
		sp.snowClient.GetCmdbCiDbMySQLInstance,
		sp.snowClient.GetCmdbCiDbPostgreSQLInstance,
		sp.snowClient.GetCmdbCiDbMongoDbInstance,
		sp.snowClient.GetCmdbCiDbMSSQLInstance,
	}

	for _, fetch := range fetchFuncs {
		dataList, err := fetch()
		if err != nil {
			return err
		}

		for _, data := range dataList {
			sysID := data.GetSysID()
			if sysID == "" {
				continue
			}

			dbInstance := sp.createDatabaseCI(data)

			if servers, ok := dbToServers[sysID]; ok {
				for srvID := range servers {
					dbInstance.ServerSysID = append(dbInstance.ServerSysID, srvID)
				}
				sort.Strings(dbInstance.ServerSysID)
			}

			sp.dbInstances[sysID] = dbInstance
		}
	}
	return nil
}

// ProcessDatabasePdbInstances fetches and transforms Oracle PDB data from ServiceNow.
func (sp *ServiceProcessor) ProcessDatabasePdbInstances() error {
	pdbToInstance, pdbToServer, err := sp.snowClient.GetOraclePdbToServerMapping()
	if err != nil {
		return fmt.Errorf("error loading oracle pdb mapping: %w", err)
	}

	dataList, err := sp.snowClient.GetCmdbCiDbOraPdbInstance()
	if err != nil {
		return err
	}

	for _, data := range dataList {
		sysID := data.GetSysID()
		if sysID == "" {
			continue
		}

		dbInstance := sp.createDatabaseCI(data)

		if instances, ok := pdbToInstance[sysID]; ok {
			for instID := range instances {
				dbInstance.DBInstanceSysID = append(dbInstance.DBInstanceSysID, instID)
			}
			sort.Strings(dbInstance.DBInstanceSysID)
		}

		if servers, ok := pdbToServer[sysID]; ok {
			for srvID := range servers {
				dbInstance.ServerSysID = append(dbInstance.ServerSysID, srvID)
			}
			sort.Strings(dbInstance.ServerSysID)
		}

		sp.dbPdbInstances[sysID] = dbInstance
	}
	return nil
}

func (sp *ServiceProcessor) createDatabaseCI(data snow.ConfigurationItemWithAppServices) *mcmp.DatabaseCI {
	res := data.RawCI
	dbInstance := &mcmp.DatabaseCI{
		Name:                  data.GetName(),
		SysID:                 data.GetSysID(),
		SysClass:              data.GetSysClassName(),
		LastDiscovered:        data.GetLastDiscovered(),
		LifeCycleStage:        data.GetLifeCycleStage(),
		LifeCycleStageStatus:  data.GetLifeCycleStageStatus(),
		InstallDirectory:      sp.getStringValue(res, "install_directory"),
		RunningProcessCommand: sp.getStringValue(res, "running_process_command"),
		TcpPort:               sp.getStringValue(res, "tcp_port"),
		Version:               sp.getStringValue(res, "version"),
		Sid:                   sp.getStringValue(res, "sid"),
		ConfigFile:            sp.getStringValue(res, "config_file"),
		InstanceName:          sp.getStringValue(res, "instance_name"),
		PFile:                 sp.getStringValue(res, "pfile"),
		AppServiceNumbers:     data.GetAppServiceNumbers(),
	}

	if dbInstance.PFile != "" {
		sp.parseOraclePFile(dbInstance)
	}
	return dbInstance
}

// parseOraclePFile extracts protocol, host and port from an Oracle pfile/connection descriptor string.
func (sp *ServiceProcessor) parseOraclePFile(db *mcmp.DatabaseCI) {
	// Common Oracle descriptor patterns: (PROTOCOL=tcp), (HOST=myhost), (PORT=1521)
	protocolRegex := regexp.MustCompile(`(?i)PROTOCOL\s*=\s*([^)\s]+)`)
	hostRegex := regexp.MustCompile(`(?i)HOST\s*=\s*([^)\s]+)`)
	portRegex := regexp.MustCompile(`(?i)PORT\s*=\s*([^)\s]+)`)

	if match := protocolRegex.FindStringSubmatch(db.PFile); len(match) > 1 {
		db.PFileProtocol = strings.ToLower(match[1])
	}
	if match := hostRegex.FindStringSubmatch(db.PFile); len(match) > 1 {
		db.PFileHost = match[1]
	}
	if match := portRegex.FindStringSubmatch(db.PFile); len(match) > 1 {
		db.PFilePort = match[1]
	}
}

func (sp *ServiceProcessor) getStringValue(m map[string]any, key string) string {
	if val, ok := m[key].(string); ok {
		return val
	}
	return ""
}

func (sp *ServiceProcessor) getNestedValue(m map[string]any, key string) string {
	if val, ok := m[key].(string); ok {
		return val
	}
	if nested, ok := m[key].(map[string]any); ok {
		if v, exists := nested["value"].(string); exists {
			return v
		}
	}
	return ""
}

// mapToSlice converts a map of pointers to a slice of values.
func mapToSlice[T any](m map[string]*T) []T {
	s := make([]T, 0, len(m))
	for _, v := range m {
		if v != nil {
			s = append(s, *v)
		}
	}
	return s
}
