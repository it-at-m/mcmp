package processor

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/clients/foreman"
	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-foreman/pkg/logging"
)

type (
	// PatchnightInfo represents information about patchnight scheduling for a host.
	// It contains the group assignment and start time for maintenance windows.
	PatchnightInfo struct {
		Group     string // Patchnight group identifier (e.g., "p4", "p1")
		StartTime string // Formatted start time in HH:MM format (e.g., "02:00")
	}

	// ForemanClientInterface defines the contract for interacting with the Foreman API.
	// This interface enables dependency injection and improves testability by allowing
	// mock implementations during unit testing.
	ForemanClientInterface interface {
		// GetAllHostsWithContext retrieves all hosts from the Foreman API with context support.
		// Returns a slice of foreman.Host objects containing basic host information.
		GetAllHostsWithContext(ctx context.Context) ([]foreman.Host, error)

		// GetHostWithContext retrieves detailed information for a specific host by ID.
		// This method provides comprehensive host details including facts, interfaces, and collections.
		GetHostWithContext(ctx context.Context, id string) (*foreman.Host, error)

		// GetConfiguredParallelQueries returns the configured number of parallel API queries.
		// This value is used to limit concurrent requests to the Foreman API.
		GetConfiguredParallelQueries() int

		// GetAPIEndpoint returns the configured base URL of the Foreman API
		GetAPIEndpoint() string

		// EnableDebug enables debug logging for the Foreman client operations.
		EnableDebug()
	}

	// MCMPClientInterface defines the contract for sending data to the MCMP system.
	// This interface abstracts the communication with the MCMP API endpoint.
	MCMPClientInterface interface {
		// SendForemanData sends processed Foreman data to the specified MCMP endpoint.
		// The jsonData parameter contains the serialized host information.
		SendForemanData(ctx context.Context, endpoint string, jsonData []byte) error
	}

	// ServiceProcessor is the main processing engine that orchestrates data flow
	// between Foreman and MCMP systems. It handles concurrent processing, data transformation,
	// and provides comprehensive logging capabilities.
	ServiceProcessor struct {
		*logging.DebugLogger                          // Embedded debug logger for detailed logging
		foremanClients       []ForemanClientInterface // Foreman API client implementation
		mcmpClient           MCMPClientInterface      // MCMP API client implementation
		endpoint             string                   // MCMP endpoint URL for data transmission
		debug                bool                     // Debug mode flag for verbose logging
		foremanData          *mcmp.ForemanData        // Processed and transformed host data
	}
)

var (
	patchnightPattern     = regexp.MustCompile(`^Patchnight_([^_]+)_(\d{4})$`)
	mountpointPattern     = regexp.MustCompile(`^mountpoints::([^:]+)::(.+)$`)
	partitionsPattern     = regexp.MustCompile(`^partitions::([^:]+)::(.+)$`)
	logicalVolumesPattern = regexp.MustCompile(`^logical_volumes::([^:]+)::(.+)$`)
)

// NewServiceProcessor creates a new instance of ServiceProcessor with initialized clients.
// The processor uses dependency injection for both Foreman and MCMP clients to improve testability.
//
// This constructor sets up all necessary components for processing Foreman host data:
// - Initializes debug logging capabilities
// - Configures client interfaces for external API communication
// - Prepares internal data structures for host information storage
//
// Parameters:
//   - foremanClient: Implementation of ForemanClientInterface for Foreman API access
//   - debug: Boolean flag to enable/disable debug logging throughout processing
//
// Returns:
//   - *ServiceProcessor: Fully initialized processor ready for data processing operations
//
// Example usage:
//
//	processor := NewServiceProcessor(foremanClients, true)
//	err := processor.ProcessForemanHosts()
//	if err != nil {
//	    log.Fatal("Processing failed:", err)
//	}
func NewServiceProcessor(foremanClients []*foreman.Client, debug bool) *ServiceProcessor {
	clients := make([]ForemanClientInterface, len(foremanClients))
	for i, c := range foremanClients {
		clients[i] = c
	}
	logger := logging.NewDebugLogger(nil)
	if debug {
		logger.EnableDebug()
	}
	return &ServiceProcessor{
		DebugLogger:    logger,
		foremanClients: clients,
		debug:          debug,
		foremanData:    &mcmp.ForemanData{Hosts: []mcmp.Host{}},
	}
}

