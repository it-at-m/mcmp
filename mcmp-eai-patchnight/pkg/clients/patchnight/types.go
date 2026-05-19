package patchnight

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

var (
	dateRegex           = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)
	timeRegex           = regexp.MustCompile(`^\d{2}:\d{2}$`)
	flexibleTimeFormats = []string{
		"2006-01-02T15:04:05",
		"2006-01-02T15:04:05Z",
		time.RFC3339,
		time.RFC3339Nano,
		"2006-01-02 15:04:05",
	}
	berlinOnce sync.Once
	berlinLoc  *time.Location
)

// HTTPClient defines the interface for HTTP client operations
// This interface enables dependency injection and testing with mock implementations
type HTTPClient interface {
	Do(req *http.Request) (*http.Response, error)
}

// Client represents an MCMP API client with HTTP communication capabilities and OAuth2 support
// It encapsulates the HTTP client configuration, OAuth2 authentication, and debug logging functionality
type Client struct {
	*logging.DebugLogger              // Embedded debug logger for request/response monitoring
	httpClient           HTTPClient   // HTTP client implementation for API calls (OAuth2-enabled)
	baseURL              *url.URL     // Base URL for API endpoints
	config               ClientConfig // Client configuration
	debug                bool         // Debug flag to enable verbose logging
}

// PatchnightDate represents a single patchnight date entry with environment and time information
// This structure corresponds to individual entries in the patchnight_dates array from the API
type PatchnightDate struct {
	Environment string    `json:"env"`        // Environment identifier ("k" for consolidation, "p" for production)
	Date        string    `json:"date"`       // Date in format "2025-04-11" (YYYY-MM-DD)
	StartDate   time.Time `json:"start_date"` // Start timestamp in RFC3339 format
	EndDate     time.Time `json:"end_date"`   // End timestamp in RFC3339 format
}

// Validate validates the PatchnightDate struct for required fields and consistency
// This method performs comprehensive validation of patchnight date data to ensure:
// - All required fields are present and non-empty
// - Date format follows YYYY-MM-DD pattern
// - Start and end dates are valid timestamps
// - Date consistency (start date must be before end date)
//
// The validation ensures data integrity before processing or API transmission
// and provides clear error messages for troubleshooting configuration issues.
//
// Returns:
//   - error: nil if validation passes, descriptive error message if validation fails
//
// Validation Rules:
//   - Environment field is mandatory and cannot be empty
//   - Date field must be in YYYY-MM-DD format
//   - Start date must be a valid timestamp
//   - End date must be a valid timestamp
//   - Start date must be before end date
func (pd *PatchnightDate) Validate() error {
	if pd.Environment == "" {
		return fmt.Errorf("environment is required")
	}
	if pd.Date == "" {
		return fmt.Errorf("date is required")
	}
	if !dateRegex.MatchString(pd.Date) {
		return fmt.Errorf("date must be in format YYYY-MM-DD")
	}
	if pd.StartDate.IsZero() {
		return fmt.Errorf("start date is required")
	}
	if pd.EndDate.IsZero() {
		return fmt.Errorf("end date is required")
	}
	if pd.StartDate.After(pd.EndDate) {
		return fmt.Errorf("start date must be before end date")
	}
	return nil
}

// Duration returns the duration of the patchnight window
// This method calculates the total time span of the patchnight maintenance window
// by computing the difference between end and start dates.
//
// Returns:
//   - time.Duration: Duration of the patchnight window
//
// Usage:
//   - Maintenance window planning and resource allocation
//   - Validation of reasonable maintenance window sizes
//   - Reporting and analytics of patchnight operation durations
func (pd *PatchnightDate) Duration() time.Duration {
	return pd.EndDate.Sub(pd.StartDate)
}

// IsActive returns true if the patchnight is currently active
// This method determines whether the patchnight maintenance window is currently
// active by checking if the provided timestamp falls within the maintenance window.
//
// Parameters:
//   - now: Current timestamp to check against the maintenance window
//
// Returns:
//   - bool: true if the patchnight is currently active, false otherwise
//
// Usage:
//   - Real-time monitoring of maintenance window status
//   - Conditional execution of maintenance operations
//   - Status reporting and dashboard updates
func (pd *PatchnightDate) IsActive(now time.Time) bool {
	return !now.Before(pd.StartDate) && !now.After(pd.EndDate)
}

