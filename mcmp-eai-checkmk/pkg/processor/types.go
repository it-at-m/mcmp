package processor

// CheckmkAggregatedData represents the aggregated performance data per host.
type CheckmkAggregatedData struct {
	Hosts map[string]HostMetrics `json:"hosts"`
}

// HostMetrics holds the CPU and memory metrics for a specific host.
type HostMetrics struct {
	CPUUtil        float64 `json:"cpu_util"`         // CPU utilization as percentage
	MemUsedPercent float64 `json:"mem_used_percent"` // Memory used as percentage
}
