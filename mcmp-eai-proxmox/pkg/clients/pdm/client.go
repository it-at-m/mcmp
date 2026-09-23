package pdm

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
	BaseURL        *url.URL                // Base URL of the PDM instance.
	baseClients    *http.Client            // HTTP client for PDM.
	ClusterURLs    map[string]*url.URL     // Base URLs of the clusters, mapped by cluster ID.
	clusterClients map[string]*http.Client // HTTP clients for the clusters, mapped by hostname.
	logger         logging.Logger          // A logger for request logging.
}

// NewClient creates a new Client.
func NewClient(cfg config.DatacenterConfig, logger logging.Logger) (*Client, error) {
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
	client.baseClients = &http.Client{Transport: &authorizedTransport{tp, auth}}

	// construct PVE clients
	client.ClusterURLs = make(map[string]*url.URL, len(cfg.CLUSTER))
	client.clusterClients = make(map[string]*http.Client, len(cfg.CLUSTER))
	for _, cfg := range cfg.CLUSTER {
		baseURL, err := url.Parse(cfg.URL)
		if err != nil {
			return nil, fmt.Errorf("failed to parse cluster url: %v", err)
		}

		tp := http.DefaultTransport.(*http.Transport).Clone()
		tp.MaxConnsPerHost = cfg.MaxConns
		tp.TLSClientConfig.InsecureSkipVerify = cfg.InsecureSkipVerify
		auth := fmt.Sprintf("PVEAPIToken=%s=%s", cfg.APITokenID, cfg.APITokenSecret)

		client.ClusterURLs[cfg.Cluster] = baseURL
		client.clusterClients[baseURL.Hostname()] = &http.Client{Transport: &authorizedTransport{tp, auth}}
	}

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

	subclient, ok := client.selectHttpClient(URL)
	if !ok {
		return fmt.Errorf("no client for host: %s", URL.Hostname())
	}

	resp, err := subclient.Do(req)
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

// ClusterConfigured checks if a cluster of the given name is
// configured for direct connections, as required for guest agent
// endpoints.
func (client *Client) ClusterConfigured(cluster string) bool {
	_, ok := client.ClusterURLs[cluster]
	return ok
}

func (client *Client) selectHttpClient(URL *url.URL) (*http.Client, bool) {
	if URL.Hostname() == client.BaseURL.Hostname() {
		return client.baseClients, true
	}

	subclient, ok := client.clusterClients[URL.Hostname()]
	return subclient, ok
}
