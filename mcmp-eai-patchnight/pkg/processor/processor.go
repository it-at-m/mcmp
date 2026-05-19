package processor

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-patchnight/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-patchnight/pkg/clients/patchnight"
)

const (
	DefaultAPITimeout = 30 * time.Second // Reduced from 2 minutes
	MaxAPITimeout     = 5 * time.Minute  // Configurable upper limit
)

var (
	berlinOnce sync.Once
	berlinLoc  *time.Location
)

// PatchnightClientInterface defines the contract for patchnight API operations
// This interface allows for dependency injection and easier testing by providing
// a mockable abstraction over the actual patchnight client implementation
type PatchnightClientInterface interface {
	FetchLinuxPatchnightDates(context.Context) ([]patchnight.PatchnightDate, error)
	FetchLinuxIncludedServers(context.Context) ([]patchnight.PatchnightLinuxIncludedServer, error)
	FetchLinuxExcludedServers(context.Context) ([]patchnight.PatchnightLinuxExcludedServer, error)

	FetchWindowsPatchnightDates(context.Context) ([]patchnight.PatchnightDate, error)
	FetchWindowsKIncludedServers(ctx context.Context) ([]string, error)
	FetchWindowsPIncludedServers(ctx context.Context) ([]string, error)
	FetchWindowsExcludedServers(ctx context.Context) ([]string, error)
	FetchWindowsUpdateStatus(ctx context.Context) ([]patchnight.WindowsPatchnightStatus, error)

	// EnableDebug activates debug logging for the patchnight client
	// This provides verbose output for troubleshooting API communication issues
	EnableDebug()
}

// ServiceProcessor handles the core business logic for processing patchnight data
// It coordinates between the patchnight API client and MCMP data transformation
// The processor is responsible for:
// - Fetching patchnight schedules and server lists
// - Calculating timing windows for server maintenance
// - Transforming data into MCMP-compatible format
// - Providing debug logging capabilities
type ServiceProcessor struct {
	// patchnightClient provides access to Patchnight API operations
	// This dependency is injected to allow for testing and modularity
	patchnightClient PatchnightClientInterface

	// debug controls whether debug logging is enabled throughout the processor
	// When true, detailed operational information is logged for troubleshooting
	debug bool

	// currentTime allows injection of time for testing purposes
	// In production, this returns time.Now(), but can be overridden for deterministic testing
	currentTime func() time.Time

	patchnightData *mcmp.PatchnightData
}

// NewServiceProcessor creates a new instance of ServiceProcessor with the provided dependencies
// This constructor function initializes the processor with default time handling
//
// Parameters:
//   - patchnightClient: Implementation of PatchnightClientInterface for API operations
//   - debug: Boolean flag to enable debug logging throughout the processor
//
// Returns:
//   - *ServiceProcessor: Configured processor instance ready for use
func NewServiceProcessor(patchnightClient PatchnightClientInterface, debug bool) *ServiceProcessor {
	return &ServiceProcessor{
		patchnightClient: patchnightClient,
		debug:            debug,
		currentTime:      func() time.Time { return time.Now() },
	}
}

// SetCurrentTime allows setting a custom time function for testing scenarios
// This method enables deterministic testing by allowing injection of fixed time values
// In production environments, this method typically isn't called, leaving the default time.Now()
//
// Parameters:
//   - timeFunc: Function that returns the current time for processor operations
func (sp *ServiceProcessor) SetCurrentTime(timeFunc func() time.Time) {
	sp.currentTime = timeFunc
}

