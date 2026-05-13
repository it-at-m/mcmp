package ontap

import "time"

// --- Aggregated Export Structure (Clean Hierarchy) ---

type OntapData struct {
	Hostname   string          `json:"hostname,omitempty"`
	DataCenter string          `json:"datacenter,omitempty"`
	Aggregates []AggregateData `json:"aggregates,omitempty"`
	SVMs       []SVMData       `json:"svms,omitempty"`
}

type SVMData struct {
	Name    string       `json:"name,omitempty"`
	UUID    string       `json:"uuid,omitempty"`
	Volumes []VolumeData `json:"volumes,omitempty"`
}

type VolumeData struct {
	UUID               string            `json:"uuid,omitempty"`
	Name               string            `json:"name,omitempty"`
	Size               int64             `json:"size,omitempty"`
	State              string            `json:"state,omitempty"`
	Type               string            `json:"type,omitempty"`
	Style              string            `json:"style,omitempty"`
	Space              interface{}       `json:"space,omitempty"`
	SnapshotPolicy     string            `json:"snapshot_policy,omitempty"`
	ExportPolicy       *ExportPolicyData `json:"export_policy,omitempty"`
	CIFSShares         []ShareData       `json:"cifs_shares,omitempty"`
	QTrees             []QTreeData       `json:"qtrees,omitempty"`
	Snapshots          []SnapshotData    `json:"snapshots,omitempty"`
	AggregateUUIDs     []string          `json:"aggregate_uuids,omitempty"`
	NASPath            string            `json:"nas_path,omitempty"`
	MountPathNFS       *string           `json:"mount_path_nfs,omitempty"`
	IsFlexClone        bool              `json:"is_flexclone"`
	ParentVolumeName   string            `json:"parent_volume_name,omitempty"`
	ParentVolumeUUID   string            `json:"parent_volume_uuid,omitempty"`
	ParentSnapshotName string            `json:"parent_snapshot_name,omitempty"`
	ParentSnapshotUUID string            `json:"parent_snapshot_uuid,omitempty"`
	ParentSVMName      string            `json:"parent_svm_name,omitempty"`
	ParentSVMUUID      string            `json:"parent_svm_uuid,omitempty"`
	IsSplitInitiated   bool              `json:"is_split_initiated"`
	Snaplock           *SnaplockData     `json:"snaplock,omitempty"`
}

type ExportPolicyData struct {
	ID    int64         `json:"id,omitempty"`
	Name  string        `json:"name,omitempty"`
	Rules []interface{} `json:"rules,omitempty"` // Raw rules
}

type ShareData struct {
	Name          string        `json:"name,omitempty"`
	Path          string        `json:"path,omitempty"`
	MountPathCIFS string        `json:"mount_path_cifs,omitempty"`
	ACLs          []interface{} `json:"acls,omitempty"` // Raw ACLs
}

type QTreeData struct {
	ID           int64             `json:"id,omitempty"`
	Name         string            `json:"name,omitempty"`
	Path         string            `json:"path,omitempty"`
	MountPathNFS *string           `json:"mount_path_nfs,omitempty"`
	Security     string            `json:"security_style,omitempty"`
	ExportPolicy *ExportPolicyData `json:"export_policy,omitempty"`
	Quota        *QuotaData        `json:"quota,omitempty"`
	CIFSShares   []ShareData       `json:"cifs_shares,omitempty"`
}

type QuotaData struct {
	Index       int64  `json:"index,omitempty"`
	Type        string `json:"type,omitempty"`
	HardLimit   int64  `json:"hard_limit,omitempty"`
	Used        int64  `json:"used_bytes,omitempty"`
	UsedPercent int    `json:"used_percent,omitempty"`
}

type SnapshotData struct {
	UUID       string    `json:"uuid,omitempty"`
	Name       string    `json:"name,omitempty"`
	CreateTime time.Time `json:"create_time,omitempty"`
}

type SnaplockData struct {
	AppendModeEnabled bool   `json:"append_mode_enabled"`
	AutocommitPeriod  string `json:"autocommit_period,omitempty"`
	Type              string `json:"type,omitempty"`
	Retention         *struct {
		Default string `json:"default,omitempty"`
		Minimum string `json:"minimum,omitempty"`
		Maximum string `json:"maximum,omitempty"`
	} `json:"retention,omitempty"`
}

type AggregateData struct {
	UUID          string `json:"uuid,omitempty"`
	Name          string `json:"name,omitempty"`
	DiskClass     string `json:"disk_class,omitempty"`
	MirrorEnabled bool   `json:"mirror_enabled"`
}
