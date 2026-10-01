package proxmox

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
)

type (
	// VMConfig contains (selected values of) the configuration of a QEMU VM.
	VMConfig struct {
		Cores   uint32          `json:"cores"`   // Number of cores per socket.
		Hotplug string          `json:"hotplug"` // Enabled hotplug features.
		OSType  string          `json:"ostype"`  // Guest operating system type.
		Agent   Agent           `json:"agent"`   // Guest Agent configuration.
		SMBIOS1 SMBIOS1         `json:"smbios1"` // SMBIOS type 1 fields.
		Nets    map[string]Net  `json:"-"`       // Network devices, mapped by their name.
		Disks   map[string]Disk `json:"-"`       // Storage devices, mapped by their name.
	}

	SMBIOS1 struct{ UUID string }
	Agent   struct{ Enabled bool }

	Net struct {
		Bridge     string // Bridge the device is attached to (can be a vlan)
		Model      string // Model of the network device (e.g. virtio)
		MacAddress string // MAC address of the device.
		LinkDown   bool   // If the device is disconnected.
		Seq        int    // Sequential network number.
	}

	Disk struct {
		File string // Name of the backing file on the storage.
		Size string // Size of the disk (with unit).
		Bus  string // Bus type.
		Seq  int    // Sequential bus number.
	}
)

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

func (result *VMConfig) UnmarshalJSON(b []byte) error {
	var values map[string]any
	if err := json.Unmarshal(b, &values); err != nil {
		return err
	}

	*result = VMConfig{
		Cores:   uint32(GetUnwrap[float64](values, "cores")),
		Hotplug: GetUnwrap[string](values, "hotplug"),
		OSType:  GetUnwrap[string](values, "ostype"),
	}

	if smbios1, ok := Get[string](values, "smbios1"); ok {
		uuid, attrs := ParseCommaSeparatedMap(smbios1)
		if uuid == "" {
			uuid = attrs["uuid"]
		}
		result.SMBIOS1.UUID = uuid
	}

	if agent, ok := Get[string](values, "agent"); ok {
		enabled, attrs := ParseCommaSeparatedMap(agent)
		if enabled == "" {
			enabled = attrs["enabled"]
		}
		result.Agent.Enabled = enabled == "1"
	}

	result.Nets = make(map[string]Net)
	for i := 0; i < 30; i++ {
		name := fmt.Sprintf("net%d", i)
		if data, ok := Get[string](values, name); ok {
			_, attrs := ParseCommaSeparatedMap(data)
			var model string
			var macAddress = attrs["macaddr"] // documented, but not used?
			for _, mod := range []string{"e1000", "e1000e", "virtio", "rtl8139", "vmxnet3"} {
				if mac, ok := attrs[mod]; ok {
					model = mod
					macAddress = mac
				}
			}

			result.Nets[name] = Net{
				Bridge:     attrs["bridge"],
				Model:      model,
				MacAddress: macAddress,
				LinkDown:   attrs["link_down"] == "1",
				Seq:        i,
			}
		}
	}

	result.Disks = make(map[string]Disk)
	for _, bus := range []string{"ide", "sata", "scsi", "virtio"} {
		for i := 0; i < 30; i++ {
			name := fmt.Sprintf("%s%d", bus, i)
			if data, ok := Get[string](values, name); ok {
				file, attrs := ParseCommaSeparatedMap(data)
				if file == "" {
					file = attrs["file"]
				}

				result.Disks[name] = Disk{
					File: file,
					Size: attrs["size"],
					Bus:  bus,
					Seq:  i,
				}
			}
		}
	}

	return nil
}