// ProcessForemanHosts orchestrates the complete workflow for processing Foreman host data.
// This method implements a multi-stage pipeline with concurrent processing capabilities:
//
// Processing Pipeline:
// 1. Retrieves the configured number of worker goroutines for parallel processing
// 2. Fetches all hosts from the Foreman API using the configured client
// 3. Sets up concurrent processing with worker goroutines for optimal performance
// 4. Displays real-time progress information during processing (when debug enabled)
// 5. Fetches detailed host information for each host using individual API calls
// 6. Transforms raw Foreman host data into the standardized MCMP struct format
// 7. Collects and aggregates all processed data into the internal data structure
//
// Concurrency Model:
// - Uses a configurable number of worker goroutines for parallel host processing
// - Implements channels for safe communication between goroutines
// - Provides error isolation - individual host processing failures don't stop the entire process
// - Progress tracking with real-time status updates
//
// Error Handling:
// - Graceful degradation: processing continues even if individual hosts fail
// - Comprehensive error collection and reporting
// - Detailed logging of all processing steps and failures
//
// Returns:
//   - error: nil on successful completion, detailed error message on critical failures
//
// Example usage:
//
//	processor := NewServiceProcessor(foremanClients, debug)
//	err := processor.ProcessForemanHosts()
//	if err != nil {
//	    log.Printf("Processing completed with errors: %v", err)
//	}
//
//	// Access processed data
//	data := processor.GetForemanData()
//	fmt.Printf("Successfully processed %d hosts", len(data.Hosts))
func (sp *ServiceProcessor) ProcessForemanHosts(ctx context.Context) error {
	sp.debugPrintf("Starting Foreman hosts processing...")

	// Store hosts per client to schedule workers correctly
	clientHosts := make(map[ForemanClientInterface][]foreman.Host)
	totalHostCount := 0

	// Step 1: Retrieve all hosts from all Foreman APIs
	for i, client := range sp.foremanClients {
		sp.debugPrintf("Retrieving hosts from Foreman instance %d...", i+1)
		hosts, err := client.GetAllHostsWithContext(ctx)
		if err != nil {
			return fmt.Errorf("failed to retrieve hosts from Foreman instance %d: %w", i+1, err)
		}
		clientHosts[client] = hosts
		totalHostCount += len(hosts)
		sp.debugPrintf("Retrieved %d hosts from Foreman instance %d", len(hosts), i+1)
	}

	sp.debugPrintf("Total hosts to process: %d", totalHostCount)

	if totalHostCount == 0 {
		sp.debugPrintf("No hosts found in any Foreman instance.")
		return nil
	}

	// Channels for results and errors
	resultChannel := make(chan mcmp.Host, totalHostCount)
	errorChannel := make(chan error, totalHostCount)

	// WaitGroup to track all workers
	var wg sync.WaitGroup

	// Step 2: Start workers for each client
	// We create separate worker pools for each client to respect their individual ParallelQueries limits
	for client, hosts := range clientHosts {
		maxWorkers := client.GetConfiguredParallelQueries()
		if len(hosts) == 0 {
			continue
		}

		sp.debugPrintf("Starting %d workers for a client with %d hosts", maxWorkers, len(hosts))

		// Channel specific for this client's workers
		hostChannel := make(chan foreman.Host, len(hosts))

		// Fill the channel
		for _, host := range hosts {
			hostChannel <- host
		}
		close(hostChannel)

		// Start workers bound to this client
		for i := 0; i < maxWorkers; i++ {
			wg.Add(1)
			go func(c ForemanClientInterface, hChan <-chan foreman.Host) {
				defer wg.Done()
				sp.processHostWorker(ctx, c, hChan, resultChannel, errorChannel)
			}(client, hostChannel)
		}
	}

	// Close result channels when all workers are done
	go func() {
		wg.Wait()
		close(resultChannel)
		close(errorChannel)
	}()

	// Collect results with real-time progress tracking
	var processedHosts []mcmp.Host
	var errors []error
	processedCount := 0

	sp.debugPrintf("Progress tracking:")
	resultsOpen, errorsOpen := true, true
	for resultsOpen || errorsOpen {
		select {
		case mcmpHost, ok := <-resultChannel:
			if !ok {
				resultsOpen = false
				resultChannel = nil // Channel auf nil setzen, um select zu blockieren
				continue
			}
			processedHosts = append(processedHosts, mcmpHost)
			processedCount++
			if sp.debug {
				sp.printProgressUpdate(processedCount, totalHostCount)
			}
		case err, ok := <-errorChannel:
			if !ok {
				errorsOpen = false
				errorChannel = nil // Channel auf nil setzen
				continue
			}
			errors = append(errors, err)
			processedCount++
			if sp.debug {
				sp.printProgressUpdate(processedCount, totalHostCount)
			}
		}
	}

	sp.debugPrintf("\nProcessing completed successfully\n")
	sp.debugPrintf("Successfully processed hosts: %d\n", len(processedHosts))
	sp.debugPrintf("Processing errors encountered: %d\n", len(errors))

	// Log any errors but continue processing
	if len(errors) > 0 {
		sp.debugPrintf("Encountered %d errors during processing:", len(errors))
		for _, err := range errors {
			sp.debugPrintf("Processing error: %v", err)
		}
	}

	// Update internal data structure
	sp.foremanData.Hosts = processedHosts

	sp.debugPrintf("Processing summary: %d hosts processed successfully (with %d errors)", len(processedHosts), len(errors))
	return nil
}

// printProgressUpdate displays real-time progress information during host processing.
// This method provides visual feedback on processing status with percentage completion.
//
// Parameters:
//   - processed: Number of hosts that have been processed so far
//   - total: Total number of hosts to be processed
//
// Example usage:
//
//	sp.printProgressUpdate(50, 100) // Outputs: "Processed: 50/100 (50.0%)"
func (sp *ServiceProcessor) printProgressUpdate(processed, total int) {
	percentage := float64(processed) / float64(total) * 100
	timestamp := time.Now().Format("15:04:05")
	sp.debugPrintf("\rVerarbeitet: %d/%d (%.1f%%)", processed, total, percentage)
	fmt.Printf("\r[%s] Verarbeitet: %d/%d (%.1f%%)\n", timestamp, processed, total, percentage)
}

// processHostWorker is a worker goroutine that processes individual hosts concurrently.
// This worker function runs in parallel with other workers to maximize throughput
// while fetching detailed host information and transforming it to MCMP format.
//
// Worker Responsibilities:
// - Processes hosts from the input channel until the channel is closed
// - Fetches comprehensive host details using the Foreman API
// - Transforms raw Foreman data into standardized MCMP format
// - Handles individual host processing errors gracefully
// - Sends results through appropriate channels for collection
//
// Parameters:
//   - ctx: Context for request cancellation and timeout control
//   - client: The specific Foreman client to use for fetching details
//   - hostChannel: Input channel for receiving hosts to process
//   - resultChannel: Output channel for successfully processed hosts
//   - errorChannel: Output channel for processing errors
//
// The worker implements graceful error handling - if detailed host information
// cannot be retrieved, it falls back to using basic host information and
// reports the error without stopping processing.
func (sp *ServiceProcessor) processHostWorker(ctx context.Context, client ForemanClientInterface, hostChan <-chan foreman.Host, resultChan chan<- mcmp.Host, errorChan chan<- error) {
	// Extract hostname from client endpoint
	endpoint := client.GetAPIEndpoint()
	foremanHostname := endpoint
	if u, err := url.Parse(endpoint); err == nil {
		foremanHostname = u.Hostname()
	}

	for host := range hostChan {
		select {
		case <-ctx.Done():
			errorChan <- ctx.Err()
			return
		default:
		}

		sp.debugPrintf("Processing host: ID=%d, Name=%s", host.ID, host.Name)

		// Erstelle separaten Context mit Timeout für jeden Request
		requestCtx, cancel := context.WithTimeout(ctx, 2*time.Minute) // 2 Minuten pro Request

		// Attempt to fetch comprehensive host information using host ID
		detailedHost, err := client.GetHostWithContext(requestCtx, strconv.Itoa(host.ID))
		cancel()

		if err != nil {
			log.Printf("Warning: Failed to get detailed information for host ID %d: %v", host.ID, err)
			detailedHost = &host
			// Optional: Trotzdem als Fehler melden ODER nur loggen, je nach Anforderung
			errorChan <- fmt.Errorf("failed to get detailed info for host %d (%s) - using fallback: %w", host.ID, host.Name, err)
		}
		// Transform raw Foreman host data into standardized MCMP struct format
		mcmpHost := sp.transformForemanHostToMCMP(detailedHost, foremanHostname)
		sp.debugPrintf("Successfully processed and transformed host: %s", mcmpHost.Name)
		resultChan <- mcmpHost
	}

}

