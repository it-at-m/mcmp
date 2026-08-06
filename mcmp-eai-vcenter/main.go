package main

import (
	"errors"
	"flag"
	"fmt"
	"log"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	cfg "github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/lock"
	"github.com/it-at-m/mcmp/mcmp-eai-vcenter/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-vcenter/pkg/cipher"
	"github.com/it-at-m/mcmp/mcmp-eai-vcenter/pkg/clients/vcenter"
	"github.com/it-at-m/mcmp/mcmp-eai-vcenter/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-vcenter/pkg/db"
	"github.com/vmware/govmomi/vim25/mo"
	"github.com/vmware/govmomi/vim25/types"
	"gorm.io/gorm"
)

var debug = false

const (
	appname                                    = "mcmp-eai-vcenter"
	ident                                      = "vcenter"
	Green                               Status = "green"  // Healthy → There are no issues with the virtual machine.
	Yellow                              Status = "yellow" // Warning → There is a potential issue or warning.
	Red                                 Status = "red"    // Critical → There is a serious issue with the VM.
	Gray                                Status = "gray"   // Unknown or invalid → There is insufficient information available.
	IPv4                                IpType = "IPv4"
	IPv6                                IpType = "IPv6"
	EsxiHostnamePrefix                         = "esxi"
	unknownDiskMode                            = "unknown"
	unknownProvisioning                        = "unknown"
	unknownUnitNumber                          = int32(-1)
	locationCodeLength                         = 3
	vmxPrefix                                  = "vmx-"
	ServerKindUnknown                          = "UNKNOWN"
	ServerKindHardware                         = "HARDWARE"
	ServerKindVirtual                          = "VIRTUAL"
	ServerTypeUnknown                          = "UNKNOWN"
	ServerTypeOther                            = "OTHER"
	ServerTypeCiscoRackUnit                    = "CISCO_RACK_UNIT"
	ServerTypeCiscoBlade                       = "CISCO_BLADE"
	ServerTypeVmVmware                         = "VM_VMWARE"
	ServerTypeVmProxmox                        = "VM_PROXMOX"
	ServerTypeVmOpenshiftVirtualization        = "VM_OPENSHIFT_VIRTUALIZATION"
	ServerTypeVmOlvm                           = "VM_OLVM"
)

