package mcmp

import (
	"context"
	"fmt"
	"strings"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/oauth2client"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// Client wraps the universal oauth2client and adds MCMP-specific functionality.
type Client struct {
	*oauth2client.Client
	logger               logging.Logger
	discoveryBackend     bool
	oracleServerEndpoint string
}

// IsDiscoveryBackend returns true if this client is configured as the discovery source.
func (c *Client) IsDiscoveryBackend() bool {
	return c.discoveryBackend
}

// NewClient creates a new MCMP client backed by the universal oauth2client.
// Additionally validates that ApiEndpoint is set, which is required for MCMP.
func NewClient(ctx context.Context, config ClientConfig, logger logging.Logger) (*Client, error) {
	if config.ApiEndpoint == "" {
		return nil, fmt.Errorf("MCMP API endpoint is required")
	}

	if strings.TrimSpace(config.TokenURL) == "" {
		if config.AuthServerURL == "" || config.Realm == "" {
			return nil, fmt.Errorf("MCMP auth server URL and realm are required to build token URL")
		}
		config.TokenURL = strings.TrimRight(config.AuthServerURL, "/") + "/realms/" + strings.TrimSpace(config.Realm) + "/protocol/openid-connect/token"
	}

	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	base, err := oauth2client.NewClient(ctx, config.ClientConfig, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to create mcmp client: %w", err)
	}

	return &Client{
		Client:               base,
		logger:               logger,
		discoveryBackend:     config.DiscoveryBackend,
		oracleServerEndpoint: config.OracleServerEndpoint,
	}, nil
}

// GetAllOracleServers retrieves a list of all Oracle servers from the MCMP API.
// It calls the configured OracleServerEndpoint and unmarshals the response into a slice of OracleServer.
func (c *Client) GetAllOracleServers(ctx context.Context) ([]OracleServer, error) {
	if c.oracleServerEndpoint == "" {
		return nil, fmt.Errorf("oracle server endpoint is required but not configured")
	}

	var servers []OracleServer
	err := c.GetJSONUnmarshal(ctx, c.oracleServerEndpoint, &servers)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch Oracle servers: %w", err)
	}

	c.logger.DebugPrintf("Retrieved %d Oracle servers from MCMP", len(servers))
	return servers, nil
}