// ProcessPatchnightData processes patchnight data and transforms it into MCMP format
// This is the main orchestration method that coordinates all patchnight data processing:
// 1. Retrieves patchnight schedules and server information from the API
// 2. Determines the next patchnight dates for different environments
// 3. Calculates maintenance windows for each server
// 4. Transforms data into MCMP-compatible structure
// 5. Sorts and organizes the final output
//
// The method handles both included servers (with maintenance schedules) and excluded servers
// Error handling ensures that partial failures don't prevent processing of other servers
//
// Returns:
//   - error: Any error that prevents successful data processing
func (sp *ServiceProcessor) ProcessPatchnightData() error {
	// Debug logging for process initiation
	sp.logDebugf("Starting patchnight data processing...")

	// Context with timeout for API calls
	ctx, cancel := context.WithTimeout(context.Background(), DefaultAPITimeout)
	defer cancel()

	linuxServers, linuxIncluded, linuxExcluded, err := sp.processLinuxPatchnightData(ctx)
	if err != nil {
		return fmt.Errorf("failed to process linux patchnight data: %w", err)
	}

	windowsServers, windowsIncluded, windowsExcluded, err := sp.processWindowsPatchnightData(ctx)
	if err != nil {
		return fmt.Errorf("failed to process windows patchnight data: %w", err)
	}

	totalServers := make([]mcmp.Server, 0, len(linuxServers)+len(windowsServers))
	totalServers = append(totalServers, linuxServers...)
	totalServers = append(totalServers, windowsServers...)

	// Sort servers alphabetically by name for consistent output
	sort.Slice(totalServers, func(i, j int) bool {
		return totalServers[i].Name < totalServers[j].Name
	})

	sp.logDebugf(
		"Processed %d servers total (linux: %d included, %d excluded; windows: %d included, %d excluded)",
		len(totalServers),
		linuxIncluded, linuxExcluded,
		windowsIncluded, windowsExcluded,
	)

	sp.patchnightData = &mcmp.PatchnightData{Servers: totalServers}
	return nil
}

// processLinuxPatchnightData processes Linux patchnight schedules, retrieves servers, and calculates maintenance windows.
// Returns the list of servers with calculated data, and counts of included and excluded servers, or an error in case of failure.
func (sp *ServiceProcessor) processLinuxPatchnightData(ctx context.Context) ([]mcmp.Server, int, int, error) {
	// Retrieve all scheduled patchnight dates from the API
	// These dates define when maintenance windows are scheduled for different environments
	patchnightDates, err := sp.patchnightClient.FetchLinuxPatchnightDates(ctx)
	if err != nil {
		return nil, 0, 0, fmt.Errorf("failed to get Linux patchnight dates: %w", err)
	}

	// Get current time for next patchnight calculation
	// Uses injected time function to support both production and testing scenarios
	currentTime := sp.currentTime()
	sp.logDebugf("Current time: %v", currentTime)

	// Find the next patchnight schedules for different environments
	// Environment 'k' and 'p' represent different deployment stages (e.g., staging and production)
	nextPatchnightK := sp.findNextPatchnight(patchnightDates, "k", currentTime)
	nextPatchnightP := sp.findNextPatchnight(patchnightDates, "p", currentTime)

	if sp.debug {
		if nextPatchnightK != nil {
			sp.logDebugf("Next Linux patchnight for env k: %v", nextPatchnightK.Date)
		}
		if nextPatchnightP != nil {
			sp.logDebugf("Next Linux patchnight for env p: %v", nextPatchnightP.Date)
		}
	}

	// Create comprehensive server list combining included and excluded servers
	servers := make([]mcmp.Server, 0, 10000)

	// Retrieve servers that are included in patchnight operations
	// These servers will have maintenance windows calculated and applied
	includedServers, err := sp.patchnightClient.FetchLinuxIncludedServers(ctx)
	if err != nil {
		return nil, 0, 0, fmt.Errorf("failed to get included servers: %w", err)
	}

	// Process included servers with full maintenance window calculation
	for _, server := range includedServers {
		mcmpServer := mcmp.Server{
			Name:    server.Name,
			Include: true,
		}

		// Set environment if specified
		// Environment determines which patchnight schedule applies to this server
		if server.Environment != "" {
			mcmpServer.Environment = &server.Environment
		}

		// Calculate maintenance window start and end times based on server environment
		var nextPatchnight *patchnight.PatchnightDate
		if server.Environment == "k" {
			nextPatchnight = nextPatchnightK
		} else if server.Environment == "p" {
			nextPatchnight = nextPatchnightP
		}

		// Calculate specific maintenance window for this server
		if nextPatchnight != nil {
			startDate, endDate, err := sp.calculatePatchnightTimes(nextPatchnight, server.StartTime, server.EndTime)
			if err != nil {
				// Log warning but continue processing other servers
				sp.logDebugf("Warning: Failed to calculate patchnight times for server %s: %v", server.Name, err)
			} else {
				mcmpServer.StartDate = startDate
				mcmpServer.EndDate = endDate
			}
		}

		servers = append(servers, mcmpServer)
	}

	// Retrieve servers that are excluded from patchnight operations
	// These servers are tracked but won't have maintenance windows
	excludedServers, err := sp.patchnightClient.FetchLinuxExcludedServers(ctx)
	if err != nil {
		return nil, len(includedServers), 0, fmt.Errorf("failed to get excluded servers: %w", err)
	}

	// Process excluded servers (no maintenance windows calculated)
	for _, server := range excludedServers {
		mcmpServer := mcmp.Server{
			Name:    server.Name,
			Include: false,
			// Environment, StartDate, EndDate remain nil for excluded servers
		}

		servers = append(servers, mcmpServer)
	}

	return servers, len(includedServers), len(excludedServers), nil
}