type (
	// VCENTER represents a VMware vCenter server configuration
	VCENTER struct {
		Enabled           bool   // Whether this vCenter server is enabled for monitoring
		Fqdn              string // Fully qualified domain name of the vCenter server
		Username          string // Username for authentication
		EncryptedPassword string // Encrypted password for authentication
		Locked            bool
		UnlockedUUIDs     string
	}

	// Config represents the complete application configuration
	Config struct {
		GENERAL  config.General  // General application settings
		DATABASE config.Database // Database connection settings
		VCENTER  []VCENTER       // List of vCenter servers to monitor
	}

	// Status represents the health status of virtual machines and components
	Status string

	// Network represents a network configuration within an IPAM system
	Network struct {
		ID             uint           `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version        uint32         `gorm:"column:version;default:0"`
		CreatedAt      time.Time      `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt      time.Time      `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		InfobloxID     uint           `gorm:"column:infoblox_id;not null;uniqueIndex:uq_infoblox_ip;uniqueIndex:uq_infoblox_vlan;constraint:OnDelete:CASCADE"`
		ConfigInfoblox ConfigInfoblox `gorm:"foreignKey:InfobloxID;constraint:OnDelete:CASCADE"`
		Vlan           *int32         `gorm:"column:vlan;uniqueIndex:uq_infoblox_vlan"`
		Name           string         `gorm:"column:name;size:100"`
		IpAddress      string         `gorm:"column:ip_address;uniqueIndex:uq_infoblox_ip"`
		Environment    string         `gorm:"column:environment;type:environment_type"`
	}

	// Cloud represents a cloud environment or vCenter server
	Cloud struct {
		ID               uint           `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version          uint32         `gorm:"column:version;default:0"`
		CreatedAt        time.Time      `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt        time.Time      `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		Name             string         `gorm:"column:name;size:100"`
		Fqdn             string         `gorm:"column:fqdn;size:100;not null;uniqueIndex"`
		ServerGUI        string         `gorm:"column:server_gui;size:100"`
		ConfigInfobloxID *uint          `gorm:"column:config_infoblox_id"`
		ConfigInfoblox   ConfigInfoblox `gorm:"constraint:OnDelete:SET NULL"`
	}

	// PortGroup represents a network port group in vCenter
	PortGroup struct {
		ID           uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version      uint32    `gorm:"column:version;default:0"`
		CreatedAt    time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt    time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		CloudID      uint      `gorm:"column:cloud_id;not null;uniqueIndex:cloud_name_idx;constraint:OnDelete:CASCADE"`
		Cloud        Cloud     `gorm:"constraint:OnDelete:CASCADE"`
		PortGroupKey string    `gorm:"column:port_group_key;size:100;not null;uniqueIndex:cloud_name_idx"`
		Name         string    `gorm:"column:name;size:50"`
		Vlan         string    `gorm:"column:vlan;size:200"`
		NetworkID    *uint     `gorm:"column:network_id;constraint:OnDelete:SET NULL"`
		Network      Network   `gorm:"constraint:OnDelete:SET NULL"`
	}

	// Server represents a virtual machine or server in the cloud environment
	Server struct {
		ID                                    uint       `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version                               uint32     `gorm:"column:version;default:0"`
		CreatedAt                             time.Time  `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt                             time.Time  `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		CloudID                               uint       `gorm:"column:cloud_id;not null;uniqueIndex:cloud_uuid_idx;constraint:OnDelete:CASCADE"`
		Cloud                                 Cloud      `gorm:"constraint:OnDelete:CASCADE"`
		UUID                                  string     `gorm:"column:uuid;size:50;not null;uniqueIndex:cloud_uuid_idx"`
		InstanceUuid                          string     `gorm:"column:instance_uuid;size:50"`
		VmId                                  string     `gorm:"column:vm_id;size:50"`
		Cluster                               string     `gorm:"column:cluster;size:25"`
		Host                                  string     `gorm:"column:host;size:50"`
		Location                              string     `gorm:"column:location;size:10"`
		Name                                  string     `gorm:"column:name;not null;size:200"`
		Fqdn                                  string     `gorm:"column:fqdn;size:100"`
		PowerState                            string     `gorm:"column:power_state;not null;size:20"`
		MemoryMB                              int32      `gorm:"column:memory_mb;not null"`
		MemoryMBPrev                          *int32     `gorm:"column:memory_mb_prev"`
		MemoryMBChangeDate                    *time.Time `gorm:"column:memory_mb_change_date"`
		NumCpu                                int32      `gorm:"column:num_cpu;not null"`
		NumCpuPrev                            *int32     `gorm:"column:num_cpu_prev"`
		NumCpuChangeDate                      *time.Time `gorm:"column:num_cpu_change_date"`
		NumCoresPerSocket                     int32      `gorm:"column:num_cores_per_socket"`
		MemoryHotAddEnabled                   bool       `gorm:"column:memory_hot_add_enabled;not null;type:boolean;check:memory_hot_add_enabled in (TRUE, FALSE);default:FALSE"`
		CpuHotAddEnabled                      bool       `gorm:"column:cpu_hot_add_enabled;;not null;type:boolean;check:cpu_hot_add_enabled in (TRUE, FALSE);default:FALSE"`
		CpuHotRemoveEnabled                   bool       `gorm:"column:cpu_hot_remove_enabled;not null;type:boolean;check:cpu_hot_remove_enabled in (TRUE, FALSE);default:FALSE"`
		CpuTopology                           string     `gorm:"column:cpu_topology;size:100"`
		VmxVersion                            string     `gorm:"column:vmx_version;size:10"`
		OverallStatus                         Status     `gorm:"column:overall_status;type:varchar(6);not null;default:gray;check:overall_status in ('green','yellow','red','gray');comment:Enum 'green','yellow','red','gray' für OverallStatus"` // 'green','yellow','red','gray'
		ConfigStatus                          Status     `gorm:"column:config_status;type:varchar(6);not null;default:gray;check:config_status in ('green','yellow','red','gray');comment:Enum 'green','yellow','red','gray' für ConfigStatus"`    // 'green','yellow','red','gray'
		ConfigEqualsTools                     bool       `gorm:"column:config_equals_tools;not null;type:boolean;check:config_equals_tools in (TRUE, FALSE);default:FALSE"`
		GuestConfigId                         string     `gorm:"column:guest_config_id;size:50"`
		GuestConfigFullName                   string     `gorm:"column:guest_config_full_name;size:50"`
		GuestToolsId                          string     `gorm:"column:guest_tools_id;size:50"`
		GuestToolsFullName                    string     `gorm:"column:guest_tools_full_name;size:50"`
		GuestToolsState                       string     `gorm:"column:guest_tools_state;size:30"`
		GuestToolsRunningStatus               string     `gorm:"column:guest_tools_running_status;size:30"`
		GuestToolsVersionStatus               string     `gorm:"column:guest_tools_version_status;size:30"`
		GuestToolsVersionStatus2              string     `gorm:"column:guest_tools_version_status2;size:30"`
		GuestToolsInstallType                 string     `gorm:"column:guest_tools_install_type;size:30"`
		GuestToolsVersion                     string     `gorm:"column:guest_tools_version;size:20"`
		GuestToolsFamily                      string     `gorm:"column:guest_tools_family;size:50"`
		GuestToolsHostname                    string     `gorm:"column:guest_tools_hostname;size:200"`
		GuestToolsIpAddress                   string     `gorm:"column:guest_tools_ip_address;size:300"`
		GuestToolsArchitecture                string     `gorm:"column:guest_tools_architecture;size:10"`
		GuestToolsBitness                     string     `gorm:"column:guest_tools_bitness;size:10"`
		GuestToolsBuildNumber                 string     `gorm:"column:guest_tools_build_number;size:20"`
		GuestToolsCpeString                   string     `gorm:"column:guest_tools_cpe_string;size:100"`
		GuestToolsDistroAddlVersion           string     `gorm:"column:guest_tools_distro_addl_version;size:50"`
		GuestToolsDistroName                  string     `gorm:"column:guest_tools_distro_name;size:100"`
		GuestToolsDistroVersion               string     `gorm:"column:guest_tools_distro_version;size:20"`
		GuestToolsFamilyName                  string     `gorm:"column:guest_tools_family_name;size:20"`
		GuestToolsKernelVersion               string     `gorm:"column:guest_tools_kernel_version;size:40"`
		GuestToolsPrettyName                  string     `gorm:"column:guest_tools_pretty_name;size:100"`
		VDisks                                uint8      `gorm:"column:vdisks;default:0"`
		VDisksCapacityInBytes                 int64      `gorm:"column:vdisks_capacity_in_bytes;default:0"`
		BootTime                              *time.Time `gorm:"column:boot_time"`
		Locked                                bool       `gorm:"column:locked;not null;type:boolean;default:FALSE"`
		HotPlugMemoryLimit                    *int64     `gorm:"column:hot_plug_memory_limit"`
		HotPlugMemoryIncrementSize            *int64     `gorm:"column:hot_plug_memory_increment_size"`
		ServerKind                            ServerKind `gorm:"column:server_kind;type:server_kind;default:'UNKNOWN'::server_kind;not null"`
		ServerType                            ServerType `gorm:"column:server_type;type:server_type;default:'UNKNOWN'::server_type;not null"`
		MemoryAllocationExpandableReservation bool       `gorm:"column:memory_allocation_expandable_reservation"`
		MemoryAllocationLimit                 *int64     `gorm:"column:memory_allocation_limit"`
		MemoryAllocationOverheadLimit         *int64     `gorm:"column:memory_allocation_overhead_limit"`
		MemoryAllocationReservation           *int64     `gorm:"column:memory_allocation_reservation"`
		CpuAllocationExpandableReservation    bool       `gorm:"column:cpu_allocation_expandable_reservation"`
		CpuAllocationLimit                    *int64     `gorm:"column:cpu_allocation_limit"`
		CpuAllocationOverheadLimit            *int64     `gorm:"column:cpu_allocation_overhead_limit"`
		CpuAllocationReservation              *int64     `gorm:"column:cpu_allocation_reservation"`
	}

	// Disk represents a virtual disk attached to a server
	Disk struct {
		ID                uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version           uint32    `gorm:"column:version;default:0"`
		CreatedAt         time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt         time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		ServerID          uint      `gorm:"column:server_id;not null;uniqueIndex:server_key_idx;constraint:OnDelete:CASCADE"`
		Server            Server    `gorm:"constraint:OnDelete:CASCADE"`
		Key               int32     `gorm:"column:vdisk_key;not null;uniqueIndex:server_key_idx"`
		UnitNumber        int32     `gorm:"column:unit_number"`
		DiskProvisioning  string    `gorm:"column:disk_provisioning;size:50"`
		FileName          string    `gorm:"column:file_name;size:200"`
		CapacityInBytes   int64     `gorm:"column:capacity_in_bytes"`
		VDiskId           string    `gorm:"column:vdisk_id;size:100"`
		DeviceName        string    `gorm:"column:device;size:200"`
		VirtualDiskFormat string    `gorm:"column:virtual_disk_format;size:30"`
		DiskMode          string    `gorm:"column:disk_mode;size:30"`
	}

	// MountPoint represents a filesystem mount point on a server
	MountPoint struct {
		ID               uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version          uint32    `gorm:"column:version;default:0"`
		CreatedAt        time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt        time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		ServerID         uint      `gorm:"column:server_id;not null;uniqueIndex:server_path_idx;constraint:OnDelete:CASCADE"`
		Server           Server    `gorm:"constraint:OnDelete:CASCADE"`
		DiskPath         string    `gorm:"column:disk_path;size:255;not null;uniqueIndex:server_path_idx"`
		CapacityInBytes  int64     `gorm:"column:capacity_in_bytes"`
		FreeSpaceInBytes int64     `gorm:"column:free_space_in_bytes"`
		FilesystemType   string    `gorm:"column:filesystem_type;size:20"`
		Source           string    `gorm:"column:source;size:10"`
	}

	// Nic represents a network interface card attached to a server
	Nic struct {
		ID                 uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version            uint32    `gorm:"column:version;default:0"`
		CreatedAt          time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt          time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		ServerID           uint      `gorm:"column:server_id;not null;uniqueIndex:server_key_idx;constraint:OnDelete:CASCADE"`
		Server             Server    `gorm:"constraint:OnDelete:CASCADE"`
		PortGroupID        *uint     `gorm:"column:port_group_id;constraint:OnDelete:CASCADE"`
		PortGroup          PortGroup `gorm:"constraint:OnDelete:CASCADE"`
		Key                int32     `gorm:"column:vnic_key;not null;uniqueIndex:server_key_idx"`
		UnitNumber         int32     `gorm:"column:unit_number"`
		DeviceName         string    `gorm:"column:device;size:200"`
		MacAddress         string    `gorm:"column:mac_address;size:50"`
		NetworkName        string    `gorm:"column:network;size:200"`
		Connected          bool      `gorm:"column:connected;not null;type:boolean;check:connected in (TRUE, FALSE);default:FALSE"`
		PortGroupSummary   string    `gorm:"column:port_group_summary;size:200"`
		PortGroupKey       string    `gorm:"column:port_group_key;size:200"`
		DistributedPortKey string    `gorm:"column:distributed_port_key;size:200"`
		AddressType        string    `gorm:"column:address_type;size:20"`
		CardType           string    `gorm:"column:card_type;size:20"`
		ToolsIpAddress     string    `gorm:"column:tools_ip_address;size:300"`
		ToolsNetworkName   string    `gorm:"column:tools_network_name;size:200"`
		ToolsConnected     bool      `gorm:"column:tools_connected;not null;type:boolean;check:tools_connected in (TRUE, FALSE);default:FALSE"`
	}

	IpType string

	Ip struct {
		ID        uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version   uint32    `gorm:"column:version;default:0"`
		CreatedAt time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		Ip        string    `gorm:"column:ip;size:45;not null;uniqueIndex"`
		IpType    string    `gorm:"column:ip_type;type:cmp.ip_type;not null"`
	}

	// IpAssignment represents the many-to-many relationship between NICs and IPs
	IpAssignment struct {
		NicID uint `gorm:"column:nic_id;primaryKey"`
		IpID  uint `gorm:"column:ip_id;primaryKey"`
	}

	// ConfigInfoblox represents the Infoblox configuration table
	ConfigInfoblox struct {
		ID        uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version   uint32    `gorm:"column:version;default:0"`
		CreatedAt time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
	}

	// Snapshot represents a VM snapshot
	Snapshot struct {
		ID              uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version         uint32    `gorm:"column:version;default:0"`
		CreatedAt       time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt       time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		ServerID        uint      `gorm:"column:server_id;not null;uniqueIndex:server_snapshot_id_idx;constraint:OnDelete:CASCADE"`
		Server          Server    `gorm:"constraint:OnDelete:CASCADE"`
		SnapshotId      int32     `gorm:"column:snapshot_id;not null;uniqueIndex:server_snapshot_id_idx"`
		Name            string    `gorm:"column:name;size:200"`
		Description     string    `gorm:"column:description;size:200"`
		CreateTime      time.Time `gorm:"column:create_time"`
		Quiesced        bool      `gorm:"column:quiesced;not null;type:boolean;check:quiesced in (TRUE, FALSE);default:FALSE"`
		State           string    `gorm:"column:state;size:20"`
		ReplaySupported bool      `gorm:"column:replay_supported;not null;type:boolean;check:replay_supported in (TRUE, FALSE);default:FALSE"`
		RetentionPeriod time.Time `gorm:"column:retention_period"`
	}

	// ServerCustomAttribute represents a custom attribute for a server
	ServerCustomAttribute struct {
		ID        uint      `gorm:"column:id;primaryKey;autoIncrement:true"`
		Version   uint32    `gorm:"column:version;default:0;not null"`
		CreatedAt time.Time `gorm:"column:created_at;default:CURRENT_TIMESTAMP(3)"`
		UpdatedAt time.Time `gorm:"column:updated_at;default:CURRENT_TIMESTAMP(3)"`
		ServerID  uint      `gorm:"column:server_id;not null;constraint:OnDelete:CASCADE"`
		Server    Server    `gorm:"constraint:OnDelete:CASCADE"`
		Name      string    `gorm:"column:name;size:255"`
		Value     string    `gorm:"column:value;type:text"`
	}

	// ServerKind represents the kind of server (UNKNOWN, HARDWARE, VIRTUAL)
	ServerKind string

	// ServerType represents the type of server (UNKNOWN, OTHER, CISCO_RACK_UNIT, etc.)
	ServerType string
)

// TableName returns the database table name for Network
func (*Network) TableName() string {
	return "network"
}

// TableName returns the database table name for Cloud
func (*Cloud) TableName() string {
	return "cloud"
}

// TableName returns the database table name for PortGroup
func (*PortGroup) TableName() string {
	return "port_group"
}

// TableName returns the database table name for Server
func (*Server) TableName() string {
	return "server"
}

// TableName returns the database table name for Disk
func (*Disk) TableName() string {
	return "disk"
}

// TableName returns the database table name for MountPoint
func (*MountPoint) TableName() string {
	return "mount_point"
}

// TableName returns the database table name for Nic
func (*Nic) TableName() string {
	return "nic"
}

// TableName returns the database table name for Snapshot
func (*Snapshot) TableName() string {
	return "snapshot"
}

func (*Ip) TableName() string {
	return "ip"
}

func (*IpAssignment) TableName() string {
	return "ip_assignment"
}

func (*ConfigInfoblox) TableName() string {
	return "config_infoblox"
}

func (*ServerCustomAttribute) TableName() string {
	return "server_custom_attribute"
}

// String returns the string representation of the Status
func (s Status) String() string {
	switch s {
	case Green:
		return "green"
	case Yellow:
		return "yellow"
	case Red:
		return "red"
	case Gray:
		return "gray"
	default:
		return "unknown"
	}
}

// CompareAndUpdate compares the stored PortGroup with a new PortGroup and updates if different
// Returns true if the stored PortGroup was updated, false otherwise
func (storedPortgroup *PortGroup) CompareAndUpdate(newPortGroup PortGroup, timeNow time.Time) bool {
	if storedPortgroup.CloudID != newPortGroup.CloudID ||
		storedPortgroup.PortGroupKey != newPortGroup.PortGroupKey ||
		storedPortgroup.Name != newPortGroup.Name ||
		storedPortgroup.Vlan != newPortGroup.Vlan ||
		!compareUintPtr(storedPortgroup.NetworkID, newPortGroup.NetworkID) {
		storedPortgroup.UpdatedAt = timeNow
		storedPortgroup.Version = storedPortgroup.Version + 1
		storedPortgroup.CloudID = newPortGroup.CloudID
		storedPortgroup.PortGroupKey = newPortGroup.PortGroupKey
		storedPortgroup.Name = newPortGroup.Name
		storedPortgroup.Vlan = newPortGroup.Vlan
		storedPortgroup.NetworkID = newPortGroup.NetworkID
		return true
	}
	return false
}

// CompareAndUpdate compares the stored Server with a new Server and updates if different
// Returns true if the stored Server was updated, false otherwise
// Also tracks changes to CPU and Memory configurations with timestamp and previous values
func (storedVM *Server) CompareAndUpdate(newVM Server, timeNow time.Time) bool {
	if storedVM.CloudID != newVM.CloudID ||
		storedVM.UUID != newVM.UUID ||
		storedVM.InstanceUuid != newVM.InstanceUuid ||
		storedVM.VmId != newVM.VmId ||
		storedVM.Cluster != newVM.Cluster ||
		storedVM.Host != newVM.Host ||
		storedVM.Location != newVM.Location ||
		storedVM.Name != newVM.Name ||
		storedVM.PowerState != newVM.PowerState ||
		storedVM.MemoryMB != newVM.MemoryMB ||
		storedVM.NumCpu != newVM.NumCpu ||
		storedVM.NumCoresPerSocket != newVM.NumCoresPerSocket ||
		storedVM.MemoryHotAddEnabled != newVM.MemoryHotAddEnabled ||
		storedVM.CpuHotAddEnabled != newVM.CpuHotAddEnabled ||
		storedVM.CpuHotRemoveEnabled != newVM.CpuHotRemoveEnabled ||
		!compareInt64Ptr(storedVM.HotPlugMemoryLimit, newVM.HotPlugMemoryLimit) ||
		!compareInt64Ptr(storedVM.HotPlugMemoryIncrementSize, newVM.HotPlugMemoryIncrementSize) ||
		storedVM.MemoryAllocationExpandableReservation != newVM.MemoryAllocationExpandableReservation ||
		!compareInt64Ptr(storedVM.MemoryAllocationLimit, newVM.MemoryAllocationLimit) ||
		!compareInt64Ptr(storedVM.MemoryAllocationOverheadLimit, newVM.MemoryAllocationOverheadLimit) ||
		!compareInt64Ptr(storedVM.MemoryAllocationReservation, newVM.MemoryAllocationReservation) ||
		storedVM.CpuAllocationExpandableReservation != newVM.CpuAllocationExpandableReservation ||
		!compareInt64Ptr(storedVM.CpuAllocationLimit, newVM.CpuAllocationLimit) ||
		!compareInt64Ptr(storedVM.CpuAllocationOverheadLimit, newVM.CpuAllocationOverheadLimit) ||
		!compareInt64Ptr(storedVM.CpuAllocationReservation, newVM.CpuAllocationReservation) ||
		storedVM.CpuTopology != newVM.CpuTopology ||
		storedVM.VmxVersion != newVM.VmxVersion ||
		storedVM.OverallStatus != newVM.OverallStatus ||
		storedVM.ConfigStatus != newVM.ConfigStatus ||
		storedVM.ConfigEqualsTools != newVM.ConfigEqualsTools ||
		storedVM.GuestConfigId != newVM.GuestConfigId ||
		storedVM.GuestConfigFullName != newVM.GuestConfigFullName ||
		storedVM.GuestToolsId != newVM.GuestToolsId ||
		storedVM.GuestToolsFullName != newVM.GuestToolsFullName ||
		storedVM.GuestToolsState != newVM.GuestToolsState ||
		storedVM.GuestToolsRunningStatus != newVM.GuestToolsRunningStatus ||
		storedVM.GuestToolsVersionStatus != newVM.GuestToolsVersionStatus ||
		storedVM.GuestToolsVersionStatus2 != newVM.GuestToolsVersionStatus2 ||
		storedVM.GuestToolsInstallType != newVM.GuestToolsInstallType ||
		storedVM.GuestToolsVersion != newVM.GuestToolsVersion ||
		storedVM.GuestToolsFamily != newVM.GuestToolsFamily ||
		storedVM.GuestToolsHostname != newVM.GuestToolsHostname ||
		storedVM.GuestToolsIpAddress != newVM.GuestToolsIpAddress ||
		storedVM.GuestToolsArchitecture != newVM.GuestToolsArchitecture ||
		storedVM.GuestToolsBitness != newVM.GuestToolsBitness ||
		storedVM.GuestToolsBuildNumber != newVM.GuestToolsBuildNumber ||
		storedVM.GuestToolsCpeString != newVM.GuestToolsCpeString ||
		storedVM.GuestToolsDistroAddlVersion != newVM.GuestToolsDistroAddlVersion ||
		storedVM.GuestToolsDistroName != newVM.GuestToolsDistroName ||
		storedVM.GuestToolsDistroVersion != newVM.GuestToolsDistroVersion ||
		storedVM.GuestToolsFamilyName != newVM.GuestToolsFamilyName ||
		storedVM.GuestToolsKernelVersion != newVM.GuestToolsKernelVersion ||
		storedVM.GuestToolsPrettyName != newVM.GuestToolsPrettyName ||
		storedVM.VDisks != newVM.VDisks ||
		storedVM.VDisksCapacityInBytes != newVM.VDisksCapacityInBytes ||
		((storedVM.BootTime != nil && newVM.BootTime != nil && !storedVM.BootTime.UTC().Equal(newVM.BootTime.UTC())) || (storedVM.BootTime == nil && newVM.BootTime != nil) || (storedVM.BootTime != nil && newVM.BootTime == nil)) ||
		(storedVM.Fqdn == "" || (newVM.GuestToolsHostname != "" && storedVM.Fqdn != newVM.GuestToolsHostname)) ||
		storedVM.Locked != newVM.Locked ||
		storedVM.ServerKind != newVM.ServerKind ||
		storedVM.ServerType != newVM.ServerType {

		storedVM.UpdatedAt = timeNow
		storedVM.Version = storedVM.Version + 1
		storedVM.CloudID = newVM.CloudID
		storedVM.UUID = newVM.UUID
		storedVM.InstanceUuid = newVM.InstanceUuid
		storedVM.VmId = newVM.VmId
		storedVM.Cluster = newVM.Cluster
		storedVM.Host = newVM.Host
		storedVM.Location = newVM.Location
		storedVM.Name = newVM.Name
		storedVM.PowerState = newVM.PowerState
		// Track memory changes with history
		if storedVM.MemoryMB != newVM.MemoryMB {
			storedVM.MemoryMBPrev = new(storedVM.MemoryMB)
			storedVM.MemoryMBChangeDate = &timeNow
		}
		storedVM.MemoryMB = newVM.MemoryMB
		// Track CPU changes with history
		if storedVM.NumCpu != newVM.NumCpu {
			storedVM.NumCpuPrev = new(storedVM.NumCpu)
			storedVM.NumCpuChangeDate = &timeNow
		}
		storedVM.NumCpu = newVM.NumCpu
		storedVM.NumCoresPerSocket = newVM.NumCoresPerSocket
		storedVM.MemoryHotAddEnabled = newVM.MemoryHotAddEnabled
		storedVM.CpuHotAddEnabled = newVM.CpuHotAddEnabled
		storedVM.CpuHotRemoveEnabled = newVM.CpuHotRemoveEnabled
		storedVM.HotPlugMemoryLimit = newVM.HotPlugMemoryLimit
		storedVM.HotPlugMemoryIncrementSize = newVM.HotPlugMemoryIncrementSize
		storedVM.MemoryAllocationExpandableReservation = newVM.MemoryAllocationExpandableReservation
		storedVM.MemoryAllocationLimit = newVM.MemoryAllocationLimit
		storedVM.MemoryAllocationOverheadLimit = newVM.MemoryAllocationOverheadLimit
		storedVM.MemoryAllocationReservation = newVM.MemoryAllocationReservation
		storedVM.CpuAllocationExpandableReservation = newVM.CpuAllocationExpandableReservation
		storedVM.CpuAllocationLimit = newVM.CpuAllocationLimit
		storedVM.CpuAllocationOverheadLimit = newVM.CpuAllocationOverheadLimit
		storedVM.CpuAllocationReservation = newVM.CpuAllocationReservation
		storedVM.CpuTopology = newVM.CpuTopology
		storedVM.VmxVersion = newVM.VmxVersion
		storedVM.OverallStatus = newVM.OverallStatus
		storedVM.ConfigStatus = newVM.ConfigStatus
		storedVM.ConfigEqualsTools = newVM.ConfigEqualsTools
		storedVM.GuestConfigId = newVM.GuestConfigId
		storedVM.GuestConfigFullName = newVM.GuestConfigFullName
		storedVM.GuestToolsId = newVM.GuestToolsId
		storedVM.GuestToolsFullName = newVM.GuestToolsFullName
		storedVM.GuestToolsState = newVM.GuestToolsState
		storedVM.GuestToolsRunningStatus = newVM.GuestToolsRunningStatus
		storedVM.GuestToolsVersionStatus = newVM.GuestToolsVersionStatus
		storedVM.GuestToolsVersionStatus2 = newVM.GuestToolsVersionStatus2
		storedVM.GuestToolsInstallType = newVM.GuestToolsInstallType
		storedVM.GuestToolsVersion = newVM.GuestToolsVersion
		storedVM.GuestToolsFamily = newVM.GuestToolsFamily
		storedVM.GuestToolsHostname = newVM.GuestToolsHostname
		storedVM.GuestToolsIpAddress = newVM.GuestToolsIpAddress
		storedVM.GuestToolsArchitecture = newVM.GuestToolsArchitecture
		storedVM.GuestToolsBitness = newVM.GuestToolsBitness
		storedVM.GuestToolsBuildNumber = newVM.GuestToolsBuildNumber
		storedVM.GuestToolsCpeString = newVM.GuestToolsCpeString
		storedVM.GuestToolsDistroAddlVersion = newVM.GuestToolsDistroAddlVersion
		storedVM.GuestToolsDistroName = newVM.GuestToolsDistroName
		storedVM.GuestToolsDistroVersion = newVM.GuestToolsDistroVersion
		storedVM.GuestToolsFamilyName = newVM.GuestToolsFamilyName
		storedVM.GuestToolsKernelVersion = newVM.GuestToolsKernelVersion
		storedVM.GuestToolsPrettyName = newVM.GuestToolsPrettyName
		storedVM.VDisks = newVM.VDisks
		storedVM.VDisksCapacityInBytes = newVM.VDisksCapacityInBytes
		storedVM.BootTime = newVM.BootTime
		// Set FQDN from guest tools hostname if available
		if newVM.GuestToolsHostname != "" {
			storedVM.Fqdn = newVM.GuestToolsHostname
		}
		if storedVM.Fqdn == "" {
			storedVM.Fqdn = newVM.Name
		}
		storedVM.Locked = newVM.Locked
		storedVM.ServerKind = newVM.ServerKind
		storedVM.ServerType = newVM.ServerType
		return true
	}
	return false
}

// CompareAndUpdate compares the stored Disk with a new Disk and updates if different
// Returns true if the stored Disk was updated, false otherwise
func (storedDisk *Disk) CompareAndUpdate(newDisk Disk, timeNow time.Time) bool {
	if storedDisk.ServerID != newDisk.ServerID ||
		storedDisk.Key != newDisk.Key ||
		storedDisk.UnitNumber != newDisk.UnitNumber ||
		storedDisk.DiskProvisioning != newDisk.DiskProvisioning ||
		storedDisk.FileName != newDisk.FileName ||
		storedDisk.CapacityInBytes != newDisk.CapacityInBytes ||
		storedDisk.VDiskId != newDisk.VDiskId ||
		storedDisk.DeviceName != newDisk.DeviceName ||
		storedDisk.VirtualDiskFormat != newDisk.VirtualDiskFormat ||
		storedDisk.DiskMode != newDisk.DiskMode {

		storedDisk.UpdatedAt = timeNow
		storedDisk.Version = storedDisk.Version + 1
		storedDisk.ServerID = newDisk.ServerID
		storedDisk.Key = newDisk.Key
		storedDisk.UnitNumber = newDisk.UnitNumber
		storedDisk.DiskProvisioning = newDisk.DiskProvisioning
		storedDisk.FileName = newDisk.FileName
		storedDisk.CapacityInBytes = newDisk.CapacityInBytes
		storedDisk.VDiskId = newDisk.VDiskId
		storedDisk.DeviceName = newDisk.DeviceName
		storedDisk.VirtualDiskFormat = newDisk.VirtualDiskFormat
		storedDisk.DiskMode = newDisk.DiskMode
		return true
	}
	return false
}

// CompareAndUpdate compares the stored MountPoint with a new MountPoint and updates if different
// Returns true if the stored MountPoint was updated, false otherwise
func (storedMountPoint *MountPoint) CompareAndUpdate(newMountPoint MountPoint, timeNow time.Time) bool {
	if storedMountPoint.ServerID != newMountPoint.ServerID ||
		storedMountPoint.DiskPath != newMountPoint.DiskPath ||
		storedMountPoint.CapacityInBytes != newMountPoint.CapacityInBytes ||
		storedMountPoint.FreeSpaceInBytes != newMountPoint.FreeSpaceInBytes ||
		storedMountPoint.FilesystemType != newMountPoint.FilesystemType ||
		storedMountPoint.Source != newMountPoint.Source {

		storedMountPoint.UpdatedAt = timeNow
		storedMountPoint.Version = storedMountPoint.Version + 1
		storedMountPoint.ServerID = newMountPoint.ServerID
		storedMountPoint.DiskPath = newMountPoint.DiskPath
		storedMountPoint.CapacityInBytes = newMountPoint.CapacityInBytes
		storedMountPoint.FreeSpaceInBytes = newMountPoint.FreeSpaceInBytes
		storedMountPoint.FilesystemType = newMountPoint.FilesystemType
		storedMountPoint.Source = newMountPoint.Source
		return true
	}
	return false
}

// CompareAndUpdate compares the stored Nic with a new Nic and updates if different
// Returns true if the stored Nic was updated, false otherwise
func (storedNIC *Nic) CompareAndUpdate(newNIC Nic, timeNow time.Time) bool {
	if storedNIC.ServerID != newNIC.ServerID ||
		storedNIC.Key != newNIC.Key ||
		storedNIC.UnitNumber != newNIC.UnitNumber ||
		storedNIC.DeviceName != newNIC.DeviceName ||
		storedNIC.MacAddress != newNIC.MacAddress ||
		storedNIC.NetworkName != newNIC.NetworkName ||
		storedNIC.Connected != newNIC.Connected ||
		storedNIC.PortGroupSummary != newNIC.PortGroupSummary ||
		storedNIC.PortGroupKey != newNIC.PortGroupKey ||
		storedNIC.DistributedPortKey != newNIC.DistributedPortKey ||
		storedNIC.AddressType != newNIC.AddressType ||
		storedNIC.CardType != newNIC.CardType ||
		storedNIC.ToolsIpAddress != newNIC.ToolsIpAddress ||
		storedNIC.ToolsNetworkName != newNIC.ToolsNetworkName ||
		storedNIC.ToolsConnected != newNIC.ToolsConnected ||
		!compareUintPtr(storedNIC.PortGroupID, newNIC.PortGroupID) {

		storedNIC.UpdatedAt = timeNow
		storedNIC.Version = storedNIC.Version + 1
		storedNIC.UnitNumber = newNIC.UnitNumber
		storedNIC.DeviceName = newNIC.DeviceName
		storedNIC.MacAddress = newNIC.MacAddress
		storedNIC.NetworkName = newNIC.NetworkName
		storedNIC.Connected = newNIC.Connected
		storedNIC.PortGroupSummary = newNIC.PortGroupSummary
		storedNIC.PortGroupKey = newNIC.PortGroupKey
		storedNIC.DistributedPortKey = newNIC.DistributedPortKey
		storedNIC.AddressType = newNIC.AddressType
		storedNIC.CardType = newNIC.CardType
		storedNIC.ToolsIpAddress = newNIC.ToolsIpAddress
		storedNIC.ToolsNetworkName = newNIC.ToolsNetworkName
		storedNIC.ToolsConnected = newNIC.ToolsConnected
		storedNIC.PortGroupID = newNIC.PortGroupID
		return true
	}
	return false
}

// CompareAndUpdate compares the stored Snapshot with a new Snapshot and updates if different
// Returns true if the stored Snapshot was updated, false otherwise
func (storedSnapshot *Snapshot) CompareAndUpdate(newSnapshot Snapshot, timeNow time.Time) bool {
	if storedSnapshot.ServerID != newSnapshot.ServerID ||
		storedSnapshot.SnapshotId != newSnapshot.SnapshotId ||
		storedSnapshot.Name != newSnapshot.Name ||
		storedSnapshot.Description != newSnapshot.Description ||
		storedSnapshot.CreateTime != newSnapshot.CreateTime ||
		storedSnapshot.Quiesced != newSnapshot.Quiesced ||
		storedSnapshot.State != newSnapshot.State ||
		storedSnapshot.ReplaySupported != newSnapshot.ReplaySupported ||
		storedSnapshot.RetentionPeriod != newSnapshot.RetentionPeriod {

		storedSnapshot.UpdatedAt = timeNow
		storedSnapshot.Version += 1
		storedSnapshot.ServerID = newSnapshot.ServerID
		storedSnapshot.SnapshotId = newSnapshot.SnapshotId
		storedSnapshot.Name = newSnapshot.Name
		storedSnapshot.Description = newSnapshot.Description
		storedSnapshot.CreateTime = newSnapshot.CreateTime
		storedSnapshot.Quiesced = newSnapshot.Quiesced
		storedSnapshot.State = newSnapshot.State
		storedSnapshot.ReplaySupported = newSnapshot.ReplaySupported
		storedSnapshot.RetentionPeriod = newSnapshot.RetentionPeriod
		return true
	}
	return false
}

// main is the entry point of the application
// It handles command line arguments and executes the appropriate functionality
func main() {
	flag.Usage = func() {
		_, err := fmt.Fprintf(flag.CommandLine.Output(), "usage: %s [init|crypt-password]\n", os.Args[0])
		if err != nil {
			log.Fatal(err)
		}
		flag.PrintDefaults()
	}
	flag.Parse()
	switch len(os.Args) {
	case 1:
		bot()
	case 2:
		switch os.Args[1] {
		case "init":
			initDB()
		case "crypt-password":
			err := app.CryptPassword()
			if err != nil {
				log.Fatal(err)
			}
		default:
			_, err := fmt.Fprintf(os.Stderr, "error: unknown command - %s\n", os.Args[1])
			if err != nil {
				log.Fatal(err)
			}
			flag.Usage()
			os.Exit(1)
		}
	default:
		_, err := fmt.Fprintln(os.Stderr, "error: wrong number of arguments")
		if err != nil {
			log.Fatal(err)
		}
		flag.Usage()
		os.Exit(1)
	}
}

// bot is the main function that coordinates the application's execution, handling concurrency, configuration, and logging.
func bot() {
	startTime := time.Now()
	log.Printf("Program started at: %s", startTime.Format("2006-01-02 15:04:05"))

	// Prüfen, ob bereits eine Instanz läuft
	release, err := lock.Acquire(appname)
	if err != nil {
		log.Printf("Error creating lock file: %v", err)
		return
	}
	defer release()

	c, err := cfg.LoadConfig[Config](appname)
	if err != nil {
		log.Printf("Error reading configuration file: %v", err)
		return
	}
	debug = c.GENERAL.Debug
	postgres, err := db.OpenPostgres(c.GENERAL, c.DATABASE)
	if err != nil {
		log.Printf("Error opening database connection: %v", err)
		return
	}
	defer func() {
		sqlDB, err := postgres.DB()
		if err == nil {
			sqlDB.Close()
		}
	}()

	var wg sync.WaitGroup

	for _, v := range c.VCENTER {
		if v.Enabled {
			wg.Add(1)
			go func(vcenter VCENTER) {
				defer wg.Done()

				vcenterStartTime := time.Now()
				log.Printf("Starting processing for vCenter: %s", vcenter.Fqdn)

				password, err := cipher.DecryptString(c.GENERAL.Passphrase, vcenter.EncryptedPassword)
				if err != nil {
					log.Printf("Error decrypting for %s: %v", vcenter.Fqdn, err)
					return
				}
				customFieldMap, serverGUI, vms, hosts, cluster, portgroups, err := loadCloudData(vcenter.Fqdn, vcenter.Username, password)
				if err != nil {
					log.Printf("vCenter %s : loadCloudData Error: %v\n", vcenter.Fqdn, err)
					return
				}
				cloud, err := fetchAndStoreCloud(postgres, serverGUI, vcenter.Fqdn)
				if err != nil {
					log.Printf("fetchAndStoreCloud failed: %v", err)
					return
				}
				ipRecordMap := updateIPs(postgres, vms)
				updatePortgroups(postgres, cloud, portgroups)
				updateServer(postgres, cloud, vms, hosts, cluster, vcenter.Locked, vcenter.UnlockedUUIDs, ipRecordMap, customFieldMap)

				vcenterEndTime := time.Now()
				vcenterDuration := vcenterEndTime.Sub(vcenterStartTime)
				log.Printf("vCenter %s completed - Runtime: %s (%.2f seconds)", vcenter.Fqdn, vcenterDuration, vcenterDuration.Seconds())
			}(v)
		}
	}

	wg.Wait()
	endTime := time.Now()
	duration := endTime.Sub(startTime)
	log.Printf("Program ended at: %s", endTime.Format("2006-01-02 15:04:05"))
	log.Printf("Total runtime: %s", duration)
	log.Printf("Total runtime in seconds: %.2f", duration.Seconds())
}

// initDB initializes the database connection and performs necessary migrations.
func initDB() {
	c, err := cfg.LoadConfig[Config](appname)
	if err != nil {
		log.Panic(err)
	}

	debug = c.GENERAL.Debug
	database, err := db.OpenPostgres(c.GENERAL, c.DATABASE)
	if err != nil {
		log.Panic(err)
	}

	models := []any{
		&Cloud{},
		&PortGroup{},
		&Server{},
		&Disk{},
		&MountPoint{},
		&Nic{},
		&Snapshot{},
		&ConfigInfoblox{},
	}
	for _, model := range models {
		err := app.AutoMigrateTable(database, model)
		if err != nil {
			log.Fatal(err)
		}
	}
}

func debugPrintf(format string, a ...interface{}) {
	if debug {
		log.Printf(format, a...)
	}
}

// loadCloudData fetches and returns data from vCenter, including custom fields, VMs, hosts, clusters, portgroups, and server GUI.
// Returns an error if the connection, login, data retrieval, or logout process fails.
func loadCloudData(fqdn string, username string, password string) (map[int32]string, string, []mo.VirtualMachine, []mo.HostSystem, []mo.ComputeResource, []mo.DistributedVirtualPortgroup, error) {
	// Read data from vCenter
	debugPrintf("Environment: %s\n", fqdn)
	c, err := vcenter.New(fqdn, username, password)
	if err != nil {
		log.Printf("vCenter: %s / Method: New / Error message: %s\n", fqdn, err)
		return nil, "", nil, nil, nil, nil, err
	}
	err = c.Login()
	if err != nil {
		log.Printf("vCenter: %s / Method: Login / Error message: %s\n", fqdn, err)
		return nil, "", nil, nil, nil, nil, err
	}
	serverGUI := c.ReadServerGUI()
	customFieldMap, vms, hosts, clusters, portgroups, err := c.ReadVcenterData()
	if err != nil {
		log.Printf("vCenter: %s / Method: ReadVcenterData / Error message: %s\n", fqdn, err)
		return nil, "", nil, nil, nil, nil, err
	}
	err = c.Logout()
	if err != nil {
		log.Printf("vCenter: %s / Method: Logout / Error message: %s\n", fqdn, err)
		return nil, "", nil, nil, nil, nil, err
	}
	return customFieldMap, serverGUI, vms, hosts, clusters, portgroups, nil
}

// fetchAndStoreCloud checks if a Cloud record exists in the database by its FQDN and inserts or updates it accordingly.
// db: The database connection object.
// serverGUI: The GUI link of the vCenter server.
// fqdn: The fully qualified domain name of the vCenter server.
// Returns the Cloud entity found or created, and an error if any database operation fails.
func fetchAndStoreCloud(db *gorm.DB, serverGUI string, fqdn string) (Cloud, error) {
	// Check if vCenter is already in the database
	var storedCloud Cloud
	result := db.Where("fqdn = ?", fqdn).First(&storedCloud)

	if result.RowsAffected == 0 {
		// vCenter is not in the database
		cloud := Cloud{Name: fqdn, Fqdn: fqdn, ServerGUI: serverGUI, CreatedAt: time.Now(), UpdatedAt: time.Now()}
		if err := db.Create(&cloud).Error; err != nil {
			log.Printf("Error saving vCenter %s: %v", fqdn, err)
			return Cloud{}, err
		}
		storedCloud = cloud
	} else {
		if storedCloud.ServerGUI != serverGUI {
			storedCloud.ServerGUI = serverGUI
			storedCloud.UpdatedAt = time.Now()
			if err := db.Save(&storedCloud).Error; err != nil {
				log.Printf("Error updating vCenter %s: %v", fqdn, err)
				return Cloud{}, err
			}
		}
	}
	return storedCloud, nil
}

// createPortGroupMap retrieves PortGroup entries from the database for a specific Cloud ID and maps them by PortGroupKey.
func createPortGroupMap(db *gorm.DB, cloudId uint) map[string]PortGroup {
	var storedPortGroups []PortGroup
	if err := db.Where("cloud_id = ?", cloudId).Find(&storedPortGroups).Error; err != nil {
		log.Printf("Error retrieving PortGroups from DB: %v", err)
		return nil
	}
	portGroupMap := make(map[string]PortGroup, len(storedPortGroups))
	for _, storedPortGroup := range storedPortGroups {
		portGroupMap[storedPortGroup.PortGroupKey] = storedPortGroup
	}
	return portGroupMap
}

// updatePortgroups synchronizes port group data between a cloud instance and the database using a provided list of port groups.
func updatePortgroups(db *gorm.DB, cloud Cloud, portgroups []mo.DistributedVirtualPortgroup) {
	existingVPortGroup := map[string]bool{}
	timeNow := time.Now()

	var networks []Network
	vlanNetworkMap := make(map[string]uint)

	if cloud.ConfigInfobloxID != nil {
		if err := db.Table("network").Where("infoblox_id = ?", *cloud.ConfigInfobloxID).Find(&networks).Error; err != nil {
			log.Printf("Error retrieving Networks from DB: %v\n\n", err)
		} else {
			// Create map: Key = VLAN as string, Value = Network ID
			for _, network := range networks {
				if network.Vlan != nil {
					vlanKey := fmt.Sprintf("%d", *network.Vlan)
					vlanNetworkMap[vlanKey] = network.ID
				}
			}
		}
	}

	for _, portgroup := range portgroups {
		vlan := getVLAN(portgroup.Config)

		// Get Network ID from map if VLAN exists
		var networkID *uint = nil
		if vlan != "" {
			if netID, exists := vlanNetworkMap[vlan]; exists {
				networkID = &netID
			}
		}

		newPortGroup := PortGroup{
			CreatedAt:    timeNow,
			UpdatedAt:    timeNow,
			CloudID:      cloud.ID,
			PortGroupKey: portgroup.Key,
			Name:         portgroup.Name,
			Vlan:         vlan,
			NetworkID:    networkID,
		}
		existingVPortGroup[portgroup.Key] = true

		var storedPortGroup PortGroup
		result := db.First(&storedPortGroup, "port_group_key = ? AND cloud_id = ?", newPortGroup.PortGroupKey, cloud.ID)

		if result.RowsAffected == 0 {
			if err := db.Create(&newPortGroup).Error; err != nil {
				log.Printf("Error saving PortGroup: %s (%v)", newPortGroup.PortGroupKey, err)
			} else {
				storedPortGroup = newPortGroup
			}
		} else {
			if storedPortGroup.CompareAndUpdate(newPortGroup, timeNow) {
				if err := db.Save(&storedPortGroup).Error; err != nil {
					log.Printf("Error updating PortGroup: %s (%v)", storedPortGroup.PortGroupKey, err)
				}
			}
		}
	}
	deleteRemovedPortGroups(db, cloud.ID, existingVPortGroup)
}

// getVLAN retrieves the VLAN information from the provided DVPortgroupConfigInfo.
// It returns the VLAN ID as a string or an empty string if the VLAN configuration is not present or invalid.
func getVLAN(config types.DVPortgroupConfigInfo) string {
	/*
		Name: ESXKB160206000_EXTSAND, Key: dvportgroup-926380, VLAN: 206
		Name: EXTDMZVDSK102_Uplink, Key: dvportgroup-924188, VLAN: Start 0, End 4094
	*/
	if vlanConfig, ok := config.DefaultPortConfig.(*types.VMwareDVSPortSetting); ok {
		if vlanId, ok := vlanConfig.Vlan.(*types.VmwareDistributedVirtualSwitchVlanIdSpec); ok {
			return fmt.Sprintf("%d", vlanId.VlanId)
		}

		if spec, ok := vlanConfig.Vlan.(*types.VmwareDistributedVirtualSwitchTrunkVlanSpec); ok {
			vlanIDs := spec.VlanId
			if len(vlanIDs) > 0 {
				var builder strings.Builder
				for i, vlanRange := range vlanIDs {
					if i > 0 {
						builder.WriteString(",")
					}
					builder.WriteString(fmt.Sprintf("%d-%d", vlanRange.Start, vlanRange.End))
				}
				return builder.String()
			}
		}
	}
	return ""
}

// updateIPs updates or inserts IP records based on the provided VirtualMachine list and returns a map of IP addresses to Ip structs.
func updateIPs(db *gorm.DB, vmList []mo.VirtualMachine) map[string]Ip {
	var allIpAddresses []string
	ipRecordMap := make(map[string]Ip)
	for _, vm := range vmList {
		if vm.Config == nil || vm.Config.Template {
			continue
		}
		if vm.Guest != nil && vm.Guest.Net != nil {
			for _, netInfo := range vm.Guest.Net {
				if netInfo.IpAddress != nil {
					for _, ip := range netInfo.IpAddress {
						if netInfo.IpAddress != nil {
							if ip != "" && ip != "127.0.0.1" && ip != "::1" {
								ip = strings.ToLower(strings.TrimSpace(ip))
								allIpAddresses = append(allIpAddresses, ip)
							}
						}
					}
				}
			}
		}
	}
	if len(allIpAddresses) > 0 {
		var existingIps []Ip
		db.Find(&existingIps)
		for _, ip := range existingIps {
			ipRecordMap[ip.Ip] = ip
		}
		var newIps []Ip
		for _, ip := range allIpAddresses {
			if _, exists := ipRecordMap[ip]; !exists {
				ipType := determineIpType(ip)
				newIp := Ip{
					Ip:     ip,
					IpType: ipType,
				}
				newIps = append(newIps, newIp)
				ipRecordMap[ip] = newIp
			}
		}
		if len(newIps) > 0 {
			if err := db.CreateInBatches(&newIps, 100).Error; err != nil {
				log.Printf("Error creating IP records in batch: %v", err)
				return ipRecordMap
			}
			for i := range newIps {
				ipRecordMap[newIps[i].Ip] = newIps[i]
			}
		}

	}
	return ipRecordMap
}

// updateServer synchronizes cloud resources with the database, processing VMs, hosts, clusters, and IP records concurrently.
func updateServer(db *gorm.DB, cloud Cloud, vmList []mo.VirtualMachine, hosts []mo.HostSystem, cluster []mo.ComputeResource, locked bool, unlockedUUIDs string, ipRecordMap map[string]Ip, customFieldMap map[int32]string) {
	existingVMs := map[string]bool{}
	timeNow := time.Now()

	unlockedUuuidSet := StringToSet(unlockedUUIDs)

	// Cache PortGroups once per Cloud execution
	portGroupMap := createPortGroupMap(db, cloud.ID)

	// key = hostId, value = Clustername (example: key = host-10052, value = CL-C-ADM001)
	hostClusterMap := make(map[string]string)
	for _, cluster := range cluster {
		// fmt.Printf("Cluster: %s\n", cluster.Name)
		for _, host := range cluster.Host {
			// fmt.Printf("  Host: %s\n", host.Value)
			hostClusterMap[host.Value] = cluster.Name
		}
	}

	// key = hostId, value = OntapHostname (example: key = host-10052, value = esxia20cnc001.example.org)
	hostNameMap := make(map[string]string)
	// key = vmId, value = hostId (example: key = vm-11008, value = host-10052)
	vmHostMap := make(map[string]string)
	for _, host := range hosts {
		hostNameMap[host.Reference().Value] = host.Name
		for _, vmHost := range host.Vm {
			vmHostMap[vmHost.Value] = host.Reference().Value
		}
	}

	// Parallel processing of VMs
	const maxWorkers = 3 // Number of parallel workers
	vmChan := make(chan mo.VirtualMachine, len(vmList))
	var wg sync.WaitGroup

	// Mutex for thread-safe access to existingVMs and database operations
	var existingVMsMutex sync.Mutex

	// Start worker goroutines
	for i := 0; i < maxWorkers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()

			for vm := range vmChan {
				processVM(db, cloud, vm, hostClusterMap, hostNameMap, vmHostMap, portGroupMap, unlockedUuuidSet, locked, timeNow, &existingVMs, &existingVMsMutex, ipRecordMap, customFieldMap)
			}
		}()
	}

	// Send VMs to channel
	for _, vm := range vmList {
		if vm.Config == nil || vm.Config.Template {
			continue
		}
		vmChan <- vm
	}
	close(vmChan)

	// Wait until all workers are finished
	wg.Wait()

	deleteRemovedVMs(db, cloud.ID, existingVMs)
}

// Helper function for processing a single VM
func processVM(db *gorm.DB, cloud Cloud, vm mo.VirtualMachine,
	hostClusterMap, hostNameMap, vmHostMap map[string]string,
	portGroupMap map[string]PortGroup, // Neuer Parameter
	unlockedUuuidSet map[string]bool, locked bool, timeNow time.Time,
	existingVMs *map[string]bool, existingVMsMutex *sync.Mutex, ipRecordMap map[string]Ip,
	customFieldMap map[int32]string,
) {
	// DetailedData splitten (Beispiel "architecture='X86' distroAddVersion='9.5 (Plow)'"
	detailsMap := make(map[string]string)
	if len(vm.Guest.GuestDetailedData) > 0 {
		re := regexp.MustCompile(`(\w+)='([^']*?)'`)
		matches := re.FindAllStringSubmatch(vm.Guest.GuestDetailedData, -1)
		for _, match := range matches {
			if len(match) == 3 {
				key := match[1]   // First part of the regular expression: Key
				value := match[2] // Second part of the regular expression: Value
				detailsMap[key] = value
			}
		}
	}

	hostname := ""
	clustername := ""
	if hostId, exists := vmHostMap[vm.Summary.Vm.Value]; exists {
		hostname = hostNameMap[hostId]
		clustername = hostClusterMap[hostId]
	}

	// OverallStatus prüfen
	overallStatus := string(vm.OverallStatus)
	if !isValidStatus(overallStatus) {
		log.Printf("WARNING: Unknown OverallStatus '%s' for VM '%s'. Default value 'gray' is used.\n", overallStatus, vm.Summary.Config.Name)
		overallStatus = string(Gray) // Standardwert für unbekannte Status
	}

	// ConfigStatus prüfen
	configStatus := string(vm.ConfigStatus)
	if !isValidStatus(configStatus) {
		log.Printf("WARNING: Unknown ConfigStatus '%s' for VM '%s'. Default value 'gray' is used.\n", configStatus, vm.Summary.Config.Name)
		configStatus = string(Gray) // Standardwert für unbekannte Status
	}

	memoryMB := vm.Summary.Config.MemorySizeMB
	numberOfVDisks, vDiskSumCapacityInBytes := calculateVDiskSummary(vm)

	serverLocked := locked
	if serverLocked && unlockedUuuidSet[strings.ToLower(vm.Summary.Config.Uuid)] {
		serverLocked = false
	}
	newVM := Server{
		Version:                               0,
		CreatedAt:                             timeNow,
		UpdatedAt:                             timeNow,
		CloudID:                               cloud.ID,
		UUID:                                  vm.Summary.Config.Uuid,
		InstanceUuid:                          vm.Summary.Config.InstanceUuid,
		VmId:                                  vm.Summary.Vm.Value,
		Cluster:                               clustername,
		Host:                                  hostname,
		Location:                              identifyLocation(hostname),
		Name:                                  vm.Summary.Config.Name,
		PowerState:                            string(vm.Runtime.PowerState),
		MemoryMB:                              memoryMB,
		NumCpu:                                vm.Summary.Config.NumCpu,
		NumCoresPerSocket:                     convertPtrToInt32(vm.Config.Hardware.NumCoresPerSocket),
		MemoryHotAddEnabled:                   convertPtrToBool(vm.Config.MemoryHotAddEnabled),
		CpuHotAddEnabled:                      convertPtrToBool(vm.Config.CpuHotAddEnabled),
		CpuHotRemoveEnabled:                   convertPtrToBool(vm.Config.CpuHotRemoveEnabled),
		HotPlugMemoryLimit:                    new(vm.Config.HotPlugMemoryLimit),
		HotPlugMemoryIncrementSize:            new(vm.Config.HotPlugMemoryIncrementSize),
		MemoryAllocationExpandableReservation: convertPtrToBool(vm.Config.MemoryAllocation.ExpandableReservation),
		MemoryAllocationLimit:                 vm.Config.MemoryAllocation.Limit,
		MemoryAllocationOverheadLimit:         vm.Config.MemoryAllocation.OverheadLimit,
		MemoryAllocationReservation:           vm.Config.MemoryAllocation.Reservation,
		CpuAllocationExpandableReservation:    convertPtrToBool(vm.Config.CpuAllocation.ExpandableReservation),
		CpuAllocationLimit:                    vm.Config.CpuAllocation.Limit,
		CpuAllocationOverheadLimit:            vm.Config.CpuAllocation.OverheadLimit,
		CpuAllocationReservation:              vm.Config.CpuAllocation.Reservation,
		CpuTopology:                           convertCpuAffinityToString(vm.Config.CpuAffinity),
		VmxVersion:                            RemoveVMXPrefix(vm.Config.Version),
		OverallStatus:                         Status(overallStatus),
		ConfigStatus:                          Status(configStatus),
		ConfigEqualsTools:                     vm.Config.GuestId == vm.Guest.GuestId,
		GuestConfigId:                         vm.Config.GuestId,
		GuestConfigFullName:                   vm.Config.GuestFullName,
		GuestToolsId:                          vm.Guest.GuestId,
		GuestToolsFullName:                    vm.Guest.GuestFullName,
		GuestToolsState:                       string(vm.Guest.ToolsStatus),
		GuestToolsRunningStatus:               vm.Guest.ToolsRunningStatus,
		GuestToolsVersionStatus:               vm.Guest.ToolsVersionStatus,
		GuestToolsVersionStatus2:              vm.Guest.ToolsVersionStatus2,
		GuestToolsInstallType:                 vm.Guest.ToolsInstallType,
		GuestToolsVersion:                     vm.Guest.ToolsVersion,
		GuestToolsFamily:                      vm.Guest.GuestFamily,
		GuestToolsHostname:                    vm.Guest.HostName,
		GuestToolsIpAddress:                   vm.Guest.IpAddress,
		GuestToolsArchitecture:                detailsMap["architecture"],
		GuestToolsBitness:                     detailsMap["bitness"],
		GuestToolsBuildNumber:                 detailsMap["buildNumber"],
		GuestToolsCpeString:                   detailsMap["cpeString"],
		GuestToolsDistroAddlVersion:           detailsMap["distroAddlVersion"],
		GuestToolsDistroName:                  detailsMap["distroName"],
		GuestToolsDistroVersion:               detailsMap["distroVersion"],
		GuestToolsFamilyName:                  detailsMap["familyName"],
		GuestToolsKernelVersion:               detailsMap["kernelVersion"],
		GuestToolsPrettyName:                  detailsMap["prettyName"],
		VDisks:                                numberOfVDisks,
		VDisksCapacityInBytes:                 vDiskSumCapacityInBytes,
		BootTime:                              vm.Runtime.BootTime,
		Locked:                                serverLocked,
		ServerKind:                            ServerKindVirtual,
		ServerType:                            ServerTypeVmVmware,
	}

	// Thread-sicherer Zugriff auf existingVMs
	existingVMsMutex.Lock()
	(*existingVMs)[newVM.UUID] = true
	existingVMsMutex.Unlock()

	var storedVM Server
	result := db.Where("uuid = ? AND cloud_id = ?", newVM.UUID, cloud.ID).First(&storedVM)
	if result.RowsAffected == 0 {
		if newVM.GuestToolsHostname != "" {
			newVM.Fqdn = newVM.GuestToolsHostname
		}
		if err := db.Create(&newVM).Error; err != nil {
			log.Printf("Error saving VM %s: %v", newVM.Name, err)
			return
		}
		storedVM = newVM
	} else {
		if storedVM.CompareAndUpdate(newVM, timeNow) {
			if err := db.Save(&storedVM).Error; err != nil {
				log.Printf("Error updating VM %s: %v", newVM.Name, err)
			}
		}
	}
	updateDisks(db, timeNow, storedVM, vm)
	if strings.EqualFold(newVM.PowerState, "poweredOn") {
		updateMountPoints(db, timeNow, storedVM, vm)
	}
	updateNetworkInterfaces(db, timeNow, storedVM, vm, ipRecordMap, portGroupMap)
	updateSnapshots(db, timeNow, storedVM, vm)
	updateCustomAttributes(db, timeNow, storedVM, vm, customFieldMap)
}

