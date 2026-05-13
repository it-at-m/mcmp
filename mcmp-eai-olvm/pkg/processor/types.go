package processor

type Cloud struct {
	Cloud     string    `json:"cloud"`
	CloudType CloudType `json:"cloud_type"`
	Servers   []Server  `json:"servers"`
}

type Server struct {
	ServerKind                  ServerKind `json:"server_kind"`
	ServerType                  ServerType `json:"server_type"`
	Name                        *string    `json:"name,omitempty"`
	UUID                        *string    `json:"uuid,omitempty"`
	InstanceUUID                *string    `json:"instance_uuid,omitempty"`
	VMID                        *string    `json:"vm_id,omitempty"`
	Cluster                     *string    `json:"cluster,omitempty"`
	Host                        *string    `json:"host,omitempty"`
	Location                    *string    `json:"location,omitempty"`
	PowerState                  *string    `json:"power_state,omitempty"`
	MemoryMB                    *uint64    `json:"memory_mb,omitempty"`
	NumCPU                      *uint      `json:"num_cpu,omitempty"`
	NumCoresPerSocket           *uint      `json:"num_cores_per_socket,omitempty"`
	NumOfThreads                *uint      `json:"num_of_threads,omitempty"`
	MemoryHotAddEnabled         *bool      `json:"memory_hot_add_enabled,omitempty"`
	CpuHotAddEnabled            *bool      `json:"cpu_hot_add_enabled,omitempty"`
	CpuHotRemoveEnabled         *bool      `json:"cpu_hot_remove_enabled,omitempty"`
	CpuTopology                 *string    `json:"cpu_topology,omitempty"`
	VMXVersion                  *string    `json:"vmx_version,omitempty"`
	OverallStatus               *string    `json:"overall_status,omitempty"`
	ConfigStatus                *string    `json:"config_status,omitempty"`
	GuestConfigID               *string    `json:"guest_config_id,omitempty"`
	GuestConfigFullName         *string    `json:"guest_config_full_name,omitempty"`
	GuestToolsID                *string    `json:"guest_tools_id,omitempty"`
	GuestToolsFullName          *string    `json:"guest_tools_full_name,omitempty"`
	GuestToolsState             *string    `json:"guest_tools_state,omitempty"`
	GuestToolsRunningStatus     *string    `json:"guest_tools_running_status,omitempty"`
	GuestToolsVersionStatus     *string    `json:"guest_tools_version_status,omitempty"`
	GuestToolsVersionStatus2    *string    `json:"guest_tools_version_status2,omitempty"`
	GuestToolsInstallType       *string    `json:"guest_tools_install_type,omitempty"`
	GuestToolsVersion           *string    `json:"guest_tools_version,omitempty"`
	GuestToolsFamily            *string    `json:"guest_tools_family,omitempty"`
	GuestToolsHostname          *string    `json:"guest_tools_hostname,omitempty"`
	GuestToolsIPAddress         *string    `json:"guest_tools_ip_address,omitempty"`
	GuestToolsArchitecture      *string    `json:"guest_tools_architecture,omitempty"`
	GuestToolsBitness           *string    `json:"guest_tools_bitness,omitempty"`
	GuestToolsBuildNumber       *string    `json:"guest_tools_build_number,omitempty"`
	GuestToolsCpeString         *string    `json:"guest_tools_cpe_string,omitempty"`
	GuestToolsDistroAddlVersion *string    `json:"guest_tools_distro_addl_version,omitempty"`
	GuestToolsDistroName        *string    `json:"guest_tools_distro_name,omitempty"`
	GuestToolsDistroVersion     *string    `json:"guest_tools_distro_version,omitempty"`
	GuestToolsFamilyName        *string    `json:"guest_tools_family_name,omitempty"`
	GuestToolsKernelVersion     *string    `json:"guest_tools_kernel_version,omitempty"`
	GuestToolsPrettyName        *string    `json:"guest_tools_pretty_name,omitempty"`
	BootTime                    *string    `json:"boot_time,omitempty"`
	HotPlugMemoryLimit          *uint64    `json:"hot_plug_memory_limit,omitempty"`
	HotPlugMemoryIncrementSize  *uint64    `json:"hot_plug_memory_increment_size,omitempty"`
	DN                          *string    `json:"dn,omitempty"`
	Association                 *string    `json:"association,omitempty"`
	MemorySpeed                 *uint      `json:"memory_speed,omitempty"`
	MfgTime                     *string    `json:"mfg_time,omitempty"`
	Model                       *string    `json:"model,omitempty"`
	NumOfAdaptors               *uint      `json:"num_of_adaptors,omitempty"`
	NumOfCoresEnabled           *uint      `json:"num_of_cores_enabled,omitempty"`
	NumOfEthHostIfs             *uint      `json:"num_of_eth_host_ifs,omitempty"`
	NumOfFcHostIfs              *uint      `json:"num_of_fc_host_ifs,omitempty"`
	OperState                   *string    `json:"oper_state,omitempty"`
	ChassisId                   *uint      `json:"ucsm_chassis_id,omitempty"`
	SlotId                      *uint      `json:"ucsm_chassis_slot_id,omitempty"`
	ServerId                    *uint      `json:"ucsm_server_id,omitempty"`
	AvailableMemory             *uint64    `json:"available_memory,omitempty"`
	Vendor                      *string    `json:"vendor,omitempty"`
	Vid                         *string    `json:"vid,omitempty"`
}
