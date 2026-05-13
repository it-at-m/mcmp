package storagegrid

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/httpclient"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const (
	// Base API V3 Prefix
	// If your configured hostname in Config includes the protocol (https://), ensure it is handled correctly.
	// The httpclient usually expects a full URL.
	apiPrefixAuthorize    = "/api/v4/authorize"
	apiPrefixGridAccounts = "/api/v4/grid/accounts"
	apiPatternUsage       = "/api/v4/grid/accounts/%s/usage"
)

// Config holds all configuration parameters for the StorageGRID Client
type Config struct {
	Hostname  string
	Username  string
	Password  string
	Enabled   bool
	VerifyTLS bool
}

func (c *Config) Validate() error {
	if !c.Enabled {
		return nil
	}
	if c.Hostname == "" {
		return fmt.Errorf("StorageGRID hostname is required")
	}
	if c.Username == "" {
		return fmt.Errorf("StorageGRID username is required")
	}
	if c.Password == "" {
		return fmt.Errorf("StorageGRID password is required")
	}
	return nil
}

type Client struct {
	config Config
	client *httpclient.Client
	logger logging.Logger
}

func NewClient(config Config, logger logging.Logger) (*Client, error) {
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	httpClientConfig := httpclient.Config{
		EnableTLSVerify: config.VerifyTLS,
	}

	client, err := httpclient.NewClient(httpClientConfig, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize http client: %w", err)
	}

	return &Client{
		config: config,
		client: client,
		logger: logger,
	}, nil
}

func (c *Client) Hostname() string {
	return c.config.Hostname
}

func (c *Client) IsEnabled() bool {
	return c.config.Enabled
}

func (c *Client) getBaseURL() string {
	return "https://" + c.config.Hostname
}

func (c *Client) Authorize(ctx context.Context) (string, error) {
	requestURL, err := url.JoinPath(c.getBaseURL(), apiPrefixAuthorize)
	if err != nil {
		return "", fmt.Errorf("failed to construct authorize URL: %w", err)
	}

	payload := AuthorizeRequest{
		Username:  c.config.Username,
		Password:  c.config.Password,
		Cookie:    false,
		CsrfToken: false,
	}

	// Use PostJSON which handles Marshal + Request + Status Check
	body, err := c.client.PostJSON(ctx, requestURL, payload)
	if err != nil {
		return "", err
	}

	var response AuthorizeResponse
	if err := json.Unmarshal(body, &response); err != nil {
		return "", fmt.Errorf("failed to decode auth response: %w", err)
	}

	newConfig := httpclient.Config{
		EnableTLSVerify: c.config.VerifyTLS,
		BearerToken:     response.Data,
	}

	newClient, err := httpclient.NewClient(newConfig, c.logger)
	if err != nil {
		return "", fmt.Errorf("failed to re-initialize http client with token: %w", err)
	}
	c.client = newClient

	return response.Data, nil
}

func (c *Client) FetchAccounts(ctx context.Context) ([]Account, error) {
	requestURL, err := url.JoinPath(c.getBaseURL(), apiPrefixGridAccounts)
	if err != nil {
		return nil, fmt.Errorf("failed to construct accounts URL: %w", err)
	}
	requestURL += "?limit=1000"
	var response AccountsResponse
	if err := c.client.GetJSON(ctx, requestURL, &response); err != nil {
		return nil, err
	}

	return response.Data, nil
}

func (c *Client) FetchAccountUsage(ctx context.Context, accountID string) (*UsageData, error) {
	endpoint := fmt.Sprintf(apiPatternUsage, accountID)
	requestURL, err := url.JoinPath(c.getBaseURL(), endpoint)
	if err != nil {
		return nil, fmt.Errorf("failed to construct usage URL: %w", err)
	}
	requestURL += "?includeBucketDetail=true"
	var response AccountUsageResponse
	if err := c.client.GetJSON(ctx, requestURL, &response); err != nil {
		return nil, err
	}

	return &response.Data, nil
}