// transformForemanHostToMCMP transforms a Foreman host into the standardized MCMP host structure.
// This method performs comprehensive data conversion, enrichment, and validation to ensure
// all relevant host information is properly mapped and formatted for MCMP consumption.
//
// Transformation Process:
//
// 1. Basic Field Mapping:
//   - Standard host information (ID, Name, FQDN, IP addresses, MAC addresses)
//   - Operating system details (Name, Family, Major version)
//   - Timestamp information (CreatedAt, InitiatedAt, InstalledAt)
//   - Hardware identification (UUID, Serial number, Architecture)
//   - Network configuration (Subnet information, Compute resources)
//
// 2. Network Interface Transformation:
//   - Converts all network interfaces from Foreman format to MCMP format
//   - Preserves all interface properties (IP, IPv6, MAC, FQDN, MTU, etc.)
//   - Maintains interface metadata (Primary, Managed, Virtual status)
//   - Handles interface-specific network configuration
//
// 3. Facts Extraction and Processing:
//   - FQDN and serial number extraction from system facts
//   - LHM patchnight status information (exit codes, exit messages)
//   - Tetration security agent installation status
//   - Database information (Oracle DB version, Oracle SID)
//   - MySQL/MariaDB version information when database services are detected
//   - System mountpoint information for NFS/CIFS filesystems
//
// 4. Advanced Data Processing and Enrichment:
//   - Intelligent database and operating system type determination
//   - Patchnight scheduling information extracted from host collections
//   - Filesystem mountpoint analysis for network storage systems
//   - System service and application detection
//
// Data Validation and Safety:
// - Null-safe implementation that handles missing or invalid data gracefully
// - Type conversion with error handling for all data transformations
// - Default value assignment for missing optional fields
// - Comprehensive logging of transformation steps and any issues encountered
//
// Parameters:
//   - foremanHost: Pointer to foreman.Host containing comprehensive source data from Foreman API
//
// Returns:
//   - mcmp.Host: Fully transformed, validated, and enriched host data ready for MCMP consumption
//
// Example transformation:
//
//	foremanHost := &foreman.Host{
//	    ID: 123,
//	    Name: "db-server-prod.example.com",
//	    IP: "192.168.1.100",
//	    // ... other fields
//	}
//	mcmpHost := processor.transformForemanHostToMCMP(foremanHost)
//	// Result: mcmpHost contains all transformed data with proper type mappings
//
// The method is null-safe and returns an empty mcmp.Host structure if the input is nil.
func (sp *ServiceProcessor) transformForemanHostToMCMP(foremanHost *foreman.Host, foremanHostname string) mcmp.Host {
	if foremanHost == nil {
		return mcmp.Host{}
	}

	// Transform interfaces if they exist
	var interfaces []mcmp.Interface
	if foremanHost.Interfaces != nil {
		for _, networkInterface := range foremanHost.Interfaces {
			mcmpInterface := mcmp.Interface{
				CreatedAt:  networkInterface.CreatedAt,
				DomainName: networkInterface.DomainName,
				Execution:  networkInterface.Execution,
				FQDN:       networkInterface.FQDN,
				ID:         networkInterface.ID,
				Identifier: networkInterface.Identifier,
				IP:         networkInterface.IP,
				IP6:        networkInterface.IP6,
				Mac:        networkInterface.Mac,
				MTU:        networkInterface.MTU,
				Managed:    networkInterface.Managed,
				Name:       networkInterface.Name,
				Primary:    networkInterface.Primary,
				Provision:  networkInterface.Provision,
				SubnetName: networkInterface.SubnetName,
				Type:       networkInterface.Type,
				UpdatedAt:  networkInterface.UpdatedAt,
				Virtual:    networkInterface.Virtual,
			}
			interfaces = append(interfaces, mcmpInterface)
		}
	}

	// Create comprehensive MCMP host structure with all mapped data
	var hostNamePtr *string
	if foremanHostname != "" {
		hostNamePtr = &foremanHostname
	}
	mcmpHost := mcmp.Host{
		ID:                        foremanHost.ID,
		Name:                      foremanHost.Name,
		Fqdn:                      sp.extractStringValueFromFacts(foremanHost.Facts, "fqdn"),
		DisplayName:               &foremanHost.DisplayName,
		IP:                        foremanHost.IP,
		Mac:                       &foremanHost.Mac,
		ArchitectureName:          &foremanHost.ArchitectureName,
		OperatingsystemName:       &foremanHost.OperatingSystemName,
		OperatingsystemFamily:     &foremanHost.OperatingSystemFamily,
		OperatingsystemMajor:      &foremanHost.OperatingSystemMajor,
		SubnetName:                &foremanHost.SubnetName,
		CreatedAt:                 &foremanHost.CreatedAt,
		InitiatedAt:               foremanHost.InitiatedAt,
		InstalledAt:               foremanHost.InstalledAt,
		InstanceUUID:              &foremanHost.UUID,
		Serialnumber:              sp.extractStringValueFromFacts(foremanHost.Facts, "serialnumber"),
		ComputeResourceName:       &foremanHost.ComputeResourceName,
		Interfaces:                interfaces,
		LhmPnExitcode:             sp.extractStringValueFromFacts(foremanHost.Facts, "lhm_pn_exitcode"),
		LhmPnExitstring:           sp.extractStringValueFromFacts(foremanHost.Facts, "lhm_pn_exitstring"),
		TetrationAgentIsInstalled: sp.extractBooleanValueFromFacts(foremanHost.Facts, "tetration_agent_is_installed"),
		OracleDBVersion:           sp.extractStringValueFromFacts(foremanHost.Facts, "lhm_ora::DB_VERSION"),
		OracleSID:                 sp.extractStringValueFromFacts(foremanHost.Facts, "lhm_ora::ORACLE_SID"),
		ServerInfosOwnerMail:      sp.extractStringValueFromFacts(foremanHost.Facts, "server_infos_owner_mail"),
		ServerInfosTicketnr:       sp.extractStringValueFromFacts(foremanHost.Facts, "server_infos_ticketnr"),
		Source:                    hostNamePtr,
	}

	// Apply advanced processing for database and OS type determination
	sp.determineDatabaseAndOSType(&mcmpHost,
		sp.extractBooleanValueFromFacts(foremanHost.Facts, "lhm_managed_postgresql"),
		sp.extractBooleanValueFromFacts(foremanHost.Facts, "lhm_managed_mariadb"),
		sp.extractBooleanValueFromFacts(foremanHost.Facts, "lhm_managed_mysql"),
		sp.extractBooleanValueFromFacts(foremanHost.Facts, "lhm_managed_mongodb"),
	)

	// Process patchnight scheduling information
	sp.processPatchnightInformation(&mcmpHost, foremanHost)

	// Extract database version information based on detected database types
	if mcmpHost.MysqlDB {
		mcmpHost.MysqlDBVersion = sp.extractStringValueFromFacts(foremanHost.Facts, "mysql_version")
	}
	if mcmpHost.MariaDB {
		mcmpHost.MariaDBVersion = sp.extractStringValueFromFacts(foremanHost.Facts, "mysql_version")
	}

	// Extract filesystem mountpoint information
	mcmpHost.Mountpoints = sp.extractMountpointsFromFacts(foremanHost.Facts)
	mcmpHost.Partitions = sp.extractPartitionsFromFacts(foremanHost.Facts)
	mcmpHost.LogicalVolumes = sp.extractLogicalVolumesFromFacts(foremanHost.Facts)

	return mcmpHost
}

