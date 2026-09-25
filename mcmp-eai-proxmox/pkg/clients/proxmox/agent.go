package proxmox

import (
	"context"
	"fmt"
	"strconv"
)

// Filesystem data gathered by the QEMU Guest Agent.
type Filesystem struct {
	Name                 string `json:"name"`
	UsedBytes            uint64 `json:"used-bytes"`
	TotalBytes           uint64 `json:"total-bytes"`
	TotalBytesPrivileged uint64 `json:"total-bytes-privileged"`
	Mountpoint           string `json:"mountpoint"`
	Type                 string `json:"type"`
}

// Filesystems fetches filesystem data for a VM using the QEMU Guest
// Agent.
func (client *Client) Filesystems(ctx context.Context, cluster string, node string, vmid uint64) ([]*Filesystem, error) {
	URL, ok := client.ClusterURLs[cluster]
	if !ok {
		return nil, fmt.Errorf("cluster %s was not configured", cluster)
	}

	URL = URL.JoinPath(
		"nodes", node,
		"qemu", strconv.FormatUint(vmid, 10),
		"agent", "get-fsinfo",
	)

	var result struct {
		Data struct {
			Result []*Filesystem `json:"result"`
		} `json:"data"`
	}

	if err := client.GetJSON(ctx, URL, &result); err != nil {
		return nil, err
	}

	return result.Data.Result, nil
}