// CompareAndUpdate checks if the stored ServerCustomAttribute differs from the provided new attribute.
// It compares critical fields (ServerID, Name, Value). If differences are found, it updates
// the stored entity's fields, increments the version, sets the UpdatedAt timestamp, and returns true.
//
// params:
//   - newAttr: The ServerCustomAttribute containing the latest data from vCenter.
//   - timeNow: The timestamp to be used for the UpdatedAt field if changes occur.
//
// returns:
//   - bool: true if the record was updated, false if it remains unchanged.
func (storedAttr *ServerCustomAttribute) CompareAndUpdate(newAttr ServerCustomAttribute, timeNow time.Time) bool {
	if storedAttr.ServerID != newAttr.ServerID ||
		storedAttr.Name != newAttr.Name ||
		storedAttr.Value != newAttr.Value {

		storedAttr.UpdatedAt = timeNow
		storedAttr.Version = storedAttr.Version + 1
		storedAttr.ServerID = newAttr.ServerID
		storedAttr.Name = newAttr.Name
		storedAttr.Value = newAttr.Value
		return true
	}
	return false
}

// updateCustomAttributes synchronizes the custom attributes of a specific VM between the vCenter data and the local database.
// It performs the following operations:
// 1. Fetches currently stored attributes for the server.
// 2. Compares them with the live data from vCenter (`vmData`).
// 3. Creates new attributes in batch if they don't exist in the DB.
// 4. Updates existing attributes if their values have changed.
// 5. Deletes attributes from the DB that are no longer present in vCenter.
//
// params:
//   - db: The GORM database connection.
//   - timeNow: The timestamp used for synchronization.
//   - vm: The Server entity representing the VM in the database.
//   - vmData: The raw VirtualMachine data object from vCenter.
//   - customFieldMap: A map translating vCenter field IDs to human-readable names.
func updateCustomAttributes(db *gorm.DB, timeNow time.Time, vm Server, vmData mo.VirtualMachine, customFieldMap map[int32]string) {
	var currentAttributes []ServerCustomAttribute
	if err := db.Where("server_id = ?", vm.ID).Find(&currentAttributes).Error; err != nil {
		log.Printf("Error loading custom attributes for server %d: %v", vm.ID, err)
		return
	}

	// Create a map for O(1) access to existing DB entries (Key: Attribute Name)
	attributeMap := make(map[string]*ServerCustomAttribute)
	for i := range currentAttributes {
		attributeMap[currentAttributes[i].Name] = &currentAttributes[i]
	}

	existingInVcenter := make(map[string]bool)
	var toUpdate []*ServerCustomAttribute
	var toCreate []ServerCustomAttribute

	// Iterate over custom values received from vCenter
	for _, cv := range vmData.Summary.CustomValue {
		val, ok := cv.(*types.CustomFieldStringValue)
		if !ok {
			continue
		}

		// Skip attributes with empty values
		if val.Value == "" {
			continue
		}

		// Resolve the field name using the ID map
		fieldName, found := customFieldMap[val.Key]
		if !found {
			continue
		}

		existingInVcenter[fieldName] = true
		newAttr := ServerCustomAttribute{
			ServerID:  vm.ID,
			Name:      fieldName,
			Value:     val.Value,
			UpdatedAt: timeNow,
		}

		if storedAttr, exists := attributeMap[fieldName]; exists {
			// Attribute exists: Compare and add to update list if changed
			if storedAttr.CompareAndUpdate(newAttr, timeNow) {
				toUpdate = append(toUpdate, storedAttr)
			}
		} else {
			// Attribute is new: Initialize and add to creation list
			newAttr.CreatedAt = timeNow
			newAttr.Version = 1
			toCreate = append(toCreate, newAttr)
		}
	}

	// 1. Batch insert new entries
	if len(toCreate) > 0 {
		if err := db.CreateInBatches(&toCreate, 100).Error; err != nil {
			log.Printf("Error during bulk insert of custom attributes for VM %d: %v", vm.ID, err)
		}
	}

	// 2. Save changed entries individually
	for _, attr := range toUpdate {
		if err := db.Save(attr).Error; err != nil {
			log.Printf("Error updating CustomAttribute %s for VM %d: %v", attr.Name, vm.ID, err)
		}
	}

	// 3. Identify and batch delete removed attributes
	var idsToDelete []uint
	for name, storedAttr := range attributeMap {
		if !existingInVcenter[name] {
			idsToDelete = append(idsToDelete, storedAttr.ID)
		}
	}

	if len(idsToDelete) > 0 {
		// Executes a single DELETE FROM ... WHERE id IN (...) query
		if err := db.Delete(&ServerCustomAttribute{}, idsToDelete).Error; err != nil {
			log.Printf("Error during batch delete of CustomAttributes for VM %d: %v", vm.ID, err)
		}
	}
}