// GetForemanData provides access to the processed Foreman data structure.
// This method returns a pointer to the internal data structure containing
// all transformed host information ready for export or inspection.
//
// Use Cases:
// - Data inspection and validation after processing
// - Custom export implementations
// - Integration with other systems
// - Testing and debugging
//
// Returns:
//   - *mcmp.ForemanData: Pointer to the processed and transformed Foreman data
//
// Example usage:
//
//	data := processor.GetForemanData()
//	fmt.Printf("Processed %d hosts", len(data.Hosts))
//	for _, host := range data.Hosts {
//	    fmt.Printf("Host: %s, IP: %s", host.Name, host.IP)
//	}
func (sp *ServiceProcessor) GetForemanData() *mcmp.ForemanData {
	return sp.foremanData
}

// ExportForemanDataAsJSON exports all processed data as a formatted JSON string.
// The generated JSON is properly indented for human readability and can be used
// for API integration, file exports, or debugging purposes.
//
// JSON Formatting:
// - Pretty-printed with proper indentation for readability
// - Standard JSON format compatible with all JSON parsers
// - Includes all processed host data with complete field mapping
// - Proper handling of nested structures (interfaces, mountpoints, etc.)
//
// Returns:
//   - string: Complete JSON representation of all processed data
//   - error: nil on success, or detailed error if JSON serialization fails
//
// Example usage:
//
//	jsonString, err := processor.ExportForemanDataAsJSON(ctx)
//	if err != nil {
//	    log.Printf("Failed to export JSON: %v", err)
//	    return
//	}
//	fmt.Println("Exported JSON:", jsonString)
//
//	// Use for API calls
//	response, err := http.Post(apiURL, "application/json", strings.NewReader(jsonString))
func (sp *ServiceProcessor) ExportForemanDataAsJSON(ctx context.Context) (string, error) {
	if sp.foremanData == nil {
		err := sp.ProcessForemanHosts(ctx)
		if err != nil {
			return "", fmt.Errorf("error during processing: %w", err)
		}
	}
	jsonData, err := json.MarshalIndent(sp.foremanData, "", "  ")
	if err != nil {
		return "", fmt.Errorf("error during JSON marshal: %w", err)
	}
	return string(jsonData), nil
}

// ExportForemanDataToFile exports all processed data to a JSON file on the filesystem.
// This method is designed for data persistence, backup operations, or integration
// with external systems that consume JSON files.
//
// File Operations:
// - Creates or overwrites the specified file
// - Sets appropriate file permissions (owner read/write, group/others read-only)
// - Handles all filesystem errors gracefully
// - Provides comprehensive error reporting
//
// Parameters:
//   - filename: Complete path and filename for the output file (e.g., "/tmp/foreman-data.json")
//
// Returns:
//   - error: nil on successful file creation, detailed error on any failure
//
// Example usage:
//
//	err := processor.ExportForemanDataToFile(ctx, "/tmp/foreman-export.json")
//	if err != nil {
//	    log.Printf("Failed to export to file: %v", err)
//	    return
//	}
//	log.Println("Data successfully exported to file")
//
//	// For backup operations
//	timestamp := time.Now().Format("20060102-150405")
//	backupFile := fmt.Sprintf("/backups/foreman-data-%s.json", timestamp)
//	err = processor.ExportForemanDataToFile(ctx, backupFile)
func (sp *ServiceProcessor) ExportForemanDataToFile(ctx context.Context, filename string) error {
	// Generate JSON representation of the data
	jsonString, err := sp.ExportForemanDataAsJSON(ctx)
	if err != nil {
		return fmt.Errorf("error creating JSON: %w", err)
	}

	// Write JSON data to file with appropriate permissions (owner read/write, group/others read)
	err = os.WriteFile(filename, []byte(jsonString), 0644)
	if err != nil {
		return fmt.Errorf("error writing file %s: %w", filename, err)
	}

	sp.debugPrintf("Foreman data successfully exported to file %s", filename)
	return nil
}

