package mcmp

import (
	"errors"
	"fmt"

	commonmcmp "github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
)

var (
	ErrRightsizingEndpointRequired   = errors.New("rightsizing endpoint is required")
	ErrServerListEndpointRequired    = errors.New("server list endpoint is required")
	ErrServerMetricsEndpointRequired = errors.New("server metrics endpoint is required")
)

// Config extends the common MCMP configuration with rightsizing-specific endpoints.
//
// Config embeds commonmcmp.Config to inherit all OAuth2 and HTTP transport settings,
// then adds three additional endpoint URLs required for rightsizing operations:
// retrieving server lists, fetching individual server metrics, and submitting
// rightsizing recommendations.
//
// The embedded Config provides:
//   - OAuth2 authentication settings (OAuthUrl, OAuthRealm, OAuthClientId, OAuthClientSecret)
//   - HTTP timeout and retry configuration
//   - Connection pooling and TLS settings
//
// The additional fields provide:
//   - RightsizingEndpoint: Where to POST the computed rightsizing recommendations
//   - ServerListEndpoint: Where to GET the list of all available server IDs
//   - ServerMetricEndpoint: Where to GET detailed metrics for a specific server
type Config struct {
	commonmcmp.Config    `mapstructure:",squash"` // Embedded: inherits all fields from common Config
	RightsizingEndpoint  string                   // Where to send rightsizing results
	ServerListEndpoint   string                   // Endpoint to fetch list of server IDs
	ServerMetricEndpoint string                   // Endpoint pattern for server data (use %d for ID)
	ApiEndpoint          string                   `toml:"-" mapstructure:"-"` // Ignore the embedded field
}

// Validate checks if all required configuration fields are set.
//
// Validate performs comprehensive validation of both the embedded common configuration
// and the rightsizing-specific endpoints. It ensures that all required fields are
// present and valid before the client is initialized.
//
// Validation includes:
//   - All common config fields (OAuth2 settings, timeouts, etc.)
//   - All rightsizing-specific endpoint URLs (must not be empty)
//
// The method temporarily sets the ApiEndpoint field to RightsizingEndpoint for
// proper common config validation, as the common client uses ApiEndpoint for
// the primary API endpoint.
//
// Returns:
//   - error: nil if all configuration is valid, otherwise an error describing
//     which field(s) are missing or invalid. Errors are wrapped with context
//     about what validation failed.
//
// Example:
//
//	config := cfg.MCMP
//	if err := config.Validate(); err != nil {
//	    logger.Fatal("invalid configuration", "error", err)
//	}
func (c *Config) Validate() error {
	// Temporarily set ApiEndpoint for base validation
	c.Config.ApiEndpoint = c.RightsizingEndpoint

	// First validate the embedded common config
	if err := c.Config.Validate(); err != nil {
		return fmt.Errorf("common MCMP config validation failed: %w", err)
	}

	// Then validate rightsizing-specific fields
	if c.RightsizingEndpoint == "" {
		return ErrRightsizingEndpointRequired
	}
	if c.ServerListEndpoint == "" {
		return ErrServerListEndpointRequired
	}
	if c.ServerMetricEndpoint == "" {
		return ErrServerMetricsEndpointRequired
	}

	return nil
}
