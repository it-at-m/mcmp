package olvm

import (
	"errors"
	"fmt"
	"strings"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/oauth2client"
)

var ErrConfigHostnameRequired = errors.New("hostname is required")

// ClientConfig is an alias for oauth2client.ClientConfig, providing the underlying OAuth2 client configuration.
type ClientConfig = oauth2client.ClientConfig

// Config holds the configuration for the OLVM client.
// It includes the hostname for the OLVM server and embeds the OAuth2 configuration.
type Config struct {
	Hostname string `mapstructure:"Hostname"`
	Enabled  bool   `mapstructure:"Enabled"`

	oauth2client.Config `mapstructure:",squash"`
}

// Validate checks if the configuration is valid.
// It ensures that the hostname is provided and that the embedded OAuth2 configuration validates successfully.
func (c Config) Validate() error {
	if strings.TrimSpace(c.Hostname) == "" {
		return ErrConfigHostnameRequired
	}
	if err := c.Config.Validate(); err != nil {
		return fmt.Errorf("failed to validate oauth2 config: %w", err)
	}
	return nil
}

// ToClientConfig converts the Config to a ClientConfig.
// It sets the ApiEndpoint based on the hostname and delegates the conversion to the embedded OAuth2 config.
func (c Config) ToClientConfig() ClientConfig {
	clientConfig := c.Config.ToClientConfig()

	if strings.TrimSpace(c.Hostname) != "" {
		scheme := "https"
		if strings.HasPrefix(strings.ToLower(strings.TrimSpace(c.Hostname)), "http://") ||
			strings.HasPrefix(strings.ToLower(strings.TrimSpace(c.Hostname)), "https://") {
			clientConfig.ApiEndpoint = strings.TrimRight(c.Hostname, "/")
		} else {
			clientConfig.ApiEndpoint = scheme + "://" + strings.TrimRight(c.Hostname, "/")
		}
	}

	return clientConfig
}
