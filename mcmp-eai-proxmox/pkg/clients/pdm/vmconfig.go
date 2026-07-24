package pdm

import (
	"context"
	"net/url"
	"strconv"
)

// VMConfig contains (selected values of) the configuration of a QEMU VM.
type VMConfig struct {
	Cores   uint32 `json:"cores"`   // Number of cores per socket.
	Hotplug string `json:"hotplug"` // Enabled hotplug features.
	OSType  string `json:"ostype"`  // Guest operating system type.
	SMBIOS1 string `json:"smbios1"` // SMBIOS type 1 fields.
}

// VMConfig fetches the pending Virtual Machine Config for a VM.
func (client *Client) VMConfig(ctx context.Context, remote string, vmid uint64) (*VMConfig, error) {
	URL := client.BaseURL.JoinPath("pve", "remotes", remote, "qemu", strconv.FormatUint(vmid, 10), "config")
	URL.RawQuery = url.Values{"state": {"pending"}}.Encode()

	var result struct {
		Data *VMConfig `json:"data"`
	}

	if err := client.GetJSON(ctx, URL, &result); err != nil {
		return nil, err
	}

	return result.Data, nil
}