// debugPrintf prints debug messages if debug mode is enabled
func (sp *ServiceProcessor) debugPrintf(format string, args ...interface{}) {
	if sp.debug {
		sp.DebugPrintf(format+"\n", args...)
	}
}

// determineDatabaseAndOSType analyzes the hostname and various Foreman facts
// to determine the database type and operating system for a host.
//
// The method uses a multi-stage analysis approach:
//
//  1. Hostname-based analysis (naming schema):
//     Pattern: .*[da|dm|db|dp|dy|ds|wi][c|d|k|p|s|tl][0-9][0-9][0-9]
//     - da = MariaDB
//     - dm = MongoDB
//     - db = Oracle DB
//     - dp = PostgreSQL DB
//     - dy = MySQL DB
//     - ds = MSSQL DB (automatically Windows)
//     - wi = Windows (no specific database type)
//
// 2. Operating system determination:
//
//   - wi* or ds* = Windows Server
//
//   - all others = Linux Server
//
//   - Suse family = additional AdabasDB
//
//     3. Foreman Facts override:
//     The following facts can override the hostname-based analysis:
//
//   - lhm_managed_postgresql → PostgresDB = true
//
//   - lhm_managed_mariadb → MariaDB = true
//
//   - lhm_managed_mysql → MysqlDB = true
//
//   - lhm_managed_mongodb → MongoDB = true
//
// Parameters:
//   - host: Pointer to mcmp.Host struct that will be updated
//   - factLhmManagedPostgresql: Foreman fact for PostgreSQL management
//   - factLhmManagedMariaDB: Foreman fact for MariaDB management
//   - factLhmManagedMySQLDB: Foreman fact for MySQL management
//   - factLhmManagedMongoDB: Foreman fact for MongoDB management
//
// The function sets the corresponding boolean fields in the Host struct.
// For unknown schemas, Linux = true is set as default.
func (sp *ServiceProcessor) determineDatabaseAndOSType(host *mcmp.Host, factLhmManagedPostgresql bool, factLhmManagedMariaDB bool, factLhmManagedMySQLDB bool, factLhmManagedMongoDB bool) {
	if host == nil || host.Name == "" {
		return
	}

	// Extract hostname from Name (everything before the first dot)
	hostname := host.Name
	if dotIndex := strings.Index(host.Name, "."); dotIndex != -1 {
		hostname = host.Name[:dotIndex]
	}

	sp.debugPrintf("Analyzing hostname: %s", hostname)

	if host.OperatingsystemFamily != nil && strings.EqualFold(*host.OperatingsystemFamily, "Suse") {
		host.AdabasDB = true
		sp.debugPrintf("Operatingsystem Family == *Suse* -> Set AdabasDB=true for host %s", hostname)
	}
	if factLhmManagedPostgresql {
		host.PostgresDB = true
		sp.debugPrintf("Fact lhm_managed_postgresql == true -> Set PostgresDB=true for host %s", hostname)
	}
	if factLhmManagedMariaDB {
		host.MariaDB = true
		sp.debugPrintf("Fact lhm_managed_mariadb == true -> Set MariaDB=true for host %s", hostname)
	}
	if factLhmManagedMySQLDB {
		host.MysqlDB = true
		sp.debugPrintf("Fact lhm_managed_mysql == true -> Set MysqlDB=true for host %s", hostname)
	}
	if factLhmManagedMongoDB {
		host.MongoDB = true
		sp.debugPrintf("Fact lhm_managed_mongodb == true -> Set MongoDB=true for host %s", hostname)
	}

	// Search for naming schema pattern from the end
	// Pattern: [da|dm|db|dp|dy|ds|wi][c|d|k|p|s|tl][0-9][0-9][0-9]
	if !sp.matchesNamingSchema(hostname) {
		sp.debugPrintf("Hostname %s does not match naming schema", hostname)
		// Default:  Linux for unknown schemas
		host.Linux = true
		return
	}

	// Extract the schema components
	dbPrefix, envSuffix := sp.extractSchemaComponents(hostname)

	if dbPrefix == "" {
		sp.debugPrintf("No valid database prefix found for hostname %s", hostname)
		// Default: Linux for unknown schemas
		host.Linux = true
		return
	}

	sp.debugPrintf("Detected schema for host %s: db_prefix=%s, env=%s", hostname, dbPrefix, envSuffix)

	// Set operating system based on prefix
	switch dbPrefix {
	case "wi":
		// Windows Server (no specific database type)
		host.Windows = true
		sp.debugPrintf("Set Windows=true for host %s", hostname)
	case "ds":
		// MSSQL Server is always Windows
		host.Windows = true
		host.MssqlDB = true
		sp.debugPrintf("Set Windows=true and MssqlDB=true for host %s", hostname)
	default:
		// All other prefixes are Linux Server
		host.Linux = true
		sp.debugPrintf("Set Linux=true for host %s", hostname)

		// Set the corresponding database fields
		switch dbPrefix {
		case "da":
			host.MariaDB = true
			sp.debugPrintf("Set MariaDB=true for host %s", hostname)
		case "la":
			host.MariaDB = true
			sp.debugPrintf("Set MariaDB=true for host %s", hostname)
		case "db":
			host.OracleDB = true
			sp.debugPrintf("Set OracleDB=true for host %s", hostname)
		case "dm":
			host.MongoDB = true
			sp.debugPrintf("Set MongoDB=true for host %s", hostname)
		case "lm":
			host.MongoDB = true
			sp.debugPrintf("Set MongoDB=true for host %s", hostname)
		case "dp":
			host.PostgresDB = true
			sp.debugPrintf("Set PostgresDB=true for host %s", hostname)
		case "lp":
			host.PostgresDB = true
			sp.debugPrintf("Set PostgresDB=true for host %s", hostname)
		case "dy":
			host.MysqlDB = true
			sp.debugPrintf("Set MysqlDB=true for host %s", hostname)
		case "ly":
			host.MysqlDB = true
			sp.debugPrintf("Set MysqlDB=true for host %s", hostname)
		default:
			sp.debugPrintf("Unknown database prefix '%s' for host %s", dbPrefix, hostname)
		}

	}
}