func (sp *ServiceProcessor) processWindowsPatchnightData(ctx context.Context) ([]mcmp.Server, int, int, error) {
	sp.logDebugf("Starting Windows patchnight data processing")

	// Retrieve all scheduled patchnight dates from the API
	patchnightDates, err := sp.patchnightClient.FetchWindowsPatchnightDates(ctx)
	if err != nil {
		return nil, 0, 0, fmt.Errorf("failed to get Windows patchnight dates: %w", err)
	}

	currentTime := sp.currentTime()
	sp.logDebugf("Current time (Windows): %v", currentTime)

	// Find next patchnight for both environments
	nextPatchnightK := sp.findNextPatchnight(patchnightDates, "k", currentTime)
	nextPatchnightP := sp.findNextPatchnight(patchnightDates, "p", currentTime)

	if sp.debug {
		if nextPatchnightK != nil {
			sp.logDebugf("Next Windows patchnight for env k: %v", nextPatchnightK.Date)
		} else {
			sp.logDebugf("No upcoming/active Windows patchnight for env k found")
		}
		if nextPatchnightP != nil {
			sp.logDebugf("Next Windows patchnight for env p: %v", nextPatchnightP.Date)
		} else {
			sp.logDebugf("No upcoming/active Windows patchnight for env p found")
		}
	}

	// Fetch Windows update status information
	statuses, err := sp.patchnightClient.FetchWindowsUpdateStatus(ctx)
	if err != nil {
		return nil, 0, 0, fmt.Errorf("failed to get Windows update status: %w", err)
	}

	statusByServer := make(map[string]patchnight.WindowsPatchnightStatus, len(statuses))
	for _, st := range statuses {
		key := strings.ToLower(strings.TrimSpace(st.Server))
		if key == "" {
			continue
		}
		statusByServer[key] = st
	}

	servers := make([]mcmp.Server, 0, 10000)

	// Included servers: env k
	includedServersK, err := sp.patchnightClient.FetchWindowsKIncludedServers(ctx)
	if err != nil {
		return nil, 0, 0, fmt.Errorf("failed to get included Windows K servers: %w", err)
	}
	envK := "k"
	servers = sp.addWindowsIncludedServers(servers, includedServersK, envK, nextPatchnightK, statusByServer)

	// Included servers: env p
	includedServersP, err := sp.patchnightClient.FetchWindowsPIncludedServers(ctx)
	if err != nil {
		return nil, len(includedServersK), 0, fmt.Errorf("failed to get included Windows P servers: %w", err)
	}
	envP := "p"
	servers = sp.addWindowsIncludedServers(servers, includedServersP, envP, nextPatchnightP, statusByServer)

	includedCount := len(includedServersK) + len(includedServersP)

	// Excluded servers
	excludedServers, err := sp.patchnightClient.FetchWindowsExcludedServers(ctx)
	if err != nil {
		return nil, includedCount, 0, fmt.Errorf("failed to get excluded Windows servers: %w", err)
	}

	for _, name := range excludedServers {
		mcmpServer := mcmp.Server{
			Name:    name,
			Include: false,
		}

		// If status information is available, also write it for excluded servers
		key := strings.ToLower(strings.TrimSpace(name))
		if st, ok := statusByServer[key]; ok {
			mcmpServer.Exitcode = new(int8(st.UpdateStatus))

			titles := strings.TrimSpace(string(st.UpdateTitles))
			if titles != "" {
				tmpTitles := titles
				mcmpServer.ExitString = &tmpTitles
			}
		}

		servers = append(servers, mcmpServer)
	}

	excludedCount := len(excludedServers)

	return servers, includedCount, excludedCount, nil
}

