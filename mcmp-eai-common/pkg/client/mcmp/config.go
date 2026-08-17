package mcmp

import (
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/oauth2client"
)

// Config embeds the base OAuth2 configuration and adds MCMP-specific fields.
type Config struct {
	oauth2client.Config  `mapstructure:",squash"`
	DiscoveryBackend     bool   // If true, this MCMP client is used as the source for discovery data (e.g. Oracle servers). Default: false
	OracleServerEndpoint string // Optional: Endpoint to fetch list of Oracle servers
}

// ClientConfig holds runtime configuration for the MCMP client.
type ClientConfig struct {
	oauth2client.ClientConfig
	DiscoveryBackend     bool
	OracleServerEndpoint string
}

// ToClientConfig converts Config to ClientConfig.
func (c *Config) ToClientConfig() ClientConfig {
	return ClientConfig{
		ClientConfig:         c.Config.ToClientConfig(),
		DiscoveryBackend:     c.DiscoveryBackend,
		OracleServerEndpoint: c.OracleServerEndpoint,
	}
}
