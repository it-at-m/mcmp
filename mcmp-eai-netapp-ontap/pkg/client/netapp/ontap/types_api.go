package ontap

import (
	"time"
)

// --- Shared Helper Structures ---

type ResourceRef struct {
	UUID string `json:"uuid,omitempty"`
	Name string `json:"name,omitempty"`
}

type SVMRef struct {
	UUID string `json:"uuid,omitempty"`
	Name string `json:"name,omitempty"`
}

// --- NetApp API Response Structures ---

type VolumeResponse struct {
	Records []Volume `json:"records,omitempty"`
}

func (r *VolumeResponse) GetRecords() []Volume {
	if r.Records == nil {
		return []Volume{}
	}
	return r.Records
}

type Volume struct {
	UUID       string `json:"uuid,omitempty"`
	Name       string `json:"name,omitempty"`
	Size       int64  `json:"size,omitempty"`
	Style      string `json:"style,omitempty"`
	State      string `json:"state,omitempty"`
	Type       string `json:"type,omitempty"`
	Aggregates []struct {
		UUID string `json:"uuid,omitempty"`
	} `json:"aggregates,omitempty"`
	SnapshotPolicy struct {
		Name string `json:"name,omitempty"`
	} `json:"snapshot_policy,omitempty"`
	NAS struct {
		Path         string `json:"path,omitempty"`
		ExportPolicy struct {
			ID int64 `json:"id,omitempty"`
		} `json:"export_policy,omitempty"`
	} `json:"nas,omitempty"`
	SVM   SVMRef `json:"svm,omitempty"`
	Space struct {
		AvailablePercent int   `json:"available_percent,omitempty"`
		AFSTotal         int64 `json:"afs_total,omitempty"`
		LogicalSpace     struct {
			Used        int64 `json:"used,omitempty"`
			Available   int64 `json:"available,omitempty"`
			UsedPercent int   `json:"used_percent,omitempty"`
			UsedByAFS   int64 `json:"used_by_afs,omitempty"`
		} `json:"logical_space,omitempty"`
		Snapshot struct {
			ReservePercent int   `json:"reserve_percent,omitempty"`
			ReserveSize    int64 `json:"reserve_size,omitempty"`
			Used           int64 `json:"used,omitempty"`
		} `json:"snapshot,omitempty"`
	} `json:"space,omitempty"`
	Clone struct {
		IsFlexClone    bool        `json:"is_flexclone,omitempty"`
		SplitInitiated bool        `json:"split_initiated,omitempty"`
		ParentVolume   ResourceRef `json:"parent_volume,omitempty"`
		ParentSVM      SVMRef      `json:"parent_svm,omitempty"`
		ParentSnapshot ResourceRef `json:"parent_snapshot,omitempty"`
	} `json:"clone,omitempty"`
	Snaplock Snaplock `json:"snaplock,omitempty"`
}

type Snaplock struct {
	AppendModeEnabled bool   `json:"append_mode_enabled,omitempty"`
	AutocommitPeriod  string `json:"autocommit_period,omitempty"`
	Type              string `json:"type,omitempty"`
	Retention         struct {
		Default string `json:"default,omitempty"`
		Minimum string `json:"minimum,omitempty"`
		Maximum string `json:"maximum,omitempty"`
	} `json:"retention,omitempty"`
}

type ExportPolicyResponse struct {
	Records []ExportPolicy `json:"records,omitempty"`
}

func (r *ExportPolicyResponse) GetRecords() []ExportPolicy {
	return r.Records
}

type ExportPolicy struct {
	ID    int64  `json:"id,omitempty"`
	Name  string `json:"name,omitempty"`
	SVM   SVMRef `json:"svm,omitempty"`
	Rules []struct {
		Index   int64 `json:"index,omitempty"`
		Clients []struct {
			Match string `json:"match,omitempty"`
		} `json:"clients,omitempty"`
		Protocols []string `json:"protocols,omitempty"`
		RWRule    []string `json:"rw_rule,omitempty"`
		RORule    []string `json:"ro_rule,omitempty"`
	} `json:"rules,omitempty"`
}

type CIFSShareResponse struct {
	Records []CIFSShare `json:"records,omitempty"`
}

func (r *CIFSShareResponse) GetRecords() []CIFSShare {
	return r.Records
}

type CIFSShare struct {
	Name   string      `json:"name,omitempty"`
	Path   string      `json:"path,omitempty"`
	Volume ResourceRef `json:"volume,omitempty"`
	SVM    SVMRef      `json:"svm,omitempty"`
	ACLs   []struct {
		UserOrGroup string `json:"user_or_group,omitempty"`
		Permission  string `json:"permission,omitempty"`
	} `json:"acls,omitempty"`
}

type SnapshotResponse struct {
	Records []Snapshot `json:"records,omitempty"`
}

func (r *SnapshotResponse) GetRecords() []Snapshot {
	return r.Records
}

type Snapshot struct {
	Volume     ResourceRef `json:"volume,omitempty"`
	UUID       string      `json:"uuid,omitempty"`
	SVM        SVMRef      `json:"svm,omitempty"`
	Name       string      `json:"name,omitempty"`
	CreateTime time.Time   `json:"create_time,omitempty"`
}

type QTreeResponse struct {
	Records []QTree `json:"records,omitempty"`
}

func (r *QTreeResponse) GetRecords() []QTree {
	return r.Records
}

type QTree struct {
	ID            int64        `json:"id,omitempty"`
	Name          string       `json:"name,omitempty"`
	SecurityStyle string       `json:"security_style,omitempty"`
	Volume        ResourceRef  `json:"volume,omitempty"`
	SVM           SVMRef       `json:"svm,omitempty"`
	ExportPolicy  ExportPolicy `json:"export_policy,omitempty"`
	NAS           struct {
		Path string `json:"path,omitempty"`
	} `json:"nas"`
	Quota Quota `json:"quota,omitempty"`
}

type QuotasResponse struct {
	Records []Quota `json:"records,omitempty"`
}

func (r *QuotasResponse) GetRecords() []Quota {
	return r.Records
}

type Quota struct {
	SVM    SVMRef      `json:"svm,omitempty"`
	Volume ResourceRef `json:"volume,omitempty"`
	Index  int64       `json:"index,omitempty"`
	Type   string      `json:"type,omitempty"`
	QTree  struct {
		Name string `json:"name,omitempty"`
		ID   int64  `json:"id,omitempty"`
	} `json:"qtree,omitempty"`
	Space struct {
		HardLimit int64 `json:"hard_limit,omitempty"`
		Used      struct {
			Total            int64 `json:"total,omitempty"`
			HardLimitPercent int   `json:"hard_limit_percent,omitempty"`
		} `json:"used,omitempty"`
	} `json:"space,omitempty"`
}

type AggregateResponse struct {
	Records    []Aggregate `json:"records"`
	NumRecords int         `json:"num_records"`
}

func (r *AggregateResponse) GetRecords() []Aggregate { return r.Records }

type Aggregate struct {
	UUID         string       `json:"uuid"`
	Name         string       `json:"name"`
	BlockStorage BlockStorage `json:"block_storage"`
	VolumeCount  int          `json:"volume_count"`
}

type BlockStorage struct {
	Primary PrimaryStorage `json:"primary"`
	Mirror  MirrorStorage  `json:"mirror"`
}

type PrimaryStorage struct {
	DiskClass string `json:"disk_class"`
}

type MirrorStorage struct {
	Enabled bool `json:"enabled"`
}