func (sp *ServiceProcessor) addWindowsIncludedServers(
	servers []mcmp.Server,
	serverNames []string,
	env string,
	nextPatchnight *patchnight.PatchnightDate,
	statusByServer map[string]patchnight.WindowsPatchnightStatus,
) []mcmp.Server {
	for _, name := range serverNames {
		mcmpServer := mcmp.Server{
			Name:        name,
			Include:     true,
			Environment: &env,
		}

		if nextPatchnight != nil {
			loc := berlinLocation()
			tmpStart := nextPatchnight.StartDate.In(loc)
			mcmpServer.StartDate = &tmpStart
			tmpEnd := nextPatchnight.EndDate.In(loc)
			mcmpServer.EndDate = &tmpEnd
		}

		// Apply Windows update status information if available
		key := strings.ToLower(strings.TrimSpace(name))
		if st, ok := statusByServer[key]; ok {
			mcmpServer.Exitcode = new(int8(st.UpdateStatus))

			titles := strings.TrimSpace(string(st.UpdateTitles))
			if titles != "" {
				tmpTitles := titles
				mcmpServer.ExitString = &tmpTitles
			}
		}

		servers = append(servers, mcmpServer)
	}
	return servers
}

// GetPatchnightData returns the processed patchnight data, processing it first if necessary
// This method provides lazy initialization of the patchnight data - it only processes
// the data if it hasn't been processed yet. This is useful for scenarios where
// the data might be accessed multiple times without needing to reprocess.
//
// Returns:
//   - *mcmp.PatchnightData: The processed patchnight data structure
//   - error: Any error that occurred during processing
func (sp *ServiceProcessor) GetPatchnightData() (*mcmp.PatchnightData, error) {
	if sp.patchnightData == nil {
		err := sp.ProcessPatchnightData()
		if err != nil {
			return nil, err
		}
	}
	return sp.patchnightData, nil
}

// ExportToJSON exports all processed data as a formatted JSON string.
// The JSON is indented for human readability and can be used for API calls or file export.
//
// Returns:
//   - string: JSON representation of all processed data
//   - error: nil on success, or an error if JSON marshaling fails
func (sp *ServiceProcessor) ExportToJSON() (string, error) {
	if sp.patchnightData == nil {
		err := sp.ProcessPatchnightData()
		if err != nil {
			return "", err
		}
	}
	jsonData, err := json.MarshalIndent(sp.patchnightData, "", "  ")
	if err != nil {
		return "", fmt.Errorf("error during JSON marshal: %w", err)
	}
	return string(jsonData), nil
}

