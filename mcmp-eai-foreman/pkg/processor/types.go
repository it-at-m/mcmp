package processor

type (
	ForemanData struct {
		Hosts []Host `json:"hosts"`
	}

	Host struct {
		ID                        int             `json:"id,omitempty"`
		Source                    *string         `json:"source,omitempty"`
		Name                      string          `json:"name,omitempty"`
		Fqdn                      *string         `json:"fqdn,omitempty"`
		DisplayName               *string         `json:"display_name,omitempty"`
		IP                        *string         `json:"ip,omitempty"`
		Mac                       *string         `json:"mac,omitempty"`
		ArchitectureName          *string         `json:"architecture_name,omitempty"`
		OperatingsystemName       *string         `json:"operatingsystem_name,omitempty"`
		OperatingsystemFamily     *string         `json:"operatingsystem_family,omitempty"`
		OperatingsystemMajor      *string         `json:"operatingsystem_major,omitempty"`
		SubnetName                *string         `json:"subnet_name,omitempty"`
		CreatedAt                 *string         `json:"created_at,omitempty"`
		InitiatedAt               *string         `json:"initiated_at,omitempty"`
		InstalledAt               *string         `json:"installed_at,omitempty"`
		Serialnumber              *string         `json:"serialnumber,omitempty"`
		InstanceUUID              *string         `json:"instance_uuid,omitempty"`
		ComputeResourceName       *string         `json:"compute_resource_name,omitempty"`
		Interfaces                []Interface     `json:"interfaces,omitempty"`
		LhmPnExitcode             *string         `json:"lhm_pn_exitcode,omitempty"`
		LhmPnExitstring           *string         `json:"lhm_pn_exitstring,omitempty"`
		OracleDB                  bool            `json:"oracle_db,omitempty"`
		MssqlDB                   bool            `json:"mssql_db,omitempty"`
		MariaDB                   bool            `json:"maria_db,omitempty"`
		MysqlDB                   bool            `json:"mysql_db,omitempty"`
		MongoDB                   bool            `json:"mongo_db,omitempty"`
		AdabasDB                  bool            `json:"adabas_db,omitempty"`
		PostgresDB                bool            `json:"postgres_db,omitempty"`
		Linux                     bool            `json:"linux,omitempty"`
		Windows                   bool            `json:"windows,omitempty"`
		TetrationAgentIsInstalled bool            `json:"tetration_agent_is_installed,omitempty"`
		ServerInfosOwnerMail      *string         `json:"server_infos_owner_mail,omitempty"`
		ServerInfosTicketnr       *string         `json:"server_infos_ticketnr,omitempty"`
		PatchnightGroup           *string         `json:"patchnight_group,omitempty"`
		PatchnightStartTime       *string         `json:"patchnight_start_time,omitempty"`
		MysqlDBVersion            *string         `json:"mysql_db_version,omitempty"`
		MariaDBVersion            *string         `json:"maria_db_version,omitempty"`
		OracleDBVersion           *string         `json:"oracle_db_version,omitempty"`
		OracleSID                 *string         `json:"oracle_sid,omitempty"`
		Mountpoints               []Mountpoint    `json:"mountpoints,omitempty"`
		Partitions                []Partition     `json:"partitions,omitempty"`
		LogicalVolumes            []LogicalVolume `json:"logical_volumes,omitempty"`
		Repositories              []string        `json:"repositories,omitempty"`
	}

	Interface struct {
		CreatedAt  string  `json:"created_at"`
		DomainName string  `json:"domain_name"`
		Execution  bool    `json:"execution"`
		FQDN       string  `json:"fqdn"`
		ID         int     `json:"id"`
		Identifier *string `json:"identifier"`
		IP         *string `json:"ip"`
		IP6        *string `json:"ip6"`
		Mac        string  `json:"mac"`
		MTU        int     `json:"mtu"`
		Managed    bool    `json:"managed"`
		Name       string  `json:"name"`
		Primary    bool    `json:"primary"`
		Provision  bool    `json:"provision"`
		SubnetName *string `json:"subnet_name"`
		Type       string  `json:"type"`
		UpdatedAt  string  `json:"updated_at"`
		Virtual    bool    `json:"virtual"`
	}

	Mountpoint struct {
		MountPoint     string   `json:"mount_point,omitempty"`
		Filesystem     string   `json:"filesystem,omitempty"`
		Device         string   `json:"device,omitempty"`
		Options        []string `json:"options,omitempty"`
		SizeBytes      int64    `json:"size_bytes,omitempty"`
		UsedBytes      int64    `json:"used_bytes,omitempty"`
		AvailableBytes int64    `json:"available_bytes,omitempty"`
	}

	Partition struct {
		Partition  string `json:"partition,omitempty"`
		MountPoint string `json:"mount_point,omitempty"`
		Filesystem string `json:"filesystem,omitempty"`
		PartType   string `json:"parttype,omitempty"`
		PartUUID   string `json:"partuuid,omitempty"`
		SizeBytes  int64  `json:"size_bytes,omitempty"`
		UUID       string `json:"uuid,omitempty"`
	}

	LogicalVolume struct {
		LogicalVolume string `json:"logical_volume,omitempty"`
		Active        string `json:"active,omitempty"`
		Attr          string `json:"attr,omitempty"`
		DmPath        string `json:"dm_path,omitempty"`
		FullName      string `json:"full_name,omitempty"`
		Layout        string `json:"layout,omitempty"`
		Path          string `json:"path,omitempty"`
		Permissions   string `json:"permissions,omitempty"`
		Role          string `json:"role,omitempty"`
		Size          string `json:"size,omitempty"`
		UUID          string `json:"uuid,omitempty"`
	}
)
