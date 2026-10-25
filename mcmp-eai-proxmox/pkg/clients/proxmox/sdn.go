package proxmox

import (
	"context"
)

// A VNet object.
// Some irrelevant fields have been ommitted from this struct.
type VNet struct {
	Alias  string `json:"alias"`  // Alias name of the VNet (Portgroup).
	Remote string `json:"remote"` // ID of the remote containing this VNet.
	Tag    uint32 `json:"tag"`    // VLAN Tag.
	Type   string `json:"type"`   // Type of this record (always "vnet").
	VNet   string `json:"vnet"`   // Name of this record.
	Zone   string `json:"zone"`   // Zone this VNet belongs to.
}

// VNets fetches all VNets known to the Proxmox Datacenter Manager.
func (client *Client) VNets(ctx context.Context) ([]*VNet, error) {
	URL := client.BaseURL.JoinPath("sdn", "vnets")

	var result struct {
		Data []*VNet `json:"data"`
	}

	if err := client.GetJSON(ctx, URL, &result); err != nil {
		return nil, err
	}

	return result.Data, nil
}
