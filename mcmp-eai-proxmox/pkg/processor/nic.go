package processor

import (
	"fmt"
	"mcmp-eai-proxmox/pkg/clients/pdm"
	"regexp"
	"strconv"
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

// processNics processes all Nics from the given VMConfig data and
// writes their data to the given Server object.
func (p *Processor) processNics(cfg *pdm.VMConfig, server *Server) error {
	server.Nics = make([]*Nic, 0, len(cfg.Nets))

	for name, net := range cfg.Nets {
		seq, err := trailingNumber(name)
		if err != nil {
			return err
		}

		server.Nics = append(server.Nics, &Nic{
			VNicKey:    uint32(seq),
			Device:     name,
			MacAddress: net.MacAddress,
			Network:    net.Bridge,
			Connected:  !net.LinkDown,
			CardType:   net.Model,
		})
	}

	return nil
}

var trailingNumberRegexp = regexp.MustCompile("([0-9]+)$")

// Retrieve a trailing number from a string.
func trailingNumber(s string) (uint64, error) {
	matches := trailingNumberRegexp.FindStringSubmatch(s)
	if len(matches) < 2 {
		return 0, fmt.Errorf("missing trailing number in: %s", s)
	}
	return strconv.ParseUint(matches[1], 10, 64)
}