// PatchnightDateResponse represents the complete API response for patchnight dates
type PatchnightDateResponse struct {
	CreateDate      string           `json:"create_date"`      // Creation date in format "2025-07-02" (YYYY-MM-DD)
	PatchnightDates []PatchnightDate `json:"patchnight_dates"` // Array of all patchnight date entries
}

// PatchnightLinuxIncludedServer represents a server that is included in patchnight operations
type PatchnightLinuxIncludedServer struct {
	Environment string `json:"env"`        // Environment identifier ("k" for consolidation, "p" for production)
	Name        string `json:"name"`       // Fully qualified domain name (FQDN) of the server
	StartTime   string `json:"start_time"` // Start time in format "15:00" (HH:MM)
	EndTime     string `json:"end_time"`   // End time in format "17:00" (HH:MM)
	OS          string `json:"os"`         // Operating system name (e.g., "RedHat", "SLES")
	OSVersion   string `json:"os_version"` // Operating system version (e.g., "7.9", "15.4")
}

// Validate validates the PatchnightLinuxIncludedServer struct
// This method performs comprehensive validation of server configuration data to ensure:
// - All required fields are present and non-empty
// - Time format follows HH:MM pattern
// - Server name is valid
// - Environment identifier is present
//
// Returns:
//   - error: nil if validation passes, descriptive error message if validation fails
//
// Validation Rules:
//   - Server name is mandatory and cannot be empty
//   - Environment field is mandatory and cannot be empty
//   - Start time must be in HH:MM format (if provided)
//   - End time must be in HH:MM format (if provided)
func (pis *PatchnightLinuxIncludedServer) Validate() error {
	if pis.Name == "" {
		return fmt.Errorf("server name is required")
	}
	if pis.Environment == "" {
		return fmt.Errorf("environment is required")
	}
	if pis.StartTime != "" && !timeRegex.MatchString(pis.StartTime) {
		return fmt.Errorf("start time must be in format HH:MM")
	}
	if pis.EndTime != "" && !timeRegex.MatchString(pis.EndTime) {
		return fmt.Errorf("end time must be in format HH:MM")
	}
	return nil
}

// GetMaintenanceWindow returns the parsed start and end times as time.Duration from midnight
// This method parses the maintenance window times and converts them to duration values
// that can be used for scheduling and time calculations.
//
// Returns:
//   - start: Duration from midnight to the start of the maintenance window
//   - end: Duration from midnight to the end of the maintenance window
//   - error: Parsing error or nil on success
//
// Usage:
//   - Scheduling maintenance operations
//   - Calculating maintenance window durations
//   - Validating maintenance window overlaps
func (pis *PatchnightLinuxIncludedServer) GetMaintenanceWindow() (start, end time.Duration, err error) {
	if pis.StartTime == "" || pis.EndTime == "" {
		return 0, 0, fmt.Errorf("start time and end time are required")
	}

	start, err = parseTimeOfDay(pis.StartTime)
	if err != nil {
		return 0, 0, fmt.Errorf("invalid start time: %w", err)
	}

	end, err = parseTimeOfDay(pis.EndTime)
	if err != nil {
		return 0, 0, fmt.Errorf("invalid end time: %w", err)
	}

	return start, end, nil
}

// PatchnightIncludedServersResponse represents the complete API response for included servers
type PatchnightIncludedServersResponse struct {
	CreateDate                string                          `json:"create_date"`           // Creation date in format "2025-07-02" (YYYY-MM-DD)
	PatchnightIncludedServers []PatchnightLinuxIncludedServer `json:"patchnight_includ_all"` // Array of included servers (preserves API typo)
}

// PatchnightLinuxExcludedServer represents a server that is excluded from patchnight operations
type PatchnightLinuxExcludedServer struct {
	Name      string `json:"name"`       // Fully qualified domain name (FQDN) of the server
	OS        string `json:"os"`         // Operating system name (e.g., "RedHat", "SLES")
	OSVersion string `json:"os_version"` // Operating system version (e.g., "7.9", "15.4")
}

// Validate validates the PatchnightLinuxExcludedServer struct
// This method performs validation of excluded server information to ensure:
// - Server name is present and non-empty
// - Basic server information is valid
//
// Returns:
//   - error: nil if validation passes, descriptive error message if validation fails
//
// Validation Rules:
//   - Server name is mandatory and cannot be empty
func (pes *PatchnightLinuxExcludedServer) Validate() error {
	if pes.Name == "" {
		return fmt.Errorf("server name is required")
	}
	return nil
}