// ExportToFile exports all processed data to a JSON file.
// This method is useful for data persistence, backup, or integration with external systems.
//
// Parameters:
//   - filename: Path and name of the file to create/overwrite
//
// Returns:
//   - error: nil on success, or an error if JSON creation or file writing fails
func (sp *ServiceProcessor) ExportToFile(filename string) error {
	jsonString, err := sp.ExportToJSON()
	if err != nil {
		return fmt.Errorf("error creating JSON: %w", err)
	}

	err = os.WriteFile(filename, []byte(jsonString), 0o644)
	if err != nil {
		return fmt.Errorf("error writing file %s: %w", filename, err)
	}

	sp.logDebugf("PatchnightData successfully exported to file %s", filename)
	return nil
}

// findNextPatchnight finds the next patchnight for a given environment
// This method determines the most relevant patchnight schedule for an environment:
// 1. If a patchnight is currently running, it returns that patchnight
// 2. Otherwise, it returns the next scheduled patchnight in the future
//
// The method handles timezone-aware date comparisons and supports multiple
// concurrent patchnight schedules for different environments
//
// Parameters:
//   - patchnightDates: Slice of all available patchnight schedules
//   - env: Environment identifier ("k", "p", etc.)
//   - currentTime: Current timestamp for comparison
//
// Returns:
//   - *patchnight.PatchnightDate: The most relevant patchnight or nil if none found
func (sp *ServiceProcessor) findNextPatchnight(patchnightDates []patchnight.PatchnightDate, env string, currentTime time.Time) *patchnight.PatchnightDate {
	var nextPatchnight *patchnight.PatchnightDate
	var closestTime time.Time

	for i := range patchnightDates {
		pd := patchnightDates[i]
		// Skip patchnights for different environments
		if pd.Environment != env {
			continue
		}

		// Check if patchnightDate is currently active (running now)
		// Active patchnights take precedence over future ones
		if currentTime.After(pd.StartDate) && currentTime.Before(pd.EndDate) {
			sp.logDebugf("Patchnight is currently running for env %s: %v", env, pd.Date)
			return &patchnightDates[i]
		}

		// Check if patchnightDate is scheduled for the future
		// Keep track of the closest future patchnightDate
		if pd.StartDate.After(currentTime) {
			if nextPatchnight == nil || pd.StartDate.Before(closestTime) {
				nextPatchnight = &patchnightDates[i]
				closestTime = pd.StartDate
			}
		}
	}

	return nextPatchnight
}