// updateSnapshots synchronizes the snapshot data of a virtual machine with the database.
func updateSnapshots(db *gorm.DB, timeNow time.Time, vm Server, vmData mo.VirtualMachine) {
	existingSnapShots := map[int32]bool{}

	if vmData.Snapshot != nil {
		// Recursively search snapshots
		var snapshots []types.VirtualMachineSnapshotTree
		snapshots = append(snapshots, vmData.Snapshot.RootSnapshotList...)
		for len(snapshots) > 0 {
			// Pop snapshot from list
			snapshot := snapshots[0]
			snapshots = snapshots[1:]

			newSnapshot := Snapshot{
				CreatedAt:       timeNow,
				UpdatedAt:       timeNow,
				ServerID:        vm.ID,
				SnapshotId:      snapshot.Id,
				Name:            snapshot.Name,
				Description:     snapshot.Description,
				CreateTime:      snapshot.CreateTime,
				Quiesced:        snapshot.Quiesced,
				State:           string(snapshot.State),
				ReplaySupported: convertPtrToBool(snapshot.ReplaySupported),
				RetentionPeriod: calculateRetentionTime(snapshot.Name, snapshot.CreateTime),
			}
			existingSnapShots[newSnapshot.SnapshotId] = true

			var storedSnapshot Snapshot
			result := db.First(&storedSnapshot, "server_id = ? AND snapshot_id = ?", vm.ID, newSnapshot.SnapshotId)

			if result.RowsAffected == 0 {
				if err := db.Create(&newSnapshot).Error; err != nil {
					log.Printf("Error saving snapshot: %s (%v)", newSnapshot.Name, err)
				} else {
					storedSnapshot = newSnapshot
				}
			} else {
				if storedSnapshot.CompareAndUpdate(newSnapshot, timeNow) {
					if err := db.Save(&storedSnapshot).Error; err != nil {
						log.Printf("Error updating snapshot: %s (%v)", storedSnapshot.Name, err)
					}
				}
			}
			// Add child snapshots
			snapshots = append(snapshots, snapshot.ChildSnapshotList...)
		}
	}
	deleteRemovedSnapshots(db, vm.ID, existingSnapShots)
}

