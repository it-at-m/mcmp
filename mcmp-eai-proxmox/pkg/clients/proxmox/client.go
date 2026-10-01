package proxmox

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"mcmp-eai-proxmox/pkg/config"
	"net/http"
	"net/url"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// A Client is an HTTP client for a Proxmox environment.
// It can communicate with both the datacenter manager and connected
// clusters.
type Client struct {
	BaseURL    *url.URL       // Base URL of the PDM instance.
	baseClient *http.Client   // HTTP client for PDM.
	logger     logging.Logger // A logger for request logging.
}

// NewClient creates a new Client.
func NewClient(cfg config.ProxmoxConfig, logger logging.Logger) (*Client, error) {
	client := &Client{logger: logger}

	baseURL, err := url.Parse(cfg.URL)
	if err != nil {
		return nil, fmt.Errorf("failed to parse PDM url: %v", err)
	}

	tp := http.DefaultTransport.(*http.Transport).Clone()
	tp.MaxConnsPerHost = cfg.MaxConns
	tp.TLSClientConfig.InsecureSkipVerify = cfg.InsecureSkipVerify
	auth := fmt.Sprintf("PDMAPIToken=%s:%s", cfg.APITokenID, cfg.APITokenSecret)

	client.BaseURL = baseURL
	client.baseClient = &http.Client{Transport: &authorizedTransport{tp, auth}}

	return client, nil
}

// GetJSON fetches and unmarshalls JSON data from an endpoint.
//
// It is assumed that the server responds with a 200 status code
// and a JSON payload, otherwise this function will fail and return
// an error. If unmarshalling the payload is successful, v will contain
// the deserialized data.
func (client *Client) GetJSON(ctx context.Context, URL *url.URL, v any) error {
	req, err := http.NewRequestWithContext(ctx, "GET", URL.String(), nil)
	if err != nil {
		return fmt.Errorf("failed to create request %s: %v", URL.String(), err)
	}

	client.logger.Debug("GET:", "url", URL.String())

	resp, err := client.baseClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to fetch %s: %v", URL.String(), err)
	}

	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		if body, err := io.ReadAll(resp.Body); err != nil {
			return fmt.Errorf("failed to fetch %s (status %s): %s", URL.String(), resp.Status, body)
		}

		return fmt.Errorf("failed to fetch %s (status %s) (no body)", URL.String(), resp.Status)
	}

	client.logger.Debug("RESPONSE:", "url", URL.String(), "status", resp.Status)

	if err := json.NewDecoder(resp.Body).Decode(v); err != nil {
		return fmt.Errorf("failed to decode response %s: %v", URL.String(), err)
	}

	return nil
}
