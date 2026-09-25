package proxmox

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
)

// VMConfig contains (selected values of) the configuration of a QEMU VM.
type VMConfig struct {
	Cores   uint32         `json:"cores"`   // Number of cores per socket.
	Hotplug string         `json:"hotplug"` // Enabled hotplug features.
	OSType  string         `json:"ostype"`  // Guest operating system type.
	Agent   Agent          `json:"agent"`   // Guest Agent configuration.
	SMBIOS1 SMBIOS1        `json:"smbios1"` // SMBIOS type 1 fields.
	Nets    map[string]Net `json:"-"`       // Network devices, mapped by their name.
}

type SMBIOS1 struct{ UUID string }
type Agent struct{ Enabled bool }

type Net struct {
	Bridge     string // Bridge the device is attached to (can be a vlan)
	Model      string // Model of the network device (e.g. virtio)
	MacAddress string // MAC address of the device.
	LinkDown   bool   // If the device is disconnected.
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
			result.Nets[name] = net(data)
		}
	}

	return nil
}

func net(data string) Net {
	_, attrs := ParseCommaSeparatedMap(data)

	result := Net{}
	result.Bridge = attrs["bridge"]
	// result.MacAddress = attrs["macaddr"] // documented, but not used?

	for _, model := range []string{"e1000", "e1000e", "virtio", "rtl8139", "vmxnet3"} {
		if mac, ok := attrs[model]; ok {
			result.Model = model
			result.MacAddress = mac
		}
	}

	return result
}