// PatchnightExcludedServersResponse represents the complete API response for excluded servers
type PatchnightExcludedServersResponse struct {
	CreateDate                     string                          `json:"create_date"`           // Creation date in format "2025-07-02" (YYYY-MM-DD)
	PatchnightLinuxExcludedServers []PatchnightLinuxExcludedServer `json:"patchnight_exclud_all"` // Array of excluded servers (preserves API typo)
}

// UnmarshalJSON implements custom JSON unmarshaling for PatchnightDate
// This method provides flexible parsing of time formats that may be returned
// by different API versions or configurations. It handles various timestamp
// formats and converts them to proper time.Time objects.
//
// Supported Time Formats:
//   - RFC3339 (standard ISO 8601 format)
//   - RFC3339Nano (with nanosecond precision)
//   - "2006-01-02T15:04:05" (without timezone)
//   - "2006-01-02T15:04:05Z" (with Z timezone indicator)
//   - "2006-01-02 15:04:05" (space-separated format)
//
// Parameters:
//   - data: Raw JSON data to unmarshal
//
// Returns:
//   - error: Unmarshaling error or nil on success
func (pd *PatchnightDate) UnmarshalJSON(data []byte) error {
	type Alias struct {
		Environment string `json:"env"`
		Date        string `json:"date"`
		StartDate   string `json:"start_date"`
		EndDate     string `json:"end_date"`
	}

	var aux Alias
	if err := json.Unmarshal(data, &aux); err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %w", err)
	}

	pd.Environment = aux.Environment
	pd.Date = aux.Date

	if aux.StartDate != "" {
		startDate, err := parseFlexibleTime(aux.StartDate)
		if err != nil {
			return fmt.Errorf("failed to parse start_date '%s': %w", aux.StartDate, err)
		}
		pd.StartDate = startDate
	}

	if aux.EndDate != "" {
		endDate, err := parseFlexibleTime(aux.EndDate)
		if err != nil {
			return fmt.Errorf("failed to parse end_date '%s': %w", aux.EndDate, err)
		}
		pd.EndDate = endDate
	}

	return nil
}

// parseFlexibleTime parses time strings in various formats commonly used by APIs
// This function attempts to parse time strings using multiple common formats
// to handle different API responses and time representations.
//
// Parameters:
//   - timeStr: Time string to parse
//
// Returns:
//   - time.Time: Parsed time object
//   - error: Parsing error or nil on success
//
// Supported Formats:
//   - ISO 8601 variants (with and without timezone)
//   - RFC3339 and RFC3339Nano
//   - Custom format variations
func parseFlexibleTime(timeStr string) (time.Time, error) {
	timeStr = strings.TrimSpace(timeStr)
	if timeStr == "" {
		return time.Time{}, fmt.Errorf("empty time string")
	}

	loc := berlinLocation()

	// 1) Formate OHNE Zeitzone: als Europe/Berlin interpretieren (nicht UTC!)
	for _, format := range []string{
		"2006-01-02T15:04:05",
		"2006-01-02 15:04:05",
	} {
		if t, err := time.ParseInLocation(format, timeStr, loc); err == nil {
			return t, nil
		}
	}

	// 2) Formate MIT Zeitzone/Offset: normal parsen
	for _, format := range []string{
		"2006-01-02T15:04:05Z",
		time.RFC3339,
		time.RFC3339Nano,
	} {
		if t, err := time.Parse(format, timeStr); err == nil {
			return t, nil
		}
	}

	return time.Time{}, fmt.Errorf("unable to parse time string %q with any known format", timeStr)
}

// parseTimeOfDay parses a time string in HH:MM format and returns duration from midnight
// This function converts time-of-day strings to duration values that can be used
// for scheduling and time calculations.
//
// Parameters:
//   - timeStr: Time string in HH:MM format
//
// Returns:
//   - time.Duration: Duration from midnight to the specified time
//   - error: Parsing error or nil on success
//
// Usage:
//   - Converting maintenance window times to durations
//   - Scheduling operations based on time-of-day
//   - Calculating time differences within a day
func parseTimeOfDay(timeStr string) (time.Duration, error) {
	t, err := time.Parse("15:04", timeStr)
	if err != nil {
		return 0, fmt.Errorf("invalid time format %q: %w", timeStr, err)
	}
	return time.Duration(t.Hour())*time.Hour + time.Duration(t.Minute())*time.Minute, nil
}

