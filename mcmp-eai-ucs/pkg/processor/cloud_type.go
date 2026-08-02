package processor

type CloudType string

const (
	CloudTypeVmware     CloudType = "VMWARE"
	CloudTypeProxmox    CloudType = "PROXMOX"
	CloudTypeUcsManager CloudType = "UCS_MANAGER"
	CloudTypeUcsCimc    CloudType = "UCS_CIMC"
)
