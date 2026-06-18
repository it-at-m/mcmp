package processor

import "github.com/it-at-m/mcmp/mcmp-eai-loadbalancer/pkg/client/loadbalancer"

// LoadBalancerData is the payload sent to the MCMP backend.
type LoadBalancerData struct {
	VirtualServers map[string]loadbalancer.VirtualServerConfig `json:"virtualServers"`
	Pools          map[string]loadbalancer.PoolConfig          `json:"pools"`
}