// WindowsServer represents a single Windows server entry as returned by the
// Patchnight Windows include/exclude APIs.
//
// JSON example:
//
//	[
//	  { "FullDomainName": "aadcwip001.example.org" },
//	  { "FullDomainName": "aualtwik002.example.org" }
//	]
//
// The struct is intentionally minimal and only captures the FQDN of the server.
// Additional fields can be added later if the API starts returning more data.
type WindowsServer struct {
	// FullDomainName contains the fully qualified domain name (FQDN) of the server,
	// for example "eplandsp004.example.org".
	//
	// It is mapped directly from the JSON field "FullDomainName".
	FullDomainName string `json:"FullDomainName"`
}

// WindowsPatchnightStatus represents the update status information for a single
// Windows server as returned by the Patchnight UpdateStatus_Report.json API.
//
// JSON example:
//
//	[
//	  {
//	    "Server": "server1.example.org",
//	    "UpdateStatus": "0",
//	    "UpdateTitles": ""
//	  },
//	  {
//	    "Server": "server2.example.org",
//	    "UpdateStatus": "1",
//	    "UpdateTitles": [
//	      "2025-10 Cumulative Update for ...",
//	      "2025-11 Cumulative Update for ..."
//	    ]
//	  }
//	]
//
// The UpdateTitles field is stored as a single string, even though the JSON
// representation may either be a string or an array of strings. A custom
// UnmarshalJSON implementation on UpdateTitlesString takes care of this
// normalization.
type WindowsPatchnightStatus struct {
	// Server holds the fully qualified domain name (FQDN) of the Windows server,
	// e.g. "server1.example.org".
	Server string `json:"Server"`

	// UpdateStatus stores the raw update status as an int8 value.
	//
	// Expected JSON representation:
	//   "UpdateStatus": "0"
	//   "UpdateStatus": "1"
	//
	// Conversion rules in UpdateStatusInt8:
	//   - String "0" → 0
	//   - Other valid integer strings → corresponding int8 value
	//   - Numeric JSON values → corresponding int8 value
	//   - For non-parsable values, 1 is used as a conservative fallback
	//     (meaning "updates present").
	UpdateStatus UpdateStatusInt8 `json:"UpdateStatus"`

	// UpdateTitles contains a human-readable list of update titles affecting
	// the server. Although the JSON representation can be either a simple
	// string or an array of strings, this field always stores a single string.
	//
	// Behavior:
	//   - If the JSON value is a string, it is stored as-is.
	//   - If the JSON value is an array of strings, the elements are joined
	//     using a newline character ("\n") and the resulting multi-line
	//     string is stored.
	//   - For any other JSON type, the raw JSON text is stored as string.
	UpdateTitles UpdateTitlesString `json:"UpdateTitles"`
}

// Validate validates a WindowsPatchnightStatus instance to ensure that the
// minimum required fields are present and structurally correct.
//
// Current validation rules:
//   - Server must not be empty or whitespace only
//   - If UpdateStatus != 0 and UpdateTitles is empty/whitespace, a default
//     English error message is written into UpdateTitles.
//
// The method mutates the receiver (it may set a default UpdateTitles value)
// and returns an error only if mandatory fields are missing/invalid.
func (w *WindowsPatchnightStatus) Validate() error {
	// Server name is mandatory
	if strings.TrimSpace(w.Server) == "" {
		return fmt.Errorf("server is required")
	}

	// If updates are pending (UpdateStatus != 0) but no textual information
	// is available, set a generic error message.
	if w.UpdateStatus != 0 {
		if strings.TrimSpace(string(w.UpdateTitles)) == "" {
			w.UpdateTitles = "An unknown error occurred while retrieving update titles for this server."
		}
	}

	return nil
}

// UpdateStatusInt8 is a helper type that converts the string-based
// "UpdateStatus" field from the Windows Patchnight status API into an
// int8 value.
//
// Typical conversion:
//   - "0"  -> 0
//   - "1"  -> 1
//   - other valid integer strings / numeric JSON values -> corresponding int8
//   - non-parseable values -> 1 (conservative: "updates present")
type UpdateStatusInt8 int8