// matchesNamingSchema checks if the hostname matches the defined naming schema
// Pattern: .*[da|dm|db|dp|dy|ds|wi][c|d|k|p|s|tl][0-9][0-9][0-9]
func (sp *ServiceProcessor) matchesNamingSchema(hostname string) bool {
	if len(hostname) < 6 { // Minimum length for the schema
		return false
	}

	// Check the last 3 characters for numbers
	suffix := hostname[len(hostname)-3:]
	for _, char := range suffix {
		if char < '0' || char > '9' {
			return false
		}
	}

	// Check environment suffix (before the 3 numbers)
	if len(hostname) < 4 {
		return false
	}

	// For "tl" environment (2-digit)
	if len(hostname) >= 5 {
		envSuffix := hostname[len(hostname)-5 : len(hostname)-3]
		if envSuffix == "tl" {
			return sp.isValidPrefix(hostname[len(hostname)-7 : len(hostname)-5])
		}
	}

	// For single-digit environment suffixes
	envChar := string(hostname[len(hostname)-4])
	validEnvSuffixes := []string{"c", "d", "k", "p", "s", "t"}
	for _, validSuffix := range validEnvSuffixes {
		if envChar == validSuffix {
			return sp.isValidPrefix(hostname[len(hostname)-6 : len(hostname)-4])
		}
	}

	return false
}

// extractSchemaComponents extracts database prefix and environment suffix from the hostname
func (sp *ServiceProcessor) extractSchemaComponents(hostname string) (string, string) {
	if len(hostname) < 6 {
		return "", ""
	}

	// Check first for "tl" (2-digit environment)
	if len(hostname) >= 7 {
		potentialTL := hostname[len(hostname)-5 : len(hostname)-3]
		if potentialTL == "tl" {
			dbPrefix := hostname[len(hostname)-7 : len(hostname)-5]
			if sp.isValidPrefix(dbPrefix) {
				return dbPrefix, "tl"
			}
		}
	}

	// Check single-digit environment suffixes
	if len(hostname) >= 6 {
		envChar := string(hostname[len(hostname)-4])
		validEnvSuffixes := []string{"c", "d", "k", "p", "s", "t"}
		for _, validSuffix := range validEnvSuffixes {
			if envChar == validSuffix {
				dbPrefix := hostname[len(hostname)-6 : len(hostname)-4]
				if sp.isValidPrefix(dbPrefix) {
					return dbPrefix, envChar
				}
			}
		}
	}

	return "", ""
}

// isValidPrefix checks if the given prefix is a valid database/system prefix
func (sp *ServiceProcessor) isValidPrefix(prefix string) bool {
	validPrefixes := []string{"da", "dm", "db", "dp", "dy", "ds", "wi", "la", "lm", "lp", "ly"}
	for _, validPrefix := range validPrefixes {
		if prefix == validPrefix {
			return true
		}
	}
	return false
}

// extractStringValueFromFacts extracts a string value from the Facts map by key
// Returns a pointer to the string if found and valid, nil otherwise
func (sp *ServiceProcessor) extractStringValueFromFacts(facts map[string]interface{}, key string) *string {
	if facts == nil {
		return nil
	}

	if value, exists := facts[key]; exists {
		if str, ok := value.(string); ok {
			return &str
		}
	}

	return nil
}

// extractBooleanValueFromFacts extracts a boolean value from Foreman host facts.
// This utility method provides safe access to boolean fact values with comprehensive
// type checking and conversion from various common representations.
//
// Boolean Conversion Logic:
// - Direct boolean values: returned as-is
// - String values: "true", "1", "yes", "on" (case-insensitive) -> true
// - String values: "false", "0", "no", "off" (case-insensitive) -> false
// - Numeric values: 0 -> false, non-zero -> true
// - Other types: false (with debug logging)
//
// Safety Features:
// - Null pointer protection for facts map
// - Type assertion with comprehensive fallback conversion
// - Graceful handling of unexpected data types
// - Detailed logging for debugging conversion issues
//
// Parameters:
//   - facts: Map of fact names to fact values from Foreman host
//   - key: The fact name to extract
//
// Returns:
//   - bool: Extracted boolean value, false if fact not found or invalid
//
// Example usage:
//
//	facts := map[string]interface{}{
//	    "is_virtual": true,
//	    "enabled": "yes",
//	    "count": 1,
//	    "disabled": "false",
//	}
//	isVirtual := sp.extractBooleanValueFromFacts(facts, "is_virtual") // Returns true
//	enabled := sp.extractBooleanValueFromFacts(facts, "enabled")     // Returns true
//	hasCount := sp.extractBooleanValueFromFacts(facts, "count")      // Returns true
//	missing := sp.extractBooleanValueFromFacts(facts, "missing")     // Returns false

func (sp *ServiceProcessor) extractBooleanValueFromFacts(facts map[string]interface{}, key string) bool {
	if facts == nil {
		return false
	}

	value, exists := facts[key]
	if !exists {
		return false
	}

	// Handle nil values
	if value == nil {
		return false
	}

	switch v := value.(type) {
	case bool:
		return v
	case string:
		val := strings.ToLower(v)
		return val == "true" || val == "1" || val == "yes" || val == "on"
	case int:
		return v != 0
	case int64:
		return v != 0
	case float64:
		return v != 0.0
	case float32:
		return v != 0.0
	default:
		sp.debugPrintf("Unable to convert fact '%s' of type %T to boolean, defaulting to false", key, v)
		return false
	}
}

