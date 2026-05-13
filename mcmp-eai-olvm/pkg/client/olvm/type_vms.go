package olvm

type VMsResponse struct {
	Vm []VM `json:"vm"`
}

type VM struct {
	FQDN                         string `json:"fqdn"`
	Name                         string `json:"name"`
	Description                  string `json:"description"`
	Comment                      string `json:"comment"`
	Status                       string `json:"status"`
	Type                         string `json:"type"`
	Origin                       string `json:"origin"`
	Stateless                    string `json:"stateless"`
	RunOnce                      string `json:"run_once"`
	NextRunConfigurationExists   string `json:"next_run_configuration_exists"`
	StartTime                    int64  `json:"start_time"`
	StopTime                     int64  `json:"stop_time"`
	CreationTime                 int64  `json:"creation_time"`
	Memory                       string `json:"memory"`
	AutoPinningPolicy            string `json:"auto_pinning_policy"`
	CPUPinningPolicy             string `json:"cpu_pinning_policy"`
	CPUShares                    string `json:"cpu_shares"`
	DeleteProtected              string `json:"delete_protected"`
	MigrationDowntime            string `json:"migration_downtime"`
	MultiQueuesEnabled           string `json:"multi_queues_enabled"`
	StartPaused                  string `json:"start_paused"`
	StorageErrorResumeBehaviour  string `json:"storage_error_resume_behaviour"`
	VirtioSCSIMultiQueuesEnabled string `json:"virtio_scsi_multi_queues_enabled"`

	GuestOperatingSystem GuestOperatingSystem `json:"guest_operating_system"`
	GuestTimeZone        GuestTimeZone        `json:"guest_time_zone"`
	Host                 Ref                  `json:"host"`
	OriginalTemplate     *Ref                 `json:"original_template,omitempty"`
	Template             *Ref                 `json:"template,omitempty"`
	Actions              Actions              `json:"actions"`
	Bios                 Bios                 `json:"bios"`
	CPU                  CPU                  `json:"cpu"`
	Display              Display              `json:"display"`
	IO                   IO                   `json:"io"`
	Lease                Lease                `json:"lease"`
	Migration            Migration            `json:"migration"`
	OS                   OS                   `json:"os"`
	SSO                  SSO                  `json:"sso"`
	USB                  USB                  `json:"usb"`
	Cluster              Ref                  `json:"cluster"`
	Quota                RefID                `json:"quota"`
	Link                 []Link               `json:"link"`
	Href                 string               `json:"href"`
	ID                   string               `json:"id"`
	HighAvailability     HighAvailability     `json:"high_availability"`
	LargeIcon            *Ref                 `json:"large_icon,omitempty"`
	SmallIcon            *Ref                 `json:"small_icon,omitempty"`
	MemoryPolicy         MemoryPolicy         `json:"memory_policy"`
	PlacementPolicy      PlacementPolicy      `json:"placement_policy"`
	TimeZone             TimeZone             `json:"time_zone"`
	CPUProfile           Ref                  `json:"cpu_profile"`
}

type GuestOperatingSystem struct {
	Architecture string    `json:"architecture"`
	Codename     string    `json:"codename"`
	Distribution string    `json:"distribution"`
	Family       string    `json:"family"`
	Kernel       Kernel    `json:"kernel"`
	Version      OSVersion `json:"version"`
}

type Kernel struct {
	Version KernelVersion `json:"version"`
}

type KernelVersion struct {
	Build       string `json:"build"`
	FullVersion string `json:"full_version"`
	Major       string `json:"major"`
	Minor       string `json:"minor"`
	Revision    string `json:"revision"`
}

type OSVersion struct {
	FullVersion string `json:"full_version"`
	Major       string `json:"major"`
	Minor       string `json:"minor"`
}

type GuestTimeZone struct {
	Name      string `json:"name"`
	UTCOffset string `json:"utc_offset"`
}

type Ref struct {
	Href string `json:"href"`
	ID   string `json:"id"`
}

type RefID struct {
	ID string `json:"id"`
}

type Actions struct {
	Link []Link `json:"link"`
}

type Link struct {
	Href string `json:"href"`
	Rel  string `json:"rel"`
}

type Bios struct {
	BootMenu struct {
		Enabled string `json:"enabled"`
	} `json:"boot_menu"`
	Type string `json:"type"`
}

type CPU struct {
	Architecture string `json:"architecture"`
	Topology     struct {
		Cores   string `json:"cores"`
		Sockets string `json:"sockets"`
		Threads string `json:"threads"`
	} `json:"topology"`
	CPUTune *CPUTune `json:"cpu_tune,omitempty"`
}

type CPUTune struct {
	VCPUPins struct {
		VCPUPin []VCPUPin `json:"vcpu_pin"`
	} `json:"vcpu_pins"`
}

type VCPUPin struct {
	CPUSet string `json:"cpu_set"`
	VCPU   string `json:"vcpu"`
}

type Display struct {
	Address             string `json:"address"`
	AllowOverride       string `json:"allow_override"`
	CopyPasteEnabled    string `json:"copy_paste_enabled"`
	DisconnectAction    string `json:"disconnect_action"`
	DisconnectDelay     string `json:"disconnect_action_delay"`
	FileTransferEnabled string `json:"file_transfer_enabled"`
	KeyboardLayout      string `json:"keyboard_layout"`
	Monitors            string `json:"monitors"`
	Port                string `json:"port"`
	SmartcardEnabled    string `json:"smartcard_enabled"`
	Type                string `json:"type"`
	Certificate         struct {
		Content      string `json:"content"`
		Organization string `json:"organization"`
		Subject      string `json:"subject"`
	} `json:"certificate"`
}

type IO struct {
	Threads string `json:"threads"`
}

type Lease struct {
	StorageDomain Ref `json:"storage_domain"`
}

type Migration struct {
	AutoConverge             string `json:"auto_converge"`
	Compressed               string `json:"compressed"`
	Encrypted                string `json:"encrypted"`
	ParallelMigrationsPolicy string `json:"parallel_migrations_policy"`
}

type OS struct {
	Boot struct {
		Devices struct {
			Device []string `json:"device"`
		} `json:"devices"`
	} `json:"boot"`
	Type string `json:"type"`
}

type SSO struct {
	Methods map[string]any `json:"methods"`
}

type USB struct {
	Enabled string `json:"enabled"`
}

type HighAvailability struct {
	Enabled  string `json:"enabled"`
	Priority string `json:"priority"`
}

type MemoryPolicy struct {
	Ballooning string `json:"ballooning"`
	Guaranteed string `json:"guaranteed"`
	Max        string `json:"max"`
}

type PlacementPolicy struct {
	Affinity string `json:"affinity"`
	Hosts    *struct {
		Host []Ref `json:"host"`
	} `json:"hosts,omitempty"`
}

type TimeZone struct {
	Name string `json:"name"`
}