// UnmarshalJSON implements json.Unmarshaler for UpdateStatusInt8.
//
// It is tolerant regarding the concrete JSON representation:
//  1. It first tries to read a string and parse it as an integer.
//  2. If that fails, it tries to parse the value as a numeric JSON value (int64).
//  3. If both attempts fail, it falls back to 1.
func (s *UpdateStatusInt8) UnmarshalJSON(data []byte) error {
	// 1) Expected path: JSON string, e.g. "0", "1", "2"
	var str string
	if err := json.Unmarshal(data, &str); err == nil {
		if str == "" {
			// Treat empty strings as "0" (no updates)
			*s = 0
			return nil
		}
		if v, err := strconv.ParseInt(str, 10, 8); err == nil {
			*s = UpdateStatusInt8(v)
			return nil
		}
		// Non-parsable string → conservative fallback 1
		*s = 1
		return nil
	}

	// 2) Second attempt: direct numeric value
	var num int64
	if err := json.Unmarshal(data, &num); err == nil {
		*s = UpdateStatusInt8(num)
		return nil
	}

	// 3) Fallback: unparseable → 1 (updates present)
	*s = 1
	return nil
}

// UpdateTitlesString is a helper type that normalizes the "UpdateTitles" field
// from the Patchnight Windows status API into a simple Go string.
//
// The source JSON for "UpdateTitles" is not consistent:
//   - Sometimes it is an empty string: ""
//   - Sometimes it is a non-empty string
//   - Sometimes it is an array of strings: ["Title 1", "Title 2", ...]
//
// UpdateTitlesString implements json.Unmarshaler so it can gracefully handle
// all of these representations and always expose a single string value in Go.
//
// Normalization rules:
//  1. If the JSON value is a string, it is used directly.
//  2. If the JSON value is a []string, all elements are joined by "\n".
//  3. If the JSON value is of any other type, the raw JSON is stored as-is.
//
// This makes the field easy to use in templates, logs, and exports without
// having to special-case the different JSON input formats.
type UpdateTitlesString string

// UnmarshalJSON implements the json.Unmarshaler interface for UpdateTitlesString.
//
// It supports multiple JSON representations of the "UpdateTitles" field and
// converts them into a single Go string according to the following rules:
//
//  1. JSON string:
//     "UpdateTitles": "Single update title"
//     → stored as "Single update title"
//
//  2. JSON empty string:
//     "UpdateTitles": ""
//     → stored as "" (empty string)
//
//  3. JSON array of strings:
//     "UpdateTitles": ["Title 1", "Title 2"]
//     → stored as "Title 1\nTitle 2"
//
//  4. Any other JSON type (e.g. number, object, array of non-strings):
//     The raw JSON text is stored as string, for example:
//     "UpdateTitles": 123
//     → stored as "123"
//
// The method never returns a non-nil error. In the worst case it falls back
// to storing the raw JSON representation as string. This makes the code
// robust against format changes in the upstream API while still preserving
// the original data in a human-inspectable form.
func (u *UpdateTitlesString) UnmarshalJSON(data []byte) error {
	// First, try to interpret the JSON value as a simple string.
	// This covers cases like:
	//   "UpdateTitles": ""
	//   "UpdateTitles": "Single update title"
	var s string
	if err := json.Unmarshal(data, &s); err == nil {
		*u = UpdateTitlesString(s)
		return nil
	}

	// If it is not a plain string, try to interpret it as an array of strings:
	//   "UpdateTitles": ["Title 1", "Title 2", ...]
	// In this case we join all elements using a newline character.
	var arr []string
	if err := json.Unmarshal(data, &arr); err == nil {
		joined := strings.Join(arr, "\n")
		*u = UpdateTitlesString(joined)
		return nil
	}

	// As a last resort, if neither string nor []string worked, store the raw
	// JSON data as string. This ensures we never lose information, even if the
	// source format changes unexpectedly (e.g. number, object, mixed array).
	*u = UpdateTitlesString(data)
	return nil
}

func berlinLocation() *time.Location {
	berlinOnce.Do(func() {
		loc, err := time.LoadLocation("Europe/Berlin")
		if err != nil {
			// Fallback (sollte praktisch nie passieren)
			loc = time.FixedZone("Europe/Berlin", 1*60*60)
		}
		berlinLoc = loc
	})
	return berlinLoc
}
