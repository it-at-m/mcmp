package pdm

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"

	"mcmp-eai-proxmox/pkg/config"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// A Client is an HTTP client for a Proxmox Datacenter Manager.
type Client struct {
	BaseURL    *url.URL       // Base of the PDM API URL, including port and /api2/json.
	httpclient *http.Client   // Underlying Go HTTP client.
	logger     logging.Logger // EAI Logger for request logging.
}

// NewClient creates a new Client.
func NewClient(cfg config.DatacenterConfig, logger logging.Logger) (*Client, error) {
	baseURL, err := url.Parse(cfg.URL)
	if err != nil {
		return nil, fmt.Errorf("failed to parse PDM url: %v", err)
	}

	tp := http.DefaultTransport.(*http.Transport).Clone()
	tp.MaxConnsPerHost = cfg.MaxConns
	tp.TLSClientConfig.InsecureSkipVerify = cfg.InsecureSkipVerify

	auth := fmt.Sprintf("PDMAPIToken=%s:%s", cfg.APITokenID, cfg.APITokenSecret)

	client := &http.Client{Transport: &authorizedTransport{tp, auth}}

	return &Client{baseURL, client, logger}, nil
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

	resp, err := client.httpclient.Do(req)
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