// calculateVDiskSummary extracts the number of virtual disks and their total capacity (in bytes) for a given VM configuration.
func calculateVDiskSummary(vmData mo.VirtualMachine) (uint8, int64) {
	var numberOfVDisks uint8 = 0
	var sumCapacityInBytes int64 = 0
	for _, device := range vmData.Config.Hardware.Device {
		if disk, ok := device.(*types.VirtualDisk); ok {
			numberOfVDisks++
			sumCapacityInBytes += disk.CapacityInBytes
		}
	}
	return numberOfVDisks, sumCapacityInBytes
}

// updateDisks synchronizes the virtual disk data of a server with the provided information from a virtual machine object.
func updateDisks(db *gorm.DB, timeNow time.Time, vm Server, vmData mo.VirtualMachine) {
	existingDisks := map[int32]bool{}

	for _, device := range vmData.Config.Hardware.Device {
		if disk, ok := device.(*types.VirtualDisk); ok {

			unitNumber := int32(-1) // -1 = unbekannt
			if disk.UnitNumber != nil {
				unitNumber = *disk.UnitNumber
			}
			diskProvisioning := unknownDiskMode
			diskMode := unknownProvisioning
			if backing, ok := disk.Backing.(*types.VirtualDiskFlatVer2BackingInfo); ok {
				// Disk Type
				if backing.ThinProvisioned != nil && *backing.ThinProvisioned {
					diskProvisioning = "Thin Provision"
				} else if backing.EagerlyScrub != nil && *backing.EagerlyScrub {
					diskProvisioning = "Thick Provision Eager Zeroed"
				} else {
					diskProvisioning = "Thick Provision Lazy Zeroed"
				}
				// Disk Mode
				diskMode = mapDiskModeToVCenterDisplay(backing.DiskMode)
			}
			var diskId string
			if disk.VDiskId != nil {
				diskId = disk.VDiskId.Id
			}

			fileName := ""
			if backing, ok := disk.Backing.(types.BaseVirtualDeviceFileBackingInfo); ok {
				fileName = backing.GetVirtualDeviceFileBackingInfo().FileName
			}

			newDisk := Disk{
				CreatedAt:         timeNow,
				UpdatedAt:         timeNow,
				ServerID:          vm.ID,
				Key:               disk.Key,
				UnitNumber:        unitNumber,
				DiskProvisioning:  diskProvisioning,
				FileName:          fileName,
				CapacityInBytes:   disk.CapacityInBytes,
				VDiskId:           diskId,
				DeviceName:        disk.DeviceInfo.GetDescription().Label,
				VirtualDiskFormat: disk.VirtualDiskFormat,
				DiskMode:          diskMode,
			}
			existingDisks[newDisk.Key] = true

			var storedDisk Disk
			result := db.First(&storedDisk, "server_id = ? AND vdisk_key = ?", vm.ID, newDisk.Key)

			if result.RowsAffected == 0 {
				if err := db.Create(&newDisk).Error; err != nil {
					log.Printf("Error saving vDisk: %s (%v)", newDisk.FileName, err)
				} else {
					storedDisk = newDisk
				}
			} else {
				if storedDisk.CompareAndUpdate(newDisk, timeNow) {
					if err := db.Save(&storedDisk).Error; err != nil {
						log.Printf("Error updating vDisk: %s (%v)", storedDisk.FileName, err)
					}
				}
			}
		}
	}
	deleteRemovedVDisks(db, vm.ID, existingDisks)
}

