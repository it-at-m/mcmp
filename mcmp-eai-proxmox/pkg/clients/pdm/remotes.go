package pdm

import "context"

// A Remote is a Proxmox Virtual Environment or Proxmox Backup Server
// cluster known to Proxmox Datacenter Manager.
type Remote struct {
	ID    string   `json:"id"`    // PDM Identifier of the Remote.
	Type  string   `json:"type"`  // Type of the Remote.
	Nodes []string `json:"nodes"` // Nodes on the Remote.
}

// Remotes fetches Remote data from Proxmox Datacenter Manager.
func (client *Client) Remotes(ctx context.Context) ([]*Remote, error) {
	URL := client.BaseURL.JoinPath("remotes", "remote")

	var result struct {
		Data []*Remote `json:"data"`
	}

	if err := client.GetJSON(ctx, URL, &result); err != nil {
		return nil, err
	}

	return result.Data, nil
}
