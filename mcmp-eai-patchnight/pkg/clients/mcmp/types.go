package mcmp

import (
	"fmt"
	"net/http"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// HTTPClient defines the interface for HTTP client operations
// This interface enables dependency injection and testing with mock implementations
type HTTPClient interface {
	Do(req *http.Request) (*http.Response, error)
}

// Client represents an MCMP API client with HTTP communication capabilities and OAuth2 support
// It encapsulates the HTTP client configuration, OAuth2 authentication, and debug logging functionality
type Client struct {
	*logging.DebugLogger            // Embedded debug logger for request/response monitoring
	httpClient           HTTPClient // HTTP client implementation for API calls (OAuth2-enabled)
	debug                bool       // Debug flag to enable verbose logging
}

// PatchnightData represents the complete patchnight data structure containing server information
// This structure serves as the root container for all server-related patchnight operations
type PatchnightData struct {
	Servers []Server `json:"server"` // Array of all servers included in patchnight operations
}

// Server represents a single server entry in the patchnight system
// This structure contains comprehensive server information including environment, scheduling, and system details
type Server struct {
	Environment *string    `json:"env,omitempty"`        // Environment identifier ("k" for consolidation, "p" for production)
	Name        string     `json:"name"`                 // Fully qualified domain name (FQDN) of the server
	Include     bool       `json:"include"`              // Inclusion flag: true = server will be patched, false = server excluded from patching
	StartDate   *time.Time `json:"start_date,omitempty"` // Patch operation start timestamp in RFC3339 format
	EndDate     *time.Time `json:"end_date,omitempty"`   // Patch operation end timestamp in RFC3339 format
	Exitcode    *int8      `json:"exitcode,omitempty"`   // Exitcode represents the result code of the patch operation, where nil indicates no operation executed.
	ExitString  *string    `json:"exitstring,omitempty"` // ExitString represents the result string of the patch operation, where nil indicates no operation executed.
}

// Validate validates the Server struct for required fields and consistency
// This method performs comprehensive validation of server data to ensure:
// - Required fields are present and non-empty
// - Date consistency (start date must be before end date)
// - Logical field combinations are valid
//
// The validation ensures data integrity before processing or API transmission
// and provides clear error messages for troubleshooting configuration issues.
//
// Returns:
//   - error: nil if validation passes, descriptive error message if validation fails
//
// Validation Rules:
//   - Server name is mandatory and cannot be empty
//   - If server is included and has both start and end dates, start must be before end
//   - Environment can be nil (optional) or a valid environment identifier
func (s *Server) Validate() error {
	if s.Name == "" {
		return fmt.Errorf("server name is required")
	}

	if s.Include && s.StartDate != nil && s.EndDate != nil {
		if s.StartDate.After(*s.EndDate) {
			return fmt.Errorf("start date must be before end date")
		}
	}

	return nil
}

// IsScheduled returns true if the server has complete scheduling information
// This method checks whether both start and end dates are provided for the server.
// A server is considered scheduled only when both timestamps are present,
// indicating a complete maintenance window definition.
//
// Returns:
//   - bool: true if both StartDate and EndDate are non-nil, false otherwise
//
// Usage:
//   - Used to determine if maintenance window calculations are possible
//   - Helps identify servers with incomplete scheduling data
//   - Enables conditional processing based on schedule availability
func (s *Server) IsScheduled() bool {
	return s.StartDate != nil && s.EndDate != nil
}

// Duration returns the duration of the patch window, or zero if not scheduled
// This method calculates the total time span of the server's maintenance window
// by computing the difference between end and start dates.
//
// Returns:
//   - time.Duration: Duration of the maintenance window, or 0 if not scheduled
//
// Behavior:
//   - Returns zero duration if server is not scheduled (missing start/end dates)
//   - Returns positive duration for valid maintenance windows
//   - Duration calculation uses Go's time.Duration for precise time arithmetic
//
// Usage:
//   - Maintenance window planning and resource allocation
//   - Validation of reasonable maintenance window sizes
//   - Reporting and analytics of patch operation durations
func (s *Server) Duration() time.Duration {
	if !s.IsScheduled() {
		return 0
	}
	return s.EndDate.Sub(*s.StartDate)
}
