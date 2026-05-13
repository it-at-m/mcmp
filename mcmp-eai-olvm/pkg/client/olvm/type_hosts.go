package olvm

type HostsResponse struct {
	Host []Host `json:"host"`
}

type Host struct {
	Address                string               `json:"address"`
	AutoNumaStatus         string               `json:"auto_numa_status"`
	Certificate            Certificate          `json:"certificate"`
	Cpu                    CpuHost              `json:"cpu"`
	DevicePassthrough      DevicePassthrough    `json:"device_passthrough"`
	ExternalStatus         string               `json:"external_status"`
	HardwareInformation    HardwareInformation  `json:"hardware_information"`
	Iscsi                  Iscsi                `json:"iscsi"`
	KdumpStatus            string               `json:"kdump_status"`
	Ksm                    Ksm                  `json:"ksm"`
	LibvirtVersion         VersionInfo          `json:"libvirt_version"`
	MaxSchedulingMemory    string               `json:"max_scheduling_memory"`
	Memory                 string               `json:"memory"`
	NumaSupported          string               `json:"numa_supported"`
	Os                     OsHost               `json:"os"`
	OvnConfigured          string               `json:"ovn_configured"`
	Port                   string               `json:"port"`
	PowerManagement        PowerManagement      `json:"power_management"`
	Protocol               string               `json:"protocol"`
	ReinstallationRequired string               `json:"reinstallation_required"`
	SeLinux                SeLinux              `json:"se_linux"`
	Spm                    Spm                  `json:"spm"`
	Ssh                    Ssh                  `json:"ssh"`
	Status                 string               `json:"status"`
	Summary                Summary              `json:"summary"`
	TransparentHugepages   TransparentHugepages `json:"transparent_hugepages"`
	Type                   string               `json:"type"`
	UpdateAvailable        string               `json:"update_available"`
	Version                VersionInfo          `json:"version"`
	VgpuPlacement          string               `json:"vgpu_placement"`
	Cluster                Ref                  `json:"cluster"`
	Actions                Actions              `json:"actions"`
	Name                   string               `json:"name"`
	Comment                string               `json:"comment"`
	Link                   []Link               `json:"link"`
	Href                   string               `json:"href"`
	Id                     string               `json:"id"`
}

type Certificate struct {
	Organization string `json:"organization"`
	Subject      string `json:"subject"`
}

type CpuHost struct {
	Name     string   `json:"name"`
	Speed    int      `json:"speed"`
	Topology Topology `json:"topology"`
	Type     string   `json:"type"`
}

type Topology struct {
	Cores   string `json:"cores"`
	Sockets string `json:"sockets"`
	Threads string `json:"threads"`
}

type DevicePassthrough struct {
	Enabled string `json:"enabled"`
}

type HardwareInformation struct {
	Family              string              `json:"family"`
	Manufacturer        string              `json:"manufacturer"`
	ProductName         string              `json:"product_name"`
	SerialNumber        string              `json:"serial_number"`
	SupportedRngSources SupportedRngSources `json:"supported_rng_sources"`
	Uuid                string              `json:"uuid"`
	Version             string              `json:"version"`
}

type SupportedRngSources struct {
	SupportedRngSource []string `json:"supported_rng_source"`
}

type Iscsi struct {
	Initiator string `json:"initiator"`
}

type VersionInfo struct {
	Build       string `json:"build"`
	FullVersion string `json:"full_version"`
	Major       string `json:"major"`
	Minor       string `json:"minor"`
	Revision    string `json:"revision"`
}

type OsHost struct {
	CustomKernelCmdline   string    `json:"custom_kernel_cmdline"`
	ReportedKernelCmdline string    `json:"reported_kernel_cmdline"`
	Type                  string    `json:"type"`
	Version               OsVersion `json:"version"`
}

type OsVersion struct {
	FullVersion string `json:"full_version"`
	Major       string `json:"major"`
	Minor       string `json:"minor"`
}

type PowerManagement struct {
	AutomaticPmEnabled string    `json:"automatic_pm_enabled"`
	Enabled            string    `json:"enabled"`
	KdumpDetection     string    `json:"kdump_detection"`
	PmProxies          PmProxies `json:"pm_proxies"`
}

type PmProxies struct {
	PmProxy []PmProxy `json:"pm_proxy"`
}

type PmProxy struct {
	Type string `json:"type"`
}

type SeLinux struct {
	Mode string `json:"mode"`
}

type Spm struct {
	Priority string `json:"priority"`
	Status   string `json:"status"`
}

type Ssh struct {
	Fingerprint string `json:"fingerprint"`
	Port        string `json:"port"`
	PublicKey   string `json:"public_key"`
}

type Summary struct {
	Active    string `json:"active"`
	Migrating string `json:"migrating"`
	Total     string `json:"total"`
}
