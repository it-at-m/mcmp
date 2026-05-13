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

	base, err := oauth2client.NewClient(ctx, config, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to create mcmp client: %w", err)
	}
	return &Client{Client: base}, nil
}