// updateMountPoints updates or creates mount points for a server based on the provided virtual machine data.
// It ensures mount points in the database are consistent with the latest disk information and removes obsolete entries.
func updateMountPoints(db *gorm.DB, timeNow time.Time, vm Server, vmData mo.VirtualMachine) {
	existingMountPoints := map[string]bool{}

	for _, disk := range vmData.Guest.Disk {

		newMountPoint := MountPoint{
			CreatedAt:        timeNow,
			UpdatedAt:        timeNow,
			ServerID:         vm.ID,
			DiskPath:         disk.DiskPath,
			CapacityInBytes:  disk.Capacity,
			FreeSpaceInBytes: disk.FreeSpace,
			FilesystemType:   disk.FilesystemType,
			Source:           ident,
		}
		existingMountPoints[newMountPoint.DiskPath] = true

		var storedMountPoint MountPoint
		result := db.First(&storedMountPoint, "server_id = ? AND disk_path = ?", vm.ID, newMountPoint.DiskPath)

		if result.RowsAffected == 0 {
			if err := db.Create(&newMountPoint).Error; err != nil {
				log.Printf("Error saving MountPoint: %s (%v)", newMountPoint.DiskPath, err)
			} else {
				storedMountPoint = newMountPoint
			}
		} else {
			if storedMountPoint.CompareAndUpdate(newMountPoint, timeNow) {
				if err := db.Save(&storedMountPoint).Error; err != nil {
					log.Printf("Error updating MountPoint: %s (%v)", storedMountPoint.DiskPath, err)
				}
			}
		}
	}
	deleteRemovedMountPoints(db, vm.ID, existingMountPoints)
}

