package processor

import (
	"slices"
	"strconv"

	"mcmp-eai-proxmox/pkg/clients/proxmox"
)

// A PortGroup that can be imported into the MCMP.
type PortGroup struct {
	PortGroupKey string `json:"port_group_key"`
	Name         string `json:"name"`
	VLAN         string `json:"vlan"`
}

// ProcessPortGroups processes a list of VNets into PortGroups
// while skipping duplicates from different remotes.
func (p *Processor) ProcessPortGroups(vnets []*proxmox.VNet) []*PortGroup {
	var portGroups []*PortGroup
	var processed []string
	for _, vnet := range vnets {
		if slices.Contains(processed, vnet.VNet) {
			continue
		}

		processed = append(processed, vnet.VNet)
		portGroups = append(portGroups, p.ProcessPortGroup(vnet))
	}

	return portGroups
}

// ProcessPortGroup processes a single VNet into a PortGroup.
func (p *Processor) ProcessPortGroup(vnet *proxmox.VNet) *PortGroup {
	return &PortGroup{
		PortGroupKey: vnet.VNet,
		Name:         vnet.Alias,
		VLAN:         strconv.FormatUint(uint64(vnet.Tag), 10),
	}
}
