package processor

import (
	"mcmp-eai-proxmox/pkg/clients/proxmox"
)

// A Nic object to save to the MCMP database.
type Nic struct {
	VNicKey    uint32 `json:"vnic_key"`
	Device     string `json:"device,omitempty"`
	MacAddress string `json:"mac_address,omitempty"`
	Network    string `json:"network,omitempty"`
	Connected  bool   `json:"connected,omitempty"`
	CardType   string `json:"card_type,omitempty"`
}

// processNics processes all Nics from the given VMConfig data.
func (p *Processor) processNics(cfg *proxmox.VMConfig) []*Nic {
	result := make([]*Nic, 0, len(cfg.Nets))

	for name, net := range cfg.Nets {
		result = append(result, &Nic{
			VNicKey:    uint32(net.Seq),
			Device:     name,
			MacAddress: net.MacAddress,
			Network:    net.Bridge,
			Connected:  !net.LinkDown,
			CardType:   net.Model,
		})
	}

	return result
}
