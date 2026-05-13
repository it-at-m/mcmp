package olvm

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"strings"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/oauth2client"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

var (
	ErrOLVMHostnameRequired  = errors.New("OLVM hostname is required")
	ErrAuthServerURLRequired = errors.New("OLVM auth server URL is required to build token URL")
)

// Client is an OLVM-specific API client based on the shared OAuth2 client.
// It extends the base OAuth2 client with OLVM-specific endpoint construction and API methods.
type Client struct {
	*oauth2client.Client
	apiBaseURL string
	hostname   string
}

// NewClient creates a new OLVM client.
// It initializes the client with the provided configuration, validates required fields,
// sets up the base OAuth2 client, and constructs OLVM-specific URLs.
func NewClient(ctx context.Context, config oauth2client.ClientConfig, logger logging.Logger) (*Client, error) {
	if strings.TrimSpace(config.ApiEndpoint) == "" {
		return nil, ErrOLVMHostnameRequired
	}

	if strings.TrimSpace(config.TokenURL) == "" {
		if config.AuthServerURL == "" {
			return nil, ErrAuthServerURLRequired
		}
		config.TokenURL = strings.TrimRight(config.AuthServerURL, "/") + "/oauth/token"
	}

	base, err := oauth2client.NewClient(ctx, config, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to create OLVM client: %w", err)
	}

	u, err := url.Parse(config.ApiEndpoint)
	if err != nil {
		return nil, fmt.Errorf("failed to parse api endpoint: %w", err)
	}

	return &Client{
		Client:     base,
		apiBaseURL: strings.TrimRight(config.ApiEndpoint, "/"),
		hostname:   u.Hostname(),
	}, nil
}

// GetHostname returns the hostname of the OLVM managed server.
func (c *Client) GetHostname() string {
	return c.hostname
}

// endpoint constructs the full API endpoint URL for the given path.
// It prepends the OLVM API base path to the provided path.
func (c *Client) endpoint(path string) string {
	return c.apiBaseURL + "/ovirt-engine/api/" + strings.TrimLeft(path, "/")
}

// get performs a GET request to the specified API path, unmarshals the JSON response into the target, and returns it.
func get[T any](ctx context.Context, c *Client, path, errMsg string) (*T, error) {
	var target T
	if err := c.GetJSONUnmarshal(ctx, c.endpoint(path), &target); err != nil {
		return nil, fmt.Errorf("%s: %w", errMsg, err)
	}
	return &target, nil
}

// GetVMs retrieves the list of virtual machines (VMs) from the OLVM managed server. Returns a VMsResponse or an error.
func (c *Client) GetVMs(ctx context.Context) (*VMsResponse, error) {
	return get[VMsResponse](ctx, c, "vms", "failed to get VMs")
}

// GetClusters retrieves the list of clusters from the OLVM managed server. Returns a ClustersResponse or an error.
func (c *Client) GetClusters(ctx context.Context) (*ClustersResponse, error) {
	return get[ClustersResponse](ctx, c, "clusters", "failed to get clusters")
}

// GetHosts retrieves the list of hosts from the OLVM managed server. Returns a HostsResponse or an error.
func (c *Client) GetHosts(ctx context.Context) (*HostsResponse, error) {
	return get[HostsResponse](ctx, c, "hosts", "failed to get hosts")
}