// processPatchnightInformation processes the patchnight information for a host
// and sets the corresponding fields in the MCMP host structure.
//
// This function should be called in the transformForemanHostToMCMP method.
//
// Parameters:
//   - mcmpHost: Pointer to mcmp.Host structure that will be updated
//   - foremanHost: Pointer to foreman.Host structure with the source data
func (sp *ServiceProcessor) processPatchnightInformation(mcmpHost *mcmp.Host, foremanHost *foreman.Host) {
	patchnightInfo, err := sp.extractPatchnightInfoFromHostCollections(foremanHost.HostCollections)
	if err != nil {
		sp.debugPrintf("Error extracting patchnight info for host %s: %v", foremanHost.Name, err)
		return
	}

	if patchnightInfo != nil {
		mcmpHost.PatchnightGroup = &patchnightInfo.Group
		mcmpHost.PatchnightStartTime = &patchnightInfo.StartTime
		sp.debugPrintf("Set patchnight info for host %s: Group=%s, StartTime=%s",
			foremanHost.Name, patchnightInfo.Group, patchnightInfo.StartTime)
	} else {
		sp.debugPrintf("No patchnight info found for host %s", foremanHost.Name)
	}
}

// extractPatchnightInfoFromHostCollections analyzes the host collections of a host
// and extracts patchnight group and start time from the name.
//
// The function searches for host collections with names in the format "Patchnight_<group>_<time>",
// where:
// - <group> is the group identifier (e.g., "p4")
// - <time> is the four-digit start time (e.g., "0200")
//
// Parameters:
//   - hostCollections: Array of foreman.HostCollection structures
//
// Returns:
//   - *PatchnightInfo: Extracted information or nil if none found
//   - error: Error during processing
func (sp *ServiceProcessor) extractPatchnightInfoFromHostCollections(hostCollections []foreman.HostCollection) (*PatchnightInfo, error) {
	if len(hostCollections) == 0 {
		sp.debugPrintf("No host collections found")
		return nil, nil
	}

	for _, collection := range hostCollections {
		sp.debugPrintf("Analyzing host collection: %s", collection.Name)

		// Check if the name matches the Patchnight pattern
		matches := patchnightPattern.FindStringSubmatch(collection.Name)
		if len(matches) == 3 {
			group := matches[1]
			timeStr := matches[2]

			// Convert time from "0200" to "02:00"
			formattedTime, err := sp.formatPatchnightTime(timeStr)
			if err != nil {
				sp.debugPrintf("Failed to format time %s: %v", timeStr, err)
				continue
			}

			patchnightInfo := &PatchnightInfo{
				Group:     group,
				StartTime: formattedTime,
			}

			sp.debugPrintf("Found Patchnight info - Group: %s, StartTime: %s",
				patchnightInfo.Group, patchnightInfo.StartTime)

			return patchnightInfo, nil
		}
	}

	sp.debugPrintf("No Patchnight collection found in %d host collections", len(hostCollections))
	return nil, nil
}

// formatPatchnightTime converts a 4-digit time specification (e.g., "0200")
// to the format "HH:MM" (e.g., "02:00")
//
// Parameters:
//   - timeStr: 4-digit time specification (e.g., "0200", "1530")
//
// Returns:
//   - string: Formatted time in "HH:MM" format
//   - error: Error if the format is invalid
func (sp *ServiceProcessor) formatPatchnightTime(timeStr string) (string, error) {
	if len(timeStr) != 4 {
		return "", fmt.Errorf("invalid time format: expected 4 digits, got %d", len(timeStr))
	}

	// Validate that all characters are digits
	for _, char := range timeStr {
		if char < '0' || char > '9' {
			return "", fmt.Errorf("invalid time format: non-digit character found in %s", timeStr)
		}
	}

	// Extract hours and minutes
	hours := timeStr[:2]
	minutes := timeStr[2:]

	// Validate hours (00-23)
	if hours > "23" {
		return "", fmt.Errorf("invalid hours: %s (must be 00-23)", hours)
	}

	// Validate minutes (00-59)
	if minutes > "59" {
		return "", fmt.Errorf("invalid minutes: %s (must be 00-59)", minutes)
	}

	formattedTime := fmt.Sprintf("%s:%s", hours, minutes)
	sp.debugPrintf("Formatted time %s to %s", timeStr, formattedTime)

	return formattedTime, nil
}

// extractMountpointsFromFacts extracts all mountpoints with filesystem "nfs" or "cifs" from the Foreman Facts
// and returns them as a slice of mcmp.Mountpoint.
//
// Parameters:
//   - facts: Map with Foreman Facts containing mountpoint information
//
// Returns:
//   - []mcmp.Mountpoint: Slice of extracted mountpoints with nfs or cifs filesystem
func (sp *ServiceProcessor) extractMountpointsFromFacts(facts map[string]interface{}) []mcmp.Mountpoint {
	return extractByPattern[mcmp.Mountpoint](
		sp,
		facts,
		mountpointPattern,
		func(name string, data map[string]interface{}) *mcmp.Mountpoint {
			return sp.createMountpointFromData(name, data)
		},
		func(mp *mcmp.Mountpoint) string {
			return fmt.Sprintf("Extracted mountpoint: %s (filesystem: %s)", mp.MountPoint, mp.Filesystem)
		},
	)
}

// extractPartitionsFromFacts extracts and returns a slice of Partition objects from the provided facts map.
func (sp *ServiceProcessor) extractPartitionsFromFacts(facts map[string]interface{}) []mcmp.Partition {
	return extractByPattern[mcmp.Partition](
		sp,
		facts,
		partitionsPattern,
		func(name string, data map[string]interface{}) *mcmp.Partition {
			return sp.createPartitionsFromData(name, data)
		},
		func(p *mcmp.Partition) string {
			return fmt.Sprintf("Extracted partition: %s ", p.Partition)
		},
	)
}

