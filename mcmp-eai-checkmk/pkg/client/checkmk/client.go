package checkmk

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/httpclient"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const (
	apiPrefixServiceCollectionPost = "/lhmmon/check_mk/api/1.0/domain-types/service/collections/all"
	apiPrefixServiceCollectionGet  = "/lhmmon/check_mk/api/v1/domain-types/service/collections/all"
)

var (
	ErrHostnameRequired             = errors.New("CheckMK hostname is required")
	ErrUsernameRequired             = errors.New("CheckMK username is required")
	ErrPasswordRequired             = errors.New("CheckMK password is required")
	ErrNilPerformanceResponse       = errors.New("received nil value in performance response")
	defaultServiceCollectionPayload = PerformanceRequest{
		Columns: []string{"host_name", "performance_data"},
		Query: Query{
			Op:    "~",
			Left:  "description",
			Right: "Memory|CPU utilization|Filesystem",
		},
	}
)

// Config holds all configuration parameters for the CheckMK Client
type Config struct {
	Hostname  string `mapstructure:"Hostname"`
	Username  string `mapstructure:"Username"`
	Password  string `mapstructure:"Password"`
	Enabled   bool   `mapstructure:"Enabled"`
	VerifyTLS bool   `mapstructure:"VerifyTLS"`
}

func (c *Config) Validate() error {
	if !c.Enabled {
		return nil // Disabled configurations don't need validation
	}
	if c.Hostname == "" {
		return ErrHostnameRequired
	}
	if c.Username == "" {
		return ErrUsernameRequired
	}
	if c.Password == "" {
		return ErrPasswordRequired
	}
	return nil
}

type Client struct {
	client *httpclient.Client
	logger logging.Logger
	config Config
}

func NewClient(config Config, logger logging.Logger) (*Client, error) {
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	httpClientConfig := httpclient.Config{
		Username:        config.Username,
		Password:        config.Password,
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

func (c *Client) FetchPerformanceDataPost(ctx context.Context) ([]PerformanceItem, error) {
	fullURL := c.getBaseURL() + apiPrefixServiceCollectionPost
	payload := defaultServiceCollectionPayload

	var response PerformanceResponse
	err := c.client.PostJSONUnmarshal(ctx, fullURL, payload, &response)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch performance data: %w", err)
	}

	return c.validatePerformanceResponse(&response)
}

func (c *Client) FetchPerformanceData(ctx context.Context) ([]PerformanceItem, error) {
	query := url.Values{}
	for _, column := range defaultServiceCollectionPayload.Columns {
		query.Add("columns", column)
	}

	queryJSON, err := json.Marshal(defaultServiceCollectionPayload.Query)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal performance query: %w", err)
	}
	query.Set("query", string(queryJSON))

	fullURL := c.getBaseURL() + apiPrefixServiceCollectionGet + "?" + query.Encode()

	var response PerformanceResponse
	if err := c.client.GetJSON(ctx, fullURL, &response); err != nil {
		return nil, fmt.Errorf("failed to fetch performance data: %w", err)
	}

	return c.validatePerformanceResponse(&response)
}

func (c *Client) validatePerformanceResponse(response *PerformanceResponse) ([]PerformanceItem, error) {
	if response.Value == nil {
		return nil, ErrNilPerformanceResponse
	}
	if len(response.Value) == 0 {
		c.logger.Debug("Received empty performance data array")
		return []PerformanceItem{}, nil
	}
	return response.Value, nil
}
