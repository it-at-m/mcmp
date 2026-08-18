package app

import (
	"os"
	"runtime"
	"runtime/debug"
	"time"
)

// EaiMetadata holds information about the EAI runtime environment, version, and VCS status.
type EaiMetadata struct {
	Name       string    `json:"name"`
	Version    string    `json:"version"`
	CommitID   string    `json:"commit_id"`
	CommitTime string    `json:"commit_time,omitempty"`
	Modified   bool      `json:"modified"`
	GoVersion  string    `json:"go_version"`
	Fqdn       string    `json:"fqdn"`
	StartTime  time.Time `json:"start_time"`
	EndTime    time.Time `json:"end_time"`
	Duration   string    `json:"duration"`
	Status     string    `json:"status"` // SUCCESS, WARNING, ERROR
}

// MetadataHolder is the interface for data models that support automatic metadata injection.
type MetadataHolder interface {
	GetEaiMetadata() EaiMetadata
	SetEaiMetadata(meta EaiMetadata)
}

// NewEaiMetadata creates a new metadata object with the initial runtime info.
func NewEaiMetadata(appName string, startTime time.Time) EaiMetadata {
	fqdn, _ := os.Hostname()
	if fqdn == "" {
		fqdn = "unknown"
	}

	version := "devel"
	commitID := "unknown"
	commitTime := ""
	modified := false

	if buildInfo, ok := debug.ReadBuildInfo(); ok {
		if buildInfo.Main.Version != "" && buildInfo.Main.Version != "(devel)" {
			version = buildInfo.Main.Version
		}
		for _, setting := range buildInfo.Settings {
			switch setting.Key {
			case "vcs.revision":
				commitID = setting.Value
				if version == "devel" && len(commitID) >= 7 {
					version = "devel-" + commitID[:7]
				}
			case "vcs.time":
				commitTime = setting.Value
			case "vcs.modified":
				modified = setting.Value == "true"
			}
		}
	}

	return EaiMetadata{
		Name:       appName,
		Version:    version,
		CommitID:   commitID,
		CommitTime: commitTime,
		Modified:   modified,
		StartTime:  startTime,
		Fqdn:       fqdn,
		GoVersion:  runtime.Version(),
		Status:     "RUNNING",
	}
}

// FinalizeMetadata updates the end time and duration of the metadata.
func FinalizeMetadata(meta *EaiMetadata) {
	if meta.StartTime.IsZero() {
		return
	}
	meta.EndTime = time.Now()
	meta.Duration = meta.EndTime.Sub(meta.StartTime).String()
	if meta.Status == "" || meta.Status == "RUNNING" {
		meta.Status = "SUCCESS"
	}
}