// extractLogicalVolumesFromFacts extracts and returns a slice of LogicalVolume objects from the provided facts map.
func (sp *ServiceProcessor) extractLogicalVolumesFromFacts(facts map[string]interface{}) []mcmp.LogicalVolume {
	return extractByPattern[mcmp.LogicalVolume](
		sp,
		facts,
		logicalVolumesPattern,
		func(name string, data map[string]interface{}) *mcmp.LogicalVolume {
			return sp.createLogicalVolumeFromData(name, data)
		},
		func(lv *mcmp.LogicalVolume) string {
			return fmt.Sprintf("Extracted logical volume: %s ", lv.LogicalVolume)
		},
	)
}

// createMountpointFromData creates a mountpoint from the collected data
func (sp *ServiceProcessor) createMountpointFromData(mountPath string, data map[string]interface{}) *mcmp.Mountpoint {
	// Validate filesystem
	filesystem := sp.getStringFromData(data, "filesystem")
	if filesystem == "" || (filesystem != "nfs" && filesystem != "cifs") {
		return nil
	}

	mountpoint := &mcmp.Mountpoint{
		MountPoint:     mountPath,
		Filesystem:     filesystem,
		Device:         sp.getStringFromData(data, "device"),
		Options:        sp.parseOptionsFromData(data, mountPath),
		SizeBytes:      sp.parseInt64FromData(data, "size_bytes", mountPath),
		UsedBytes:      sp.parseInt64FromData(data, "used_bytes", mountPath),
		AvailableBytes: sp.parseInt64FromData(data, "available_bytes", mountPath),
	}
	return mountpoint
}

// extractPartitionsFromFacts creates a partition from the collected data
func (sp *ServiceProcessor) createPartitionsFromData(partitionPath string, data map[string]interface{}) *mcmp.Partition {
	partition := &mcmp.Partition{
		Partition:  partitionPath,
		MountPoint: sp.getStringFromData(data, "mount"),
		Filesystem: sp.getStringFromData(data, "filesystem"),
		PartType:   sp.getStringFromData(data, "parttype"),
		PartUUID:   sp.getStringFromData(data, "partuuid"),
		SizeBytes:  sp.parseInt64FromData(data, "size_bytes", partitionPath),
		UUID:       sp.getStringFromData(data, "uuid"),
	}
	return partition
}

// extractLogicalVolumeFromFacts creates a logical volume from the collected data
func (sp *ServiceProcessor) createLogicalVolumeFromData(logicalVolumePath string, data map[string]interface{}) *mcmp.LogicalVolume {
	logicalVolume := &mcmp.LogicalVolume{
		LogicalVolume: logicalVolumePath,
		Active:        sp.getStringFromData(data, "active"),
		Attr:          sp.getStringFromData(data, "attr"),
		DmPath:        sp.getStringFromData(data, "dm_path"),
		FullName:      sp.getStringFromData(data, "full_name"),
		Layout:        sp.getStringFromData(data, "layout"),
		Path:          sp.getStringFromData(data, "path"),
		Permissions:   sp.getStringFromData(data, "permissions"),
		Role:          sp.getStringFromData(data, "role"),
		Size:          sp.getStringFromData(data, "size"),
		UUID:          sp.getStringFromData(data, "uuid"),
	}
	return logicalVolume
}

// getStringFromData extracts a string value from the data
func (sp *ServiceProcessor) getStringFromData(data map[string]interface{}, key string) string {
	if value, ok := data[key]; ok {
		if str, ok := value.(string); ok {
			return str
		}
	}
	return ""
}

// parseOptionsFromData parses options from JSON string
func (sp *ServiceProcessor) parseOptionsFromData(data map[string]interface{}, mountPath string) []string {
	optionsStr := sp.getStringFromData(data, "options")
	if optionsStr == "" {
		return nil
	}

	var options []string
	if err := json.Unmarshal([]byte(optionsStr), &options); err != nil {
		sp.debugPrintf("Warning: Failed to parse options for mountpoint %s: %v", mountPath, err)
		return nil
	}
	return options
}

// parseInt64FromData parses an Int64 value from string
func (sp *ServiceProcessor) parseInt64FromData(data map[string]interface{}, key, mountPath string) int64 {
	valueStr := sp.getStringFromData(data, key)
	if valueStr == "" {
		return 0
	}

	value, err := strconv.ParseInt(valueStr, 10, 64)
	if err != nil {
		sp.debugPrintf("Warning: Failed to parse %s for mountpoint %s: %v", key, mountPath, err)
		return 0
	}
	return value
}

// groupFactsByPattern groups Foreman facts by a regex pattern of the form "prefix::<name>::<field>"
func (sp *ServiceProcessor) groupFactsByPattern(facts map[string]interface{}, pattern *regexp.Regexp) map[string]map[string]interface{} {
	grouped := make(map[string]map[string]interface{})
	for key, value := range facts {
		if matches := pattern.FindStringSubmatch(key); len(matches) == 3 {
			name := matches[1]
			field := matches[2]

			if grouped[name] == nil {
				grouped[name] = make(map[string]interface{})
			}
			grouped[name][field] = value
		}
	}
	return grouped
}

// extractByPattern is a generic helper that groups facts by regex and builds typed items using a builder function.
// It also supports optional per-item debug logging via the provided log formatter.
func extractByPattern[T any](
	sp *ServiceProcessor,
	facts map[string]interface{},
	pattern *regexp.Regexp,
	build func(name string, data map[string]interface{}) *T,
	logFormatter func(*T) string,
) []T {
	if facts == nil {
		return nil
	}

	grouped := sp.groupFactsByPattern(facts, pattern)
	result := make([]T, 0, len(grouped))

	for name, data := range grouped {
		if item := build(name, data); item != nil {
			result = append(result, *item)
			if sp.debug && logFormatter != nil {
				sp.debugPrintf("%s", logFormatter(item))
			}
		}
	}

	return result
}