// updateNetworkInterfaces updates the network interface details of a server based on the provided virtual machine data.
func updateNetworkInterfaces(db *gorm.DB, timeNow time.Time, vm Server, vmData mo.VirtualMachine, ipRecordMap map[string]Ip, portGroupMap map[string]PortGroup) {
	existingNICs := map[int32]bool{}
	for _, device := range vmData.Config.Hardware.Device {
		switch nic := device.(type) {
		case *types.VirtualVmxnet:
			processNIC(db, vm, "Vmxnet", nic.Key, nic.UnitNumber, nic.AddressType, nic.DeviceInfo, nic.MacAddress, nic.Backing, nic.Connectable, vmData.Guest, existingNICs, timeNow, portGroupMap, ipRecordMap)
		case *types.VirtualVmxnet2:
			processNIC(db, vm, "Vmxnet2", nic.Key, nic.UnitNumber, nic.AddressType, nic.DeviceInfo, nic.MacAddress, nic.Backing, nic.Connectable, vmData.Guest, existingNICs, timeNow, portGroupMap, ipRecordMap)
		case *types.VirtualVmxnet3:
			processNIC(db, vm, "Vmxnet3", nic.Key, nic.UnitNumber, nic.AddressType, nic.DeviceInfo, nic.MacAddress, nic.Backing, nic.Connectable, vmData.Guest, existingNICs, timeNow, portGroupMap, ipRecordMap)
		case *types.VirtualE1000:
			processNIC(db, vm, "E1000", nic.Key, nic.UnitNumber, nic.AddressType, nic.DeviceInfo, nic.MacAddress, nic.Backing, nic.Connectable, vmData.Guest, existingNICs, timeNow, portGroupMap, ipRecordMap)
		case *types.VirtualE1000e:
			processNIC(db, vm, "E100e", nic.Key, nic.UnitNumber, nic.AddressType, nic.DeviceInfo, nic.MacAddress, nic.Backing, nic.Connectable, vmData.Guest, existingNICs, timeNow, portGroupMap, ipRecordMap)
		case *types.VirtualPCNet32:
			processNIC(db, vm, "PCNet32", nic.Key, nic.UnitNumber, nic.AddressType, nic.DeviceInfo, nic.MacAddress, nic.Backing, nic.Connectable, vmData.Guest, existingNICs, timeNow, portGroupMap, ipRecordMap)
		case *types.VirtualSriovEthernetCard:
			processNIC(db, vm, "SriovEthernetCard", nic.Key, nic.UnitNumber, nic.AddressType, nic.DeviceInfo, nic.MacAddress, nic.Backing, nic.Connectable, vmData.Guest, existingNICs, timeNow, portGroupMap, ipRecordMap)
		}
	}
	deleteRemovedNetworkInterfaces(db, vm.ID, existingNICs)
}

// processNIC handles the creation and updating of network interface card (NIC) records and associated IP assignments.
func processNIC(db *gorm.DB, vm Server, cardType string, vNicKey int32, vNicUnitNumber *int32, addressType string, deviceInfo types.BaseDescription, macAddress string, backingInfo types.BaseVirtualDeviceBackingInfo,
	connectableInfo *types.VirtualDeviceConnectInfo, guestInfo *types.GuestInfo, existingNICs map[int32]bool, timeNow time.Time, portGroupMap map[string]PortGroup, ipRecordMap map[string]Ip,
) {
	unitNumber := unknownUnitNumber
	if vNicUnitNumber != nil {
		unitNumber = *vNicUnitNumber
	}
	connected := false
	if connectableInfo != nil {
		connected = connectableInfo.Connected
	}
	networkName := ""
	portGroupKey := ""
	distributedPortKey := ""
	toolsNetworkName := ""
	toolsConnected := false
	var ipAddresses []string
	var ipObjects []Ip

	if guestInfo != nil && guestInfo.Net != nil {
		ipSet := make(map[string]bool)

		for _, netInfo := range guestInfo.Net {
			if netInfo.MacAddress == macAddress {
				if netInfo.IpAddress != nil {
					for _, ip := range netInfo.IpAddress {
						if ip != "" && ip != "127.0.0.1" && ip != "::1" {
							ip = strings.ToLower(strings.TrimSpace(ip))
							ipSet[ip] = true
						}
					}
				}
				toolsNetworkName = netInfo.Network
				toolsConnected = netInfo.Connected
				break
			}
		}

		for ip := range ipSet {
			ipAddresses = append(ipAddresses, ip)
			if existingIp, exists := ipRecordMap[ip]; exists {
				ipObjects = append(ipObjects, existingIp)
			} else {
				ipType := determineIpType(ip)
				var ipRecord Ip
				result := db.Where("ip = ?", ip).First(&ipRecord)
				if result.Error != nil {
					if errors.Is(result.Error, gorm.ErrRecordNotFound) {
						ipRecord = Ip{
							Ip:     ip,
							IpType: ipType,
						}
						if err := db.Create(&ipRecord).Error; err != nil {
							log.Printf("Error creating IP record for %s: %v", ip, err)
							continue
						}
						ipRecordMap[ip] = ipRecord
					} else {
						log.Printf("Error querying IP record for %s: %v", ip, result.Error)
						continue
					}
				}
				ipObjects = append(ipObjects, ipRecord)
			}
		}
	}

	toolsIpAddress := strings.Join(ipAddresses, "; ")

	// Backing-Typ prüfen
	switch backing := backingInfo.(type) {
	case *types.VirtualEthernetCardNetworkBackingInfo: // Standard-Switch-Netzwerk
		networkName = backing.DeviceName
		portGroupKey = backing.Network.Value

	case *types.VirtualEthernetCardDistributedVirtualPortBackingInfo: // Distributed Virtual Switch - Netzwerk
		networkName = backing.Port.PortgroupKey
		distributedPortKey = backing.Port.PortKey
		portGroupKey = backing.Port.PortgroupKey
	default:
		// Unknown type - No specific handling required
		log.Printf("Unknown backing type for vNIC: %T", backingInfo)
	}

	var portGroupId *uint = nil
	if portGroup, ok := portGroupMap[portGroupKey]; ok {
		portGroupId = &portGroup.ID
	}

	// Neues Netzwerkgerät erstellen
	newNIC := Nic{
		CreatedAt:          timeNow,
		UpdatedAt:          timeNow,
		ServerID:           vm.ID,
		Key:                vNicKey,
		CardType:           cardType,
		UnitNumber:         unitNumber,
		DeviceName:         deviceInfo.GetDescription().Label,
		MacAddress:         macAddress,
		NetworkName:        networkName,
		Connected:          connected,
		PortGroupSummary:   deviceInfo.GetDescription().Summary,
		PortGroupKey:       portGroupKey,
		DistributedPortKey: distributedPortKey,
		AddressType:        addressType,
		ToolsIpAddress:     toolsIpAddress,
		ToolsNetworkName:   toolsNetworkName,
		ToolsConnected:     toolsConnected,
		PortGroupID:        portGroupId,
	}

	// Als existierend markieren
	existingNICs[newNIC.Key] = true

	// In der Datenbank suchen
	var storedNIC Nic
	result := db.First(&storedNIC, "server_id = ? AND vnic_key = ?", vm.ID, newNIC.Key)
	if result.RowsAffected == 0 {
		// Neue Netzwerkkarte speichern
		if err := db.Create(&newNIC).Error; err != nil {
			log.Printf("Error saving vNIC: %s (%v)", newNIC.DeviceName, err)
		} else {
			storedNIC = newNIC
		}
	} else {
		// Änderungen prüfen und speichern
		if storedNIC.CompareAndUpdate(newNIC, timeNow) {
			if err := db.Save(&storedNIC).Error; err != nil {
				log.Printf("Error updating vNIC: %s (%v)", newNIC.DeviceName, err)
			}
		}
	}

	if len(ipObjects) > 0 {
		var existingAssignments []IpAssignment
		if err := db.Where("nic_id = ?", storedNIC.ID).Find(&existingAssignments).Error; err != nil {
			log.Printf("Error fetching existing IP assignments for NIC %d: %v", storedNIC.ID, err)
			return
		}
		existingIPMap := make(map[uint]bool)
		for _, assignment := range existingAssignments {
			existingIPMap[assignment.IpID] = true
		}

		newIPMap := make(map[uint]bool)
		for _, ipObj := range ipObjects {
			newIPMap[ipObj.ID] = true
		}
		for _, assignment := range existingAssignments {
			if !newIPMap[assignment.IpID] {
				if err := db.Delete(&assignment).Error; err != nil {
					log.Printf("Error deleting IP assignment for NIC %d and IP %d: %v", storedNIC.ID, assignment.IpID, err)
				}
			}
		}

		for _, ipObj := range ipObjects {
			if !existingIPMap[ipObj.ID] {
				assignment := IpAssignment{
					NicID: storedNIC.ID,
					IpID:  ipObj.ID,
				}
				if err := db.Create(&assignment).Error; err != nil {
					log.Printf("Error creating IP assignment for NIC %d and IP %d: %v", storedNIC.ID, ipObj.ID, err)
				}
			}
		}
	} else {
		if err := db.Where("nic_id = ?", storedNIC.ID).Delete(&IpAssignment{}).Error; err != nil {
			log.Printf("Error clearing IP assignments for NIC %d: %v", storedNIC.ID, err)
		}
	}
}

