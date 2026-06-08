package processor

import (
	"mcmp-eai-proxmox/pkg/clients/pdm"
	"strconv"
	"strings"
	"time"
)

type (
	ServerKind string
	ServerType string
	PowerState string
)

const (
	ServerKindVirtual    ServerKind = "VIRTUAL"
	ServerTypeProxmox    ServerType = "VM_PROXMOX"
	PowerStatePoweredOn  PowerState = "poweredOn"
	PowerStatePoweredOff PowerState = "poweredOff"
)

// A Server represents a single virtualized server accepted by the MCMP
// backend's cloud import API.
type Server struct {
	ServerKind ServerKind `json:"server_kind,omitempty"` // Must be "VIRTUAL"
	ServerType ServerType `json:"server_type,omitempty"` // Must be "VM_PROXMOX"

	// "UUID" of the server. Doesn't have to be an actual UUID, but is
	// used to determine identity in the backend, so it must be unique
	// for this Cloud instance.
	UUID string `json:"uuid,omitempty"`

	// Numeric ID of the server on the hypervisor.
	VMID string `json:"vm_id,omitempty"`

	// Name of the server on the hypervisor.
	Name string `json:"name,omitempty"`

	// FQDN of the server. This is used to map the server entry to
	// other systems such as Foreman.
	Fqdn string `json:"fqdn,omitempty"`

	Cluster    string     `json:"cluster,omitempty"`     // Cluster the VM is hosted in.
	Host       string     `json:"host,omitempty"`        // Host/Node the VM is hosted on.
	PowerState PowerState `json:"power_state,omitempty"` // Must be "poweredOn" or "poweredOff"
	MemoryMB   uint64     `json:"memory_mb,omitempty"`   // Available memory in MiB.
	NumCpu     uint32     `json:"num_cpu,omitempty"`     // Number of CPUs.
	BootTime   *time.Time `json:"boot_time,omitempty"`   // Time of last boot.
}

// ProcessServer processes a single server based on a pdm.Resource
// record.
func (p *Processor) ProcessServer(vm *pdm.Resource) *Server {
	server := &Server{
		ServerKind: ServerKindVirtual,
		ServerType: ServerTypeProxmox,
	}

	// obviously not a real UUID, but neither are the "UUID"s of
	// UCS servers. this value only really has to be unique within
	// the cloud instance, which it is.
	VMID := strconv.FormatUint(vm.VMID, 10)
	server.UUID = VMID
	server.VMID = VMID

	// assert that every VM should be named after the FQDN of
	// the guest. this should be true for any server installed via
	// the MCMP.
	server.Fqdn = vm.Name

	// ID schema: remote/<remote>/guest/<vmid>
	ps := strings.Split(vm.ID, "/")
	if len(ps) > 1 {
		server.Cluster = ps[1]
	}

	if nodeFQDN, ok := p.nodeFQDNs[vm.Node]; ok {
		server.Host = nodeFQDN
	} else {
		server.Host = vm.Node
	}

	server.NumCpu = uint32(vm.MaxCPU)
	server.MemoryMB = vm.MaxMem / (1024 * 1024) // B->MiB

	bootTime := time.Now().Add(time.Duration(-vm.Uptime) * time.Second)
	server.BootTime = &bootTime

	// impersonate VMware
	switch vm.Status {
	case "running":
		server.PowerState = PowerStatePoweredOn
	case "stopped":
		server.PowerState = PowerStatePoweredOff
	}

	return server
}
