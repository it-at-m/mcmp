package loadbalancer

import (
	"context"
	"errors"
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/httpclient"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

var (
	ErrAPIURLRequired    = errors.New("loadbalancer API URL is required")
	ErrNilConfigResponse = errors.New("received nil config from loadbalancer API")
)

// ClientConfig holds configuration for the single loadbalancer management API.
type ClientConfig struct {
	APIURL    string `mapstructure:"APIURL"`
	Username  string `mapstructure:"Username"`
	Password  string `mapstructure:"Password"`
	Enabled   bool   `mapstructure:"Enabled"`
	VerifyTLS bool   `mapstructure:"VerifyTLS"`
}

// Validate checks that all required fields are set when the source is enabled.
func (c *ClientConfig) Validate() error {
	if !c.Enabled {
		return nil
	}
	if c.APIURL == "" {
		return ErrAPIURLRequired
	}
	return nil
}

// Client fetches load-balancer configuration from a BIG-IP REST API.
type Client struct {
	config ClientConfig
	http   *httpclient.Client
	logger logging.Logger
}

// NewClient creates a new loadbalancer API client.
func NewClient(config ClientConfig, logger logging.Logger) (*Client, error) {
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	httpCfg := httpclient.Config{
		Username:        config.Username,
		Password:        config.Password,
		EnableTLSVerify: config.VerifyTLS,
	}

	httpClient, err := httpclient.NewClient(httpCfg, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to create HTTP client: %w", err)
	}

	return &Client{
		config: config,
		http:   httpClient,
		logger: logger,
	}, nil
}

// IsEnabled reports whether this source is enabled.
func (c *Client) IsEnabled() bool {
	return c.config.Enabled
}

// FetchConfig retrieves the full BIG-IP configuration from the management API.
func (c *Client) FetchConfig(ctx context.Context) (*Config, error) {
	var cfg Config
	if err := c.http.GetJSON(ctx, c.config.APIURL, &cfg); err != nil {
		return nil, fmt.Errorf("failed to fetch loadbalancer config from %s: %w", c.config.APIURL, err)
	}

	if cfg.VirtualServers == nil {
		return nil, ErrNilConfigResponse
	}

	return &cfg, nil
}
