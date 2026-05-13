package processor

type CloudType string

const (
	CloudTypeVcenter    CloudType = "VCENTER"
	CloudTypeProxmox    CloudType = "PROXMOX"
	CloudTypeUcsManager CloudType = "UCS_MANAGER"
	CloudTypeUcsCimc    CloudType = "UCS_CIMC"
)
