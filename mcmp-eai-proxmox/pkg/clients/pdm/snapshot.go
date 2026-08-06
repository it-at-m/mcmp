package pdm

import (
	"context"
	"strconv"
)

type Snapshot struct {
	Name        string `json:"name"`        // Name of the snapshot.
	Description string `json:"description"` // Description of the snapshot.
	VMState     bool   `json:"vmstate"`     // If the snapshot includes RAM.
	Snaptime    int64  `json:"snaptime"`    // Creation time (UNIX epoch).
}

// Snapshots fetches the snapshots for the VM.
func (client *Client) Snapshots(ctx context.Context, remote string, vmid uint64) ([]Snapshot, error) {
	URL := client.BaseURL.JoinPath("pve", "remotes", remote, "qemu", strconv.FormatUint(vmid, 10), "snapshot")

	var result struct {
		Data []Snapshot `json:"data"`
	}

	if err := client.GetJSON(ctx, URL, &result); err != nil {
		return nil, err
	}

	return result.Data, nil
}