// determineIpType determines whether an IP address is IPv4 or IPv6
func determineIpType(ip string) string {
	if strings.Contains(ip, ":") {
		return "IPv6"
	}
	return "IPv4"
}

// deleteRemovedPortGroups removes PortGroup entries from the database that are missing in the existingPortGroups map.
// It queries all PortGroups for the given cloudId and deletes any PortGroup not present in the existingPortGroups map.
func deleteRemovedPortGroups(db *gorm.DB, cloudId uint, existingPortGroups map[string]bool) {
	var storedPortGroups []PortGroup
	if err := db.Where("cloud_id = ?", cloudId).Find(&storedPortGroups).Error; err != nil {
		log.Printf("Error retrieving PortGroups from DB: %v", err)
		return
	}
	for _, storedPortGroup := range storedPortGroups {
		if !existingPortGroups[storedPortGroup.PortGroupKey] {
			if err := db.Delete(&storedPortGroup).Error; err != nil {
				log.Printf("Error deleting PortGroup VM from DB: %s (%v)", storedPortGroup.Name, err)
			}
		}
	}
}

// deleteRemovedVMs deletes virtual machines from the database that are no longer present in the provided existingVMs map.
// It uses the provided cloudId to identify the relevant VMs and ensures only outdated entries are removed.
func deleteRemovedVMs(db *gorm.DB, cloudId uint, existingVMs map[string]bool) {
	var storedVMs []Server
	if err := db.Where("cloud_id = ?", cloudId).Find(&storedVMs).Error; err != nil {
		log.Printf("Error retrieving VMs from DB: %v", err)
		return
	}
	for _, storedVM := range storedVMs {
		if !existingVMs[storedVM.UUID] {
			if err := db.Delete(&storedVM).Error; err != nil {
				log.Printf("Error deleting VM from DB: %s (%v)", storedVM.Name, err)
			}
		}
	}
}

// deleteRemovedVDisks removes virtual disks from the database that are no longer present in the provided existingVDisks map.
func deleteRemovedVDisks(db *gorm.DB, serverId uint, existingVDisks map[int32]bool) {
	var vDiskInDB []Disk
	if err := db.Where("server_id = ?", serverId).Find(&vDiskInDB).Error; err != nil {
		log.Printf("Error retrieving vDisk from DB for ServerID %d: %v", serverId, err)
		return
	}

	// Check if every disk exists in existingDisks
	for _, vDisk := range vDiskInDB {
		if !existingVDisks[vDisk.Key] {
			// Disk no longer exists, so delete
			if err := db.Delete(&vDisk).Error; err != nil {
				log.Printf("Error deleting vDisk '%s' (%d): %v", vDisk.FileName, vDisk.Key, err)
			}
		}
	}
}

// deleteRemovedMountPoints removes obsolete mount points from the database for a specified server using the provided map.
func deleteRemovedMountPoints(db *gorm.DB, serverId uint, existingMountPoints map[string]bool) {
	var mountPoints []MountPoint
	if err := db.Where("server_id = ? and source = ?", serverId, ident).Find(&mountPoints).Error; err != nil {
		log.Printf("Error retrieving VM from DB for ServerID %d: %v", serverId, err)
		return
	}

	// Check if every mount point exists in existingMountPoints
	for _, mountPoint := range mountPoints {
		if !existingMountPoints[mountPoint.DiskPath] {
			// Mount point no longer exists, therefore delete
			if err := db.Delete(&mountPoint).Error; err != nil {
				log.Printf("Error deleting MountPoint '%s' : %v", mountPoint.DiskPath, err)
			}
		}
	}
}

// deleteRemovedNetworkInterfaces removes network interfaces from the database that are no longer present in the existingNICs map.
func deleteRemovedNetworkInterfaces(db *gorm.DB, serverId uint, existingNICs map[int32]bool) {
	var interfacesInDB []Nic

	// Retrieve all network interfaces of the VM from the database
	if err := db.Where("server_id = ?", serverId).Find(&interfacesInDB).Error; err != nil {
		log.Printf("Error retrieving network interfaces from DB for VM ID %d: %v", serverId, err)
		return
	}

	// Check if every network interface exists
	for _, nic := range interfacesInDB {
		if !existingNICs[nic.Key] {
			// Network interface no longer exists, therefore delete
			if err := db.Delete(&nic).Error; err != nil {
				log.Printf("Error deleting network interface '%s': %v", nic.DeviceName, err)
			}
		}
	}
}

// deleteRemovedSnapshots removes snapshots from the database that are not present in the existingSnapshots map for a server.
func deleteRemovedSnapshots(db *gorm.DB, serverId uint, existingSnapshots map[int32]bool) {
	var snapshots []Snapshot
	if err := db.Where("server_id = ?", serverId).Find(&snapshots).Error; err != nil {
		log.Printf("Error retrieving snapshots from DB for Server ID %d: %v", serverId, err)
		return
	}

	// Check if every snapshot exists in existingSnapshots
	for _, snapshot := range snapshots {
		if !existingSnapshots[snapshot.SnapshotId] {
			// Snapshot no longer exists, therefore delete
			if err := db.Delete(&snapshot).Error; err != nil {
				log.Printf("Error deleting snapshot '%s' : %v", snapshot.Name, err)
			}
		}
	}
}

// isValidStatus checks if the given status string matches predefined valid statuses: Green, Yellow, Red, or Gray.
func isValidStatus(status string) bool {
	switch Status(status) {
	case Green, Yellow, Red, Gray:
		return true
	default:
		return false
	}
}

// identifyLocation extracts the location code (3 characters after "esxi" prefix)
// Example: "esxia20cnc001.example.org" -> "A20"
// Returns an empty string if the format is incorrect
func identifyLocation(input string) string {
	// Empty string check
	if input == "" {
		return ""
	}

	// Check minimum length
	const minLength = len(EsxiHostnamePrefix) + locationCodeLength
	if len(input) < minLength {
		return ""
	}

	// Case-insensitive prefix check
	if !strings.EqualFold(input[:len(EsxiHostnamePrefix)], EsxiHostnamePrefix) {
		return ""
	}

	// Extract location code
	locationCode := input[len(EsxiHostnamePrefix) : len(EsxiHostnamePrefix)+locationCodeLength]

	// Optional: Validate that it is alphanumeric
	for _, char := range locationCode {
		if !((char >= 'a' && char <= 'z') || (char >= 'A' && char <= 'Z') || (char >= '0' && char <= '9')) {
			return ""
		}
	}
	return strings.ToUpper(locationCode)
}

// RemoveVMXPrefix removes "vmx-" at the beginning of a string
func RemoveVMXPrefix(input string) string {
	inputLower := strings.ToLower(input)
	if strings.HasPrefix(inputLower, vmxPrefix) {
		return strings.TrimPrefix(inputLower, vmxPrefix)
	}
	return input
}

// mapDiskModeToVCenterDisplay maps a disk mode string to the corresponding vCenter display name.
func mapDiskModeToVCenterDisplay(diskMode string) string {
	switch diskMode {
	case "persistent":
		return "Dependent" // Data is stored permanently, snapshots contain changes.
	case "independent_persistent":
		return "Independent - Persistent" // Changes are independent of snapshots, but stored permanently.
	case "independent_nonpersistent":
		return "Independent - Nonpersistent" // Changes are lost upon restart or power off.
	default:
		return diskMode // For "append", "undoable" or invalid/modern modes
	}
}

// convertPtrToBool converts a pointer to a bool value to a simple bool; returns false if the pointer is nil.
func convertPtrToBool(ptr *bool) bool {
	if ptr == nil {
		return false
	}
	return *ptr
}

// convertPtrToInt64 converts a pointer to an int64 value into its dereferenced int64 value or returns 0 if the pointer is nil.
func convertPtrToInt64(ptr *int64) int64 {
	if ptr == nil {
		return 0
	}
	return *ptr
}

// compareInt64Ptr compares two *int64 pointers for equality, handling nil cases, and returns true if they are equal.
func compareInt64Ptr(a, b *int64) bool {
	if a == nil && b == nil {
		return true
	}
	if a == nil || b == nil {
		return false
	}
	return *a == *b
}

// compareUintPtr compares two uint pointers for equality, considering nil values as well. Returns true if equal, false otherwise.
func compareUintPtr(a, b *uint) bool {
	if a == nil && b == nil {
		return true
	}
	if a == nil || b == nil {
		return false
	}
	return *a == *b
}

// convertPtrToInt32 converts a pointer to an int32 value to a simple int32; returns 0 if the pointer is nil.
func convertPtrToInt32(ptr *int32) int32 {
	if ptr == nil {
		return 0
	}
	return *ptr
}

// convertCpuAffinityToString generates a string representation of the CPU affinity of a virtual machine.
// Returns "Assigned at power on" if the affinity set is empty or nil, or "Manual" with a list of CPUs if manually assigned.
func convertCpuAffinityToString(cpuAffinity *types.VirtualMachineAffinityInfo) string {
	if cpuAffinity == nil || len(cpuAffinity.AffinitySet) == 0 {
		return "Assigned at power on"
	}
	// manual CPU assignment
	var cpuList []string
	for _, cpu := range cpuAffinity.AffinitySet {
		cpuList = append(cpuList, fmt.Sprintf("CPU %d", cpu))
	}
	return fmt.Sprintf("Manual (%s)", strings.Join(cpuList, ", "))
}

// calculateRetentionTime determines the retention time for a snapshot based on its name and create time.
// If the name contains a pattern ###...### with an 8-digit date (YYYYMMDD), it returns that date.
// If the pattern contains a numeric value, it interprets it as hours and adds it to createTime.
// Defaults to createTime + 5 days if no valid pattern is found.
func calculateRetentionTime(name string, createTime time.Time) time.Time {
	// Default return value: createTime + 5 days
	defaultRetention := createTime.AddDate(0, 0, 5)

	// Search regex pattern for ###...###
	pattern := `###(.*)###`
	re := regexp.MustCompile(pattern)
	matches := re.FindStringSubmatch(name)

	// If no match is found, return default retention
	if len(matches) < 2 {
		return defaultRetention
	}

	// Extract the found string between ###
	extractedStr := matches[1]

	// Check if it is a number
	if num, err := strconv.ParseInt(extractedStr, 10, 64); err == nil {
		// It is a number
		if len(extractedStr) == 8 {
			// Interpret 8-digit number as date in YYYYMMDD format
			if year, err := strconv.Atoi(extractedStr[0:4]); err == nil {
				if month, err := strconv.Atoi(extractedStr[4:6]); err == nil {
					if day, err := strconv.Atoi(extractedStr[6:8]); err == nil {
						// Create date with 00:00 time
						parsedDate := time.Date(year, time.Month(month), day, 0, 0, 0, 0, time.UTC)
						return parsedDate
					}
				}
			}
		} else if num > 0 {
			// Number with less than 8 digits and > 0: interpret as hours
			return createTime.Add(time.Duration(num) * time.Hour)
		}
	}

	// Fallback: Default retention (createTime + 5 days)
	return defaultRetention
}

// StringToSet converts a comma-separated string into a set represented as a map[string]bool with trimmed, lowercase keys.
func StringToSet(input string) map[string]bool {
	set := make(map[string]bool)
	if strings.TrimSpace(input) == "" {
		return set
	}
	parts := strings.Split(input, ",")
	for _, part := range parts {
		processed := strings.ToLower(strings.TrimSpace(part))
		if processed != "" {
			set[processed] = true
		}
	}
	return set
}
