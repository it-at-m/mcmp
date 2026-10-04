package processor

// CheckmkAggregatedData represents the aggregated performance data per host.
type CheckmkAggregatedData struct {
	Hosts map[string]HostMetrics `json:"hosts"`
}

// HostMetrics holds the CPU and memory metrics for a specific host.
type HostMetrics struct {
	CPUUtil           float64             `json:"cpu_util"`                     // CPU utilization as percentage
	MemUsedPercent    float64             `json:"mem_used_percent"`             // Memory used as percentage
	FilesystemMetrics []FilesystemMetrics `json:"filesystem_metrics,omitempty"` // Filesystem metrics
}

// FilesystemMetrics holds the used and free disk usage metrics for
// a specific file system.
type FilesystemMetrics struct {
	Path string  `json:"path"` // Mount path of the filesystem
	Size float64 `json:"size"` // Total space on the filesystem (MiB)
	Free float64 `json:"free"` // Free space on the filesystem (MiB)
}