// calculatePatchnightTimes calculates the start and end timestamps for a server's patchnight window
// This method combines the patchnight date with server-specific start and end times
// to create precise maintenance windows. It handles:
// - Date parsing and validation
// - Time parsing in HH:MM format
// - Cross-midnight maintenance windows (e.g., 23:00 to 02:00)
// - Timezone handling (uses local time for consistency)
//
// Parameters:
//   - patchnight: The patchnight schedule containing the base date
//   - startTime: Server's maintenance start time in "HH:MM" format
//   - endTime: Server's maintenance end time in "HH:MM" format
//   - location: Timezone location for timestamp creation (typically time.Local)
//
// Returns:
//   - *time.Time: Calculated start timestamp for the maintenance window
//   - *time.Time: Calculated end timestamp for the maintenance window
//   - error: Any error in date/time parsing or calculation
func (sp *ServiceProcessor) calculatePatchnightTimes(patchnight *patchnight.PatchnightDate, startTime, endTime string) (*time.Time, *time.Time, error) {
	loc := berlinLocation()

	// Parse the base patchnight date (YYYY-MM-DD) in Europe/Berlin
	patchnightDate, err := time.ParseInLocation("2006-01-02", patchnight.Date, loc)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to parse patchnight date %s: %w", patchnight.Date, err)
	}

	// Parse server-specific start time (HH:MM format)
	startHour, startMinute, err := parseTime(startTime)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to parse start time %s: %w", startTime, err)
	}

	// Parse server-specific end time (HH:MM format)
	endHour, endMinute, err := parseTime(endTime)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to parse end time %s: %w", endTime, err)
	}

	// Build server start/end on the patchnight "date" in Berlin
	startDate := time.Date(
		patchnightDate.Year(),
		patchnightDate.Month(),
		patchnightDate.Day(),
		startHour,
		startMinute,
		0, 0,
		loc,
	)

	endDate := time.Date(
		patchnightDate.Year(),
		patchnightDate.Month(),
		patchnightDate.Day(),
		endHour,
		endMinute,
		0, 0,
		loc,
	)

	// Determine the patchnight's "start clock time" on patchnightDate in Berlin.
	// Important: use Hour/Minute as given (works even if StartDate was parsed as UTC in tests).
	patchnightStartClock := time.Date(
		patchnightDate.Year(),
		patchnightDate.Month(),
		patchnightDate.Day(),
		patchnight.StartDate.Hour(),
		patchnight.StartDate.Minute(),
		0, 0,
		loc,
	)

	// If the patchnight window spans midnight (e.g., 20:00-06:00),
	// server times after midnight (e.g., 02:00) belong to the next day.
	if startDate.Before(patchnightStartClock) {
		startDate = startDate.Add(24 * time.Hour)
	}
	if endDate.Before(patchnightStartClock) {
		endDate = endDate.Add(24 * time.Hour)
	}

	// Handle cross-midnight maintenance windows (e.g., 23:00 -> 01:00)
	if endDate.Before(startDate) {
		endDate = endDate.Add(24 * time.Hour)
	}

	return &startDate, &endDate, nil
}

// parseTime parses time in HH:MM format and returns hour and minute components
// This utility function provides robust time parsing with validation:
// - Ensures exactly HH:MM format (colon-separated)
// - Validates hour range (0-23)
// - Validates minute range (0-59)
// - Provides clear error messages for debugging
//
// Parameters:
//   - timeStr: Time string in "HH:MM" format (e.g., "14:30", "09:15")
//
// Returns:
//   - int: Hour component (0-23)
//   - int: Minute component (0-59)
//   - error: Parsing or validation error
func parseTime(timeStr string) (int, int, error) {
	// Split time string on colon separator
	parts := strings.Split(timeStr, ":")
	if len(parts) != 2 {
		return 0, 0, fmt.Errorf("invalid time format: %s", timeStr)
	}

	// Parse hour component
	hour, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, 0, fmt.Errorf("invalid hour: %s", parts[0])
	}

	// Parse minute component
	minute, err := strconv.Atoi(parts[1])
	if err != nil {
		return 0, 0, fmt.Errorf("invalid minute: %s", parts[1])
	}

	// Validate hour range (24-hour format)
	if hour < 0 || hour > 23 {
		return 0, 0, fmt.Errorf("hour out of range: %d", hour)
	}

	// Validate minute range
	if minute < 0 || minute > 59 {
		return 0, 0, fmt.Errorf("minute out of range: %d", minute)
	}

	return hour, minute, nil
}

// logDebugf provides conditional debug logging functionality.
// Messages are only logged when debug mode is enabled, reducing noise in production.
//
// Parameters:
//   - format: Printf-style format string
//   - a: Variable arguments for format string substitution
func (sp *ServiceProcessor) logDebugf(msg string, args ...interface{}) {
	if sp.debug {
		slog.Debug("[DEBUG] ServiceProcessor: "+msg, args...)
	}
}

func berlinLocation() *time.Location {
	berlinOnce.Do(func() {
		loc, err := time.LoadLocation("Europe/Berlin")
		if err != nil {
			// Fallback: sollte praktisch nie passieren, aber verhindert nil-Derefs.
			loc = time.FixedZone("Europe/Berlin", 1*60*60)
		}
		berlinLoc = loc
	})
	return berlinLoc
}
