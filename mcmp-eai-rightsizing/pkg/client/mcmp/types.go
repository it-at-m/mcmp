package mcmp

import "time"

// GreenItServer represents a virtual machine with its resource allocation, state, and historical metrics.
//
// GreenItServer contains comprehensive information about a server including its identification,
// current resource allocation, historical resource changes, power state, and a collection of
// time-series metrics for CPU and memory utilization. This data structure is used throughout
// the rightsizing analysis pipeline to store server information retrieved from the MCMP API.
//
// The Metrics field contains historical utilization samples that are analyzed using percentile-based
// algorithms (similar to Kubernetes VPA) to calculate optimal resource recommendations.
type GreenItServer struct {
	ID                                   int64           `json:"id"`                                        // Unique server identifier
	CloudName                            *string         `json:"cloud_name"`                                // Name of the cloud environment (e.g., vcenterx.example.de)
	VMName                               *string         `json:"vm_name"`                                   // Virtual machine name
	FQDN                                 *string         `json:"fqdn"`                                      // Fully qualified domain name
	PowerState                           *string         `json:"power_state"`                               // Current power state
	MemoryMB                             int             `json:"memory_mb"`                                 // Currently allocated memory in megabytes
	MemoryMBPrev                         *int            `json:"memory_mb_prev"`                            // Previously allocated memory in megabytes (before last change)
	MemoryMBChangeDate                   *time.Time      `json:"memory_mb_change_date"`                     // Timestamp of the last memory allocation change
	MemoryMBChangeDatePrev               *time.Time      `json:"memory_mb_change_date_prev"`                // Timestamp of the last memory allocation change bevor the last
	NumCPU                               int             `json:"num_cpu"`                                   // Currently allocated number of CPU cores
	NumCPUPrev                           *int            `json:"num_cpu_prev"`                              // Previously allocated number of CPU cores (before last change)
	NumCPUChangeDate                     *time.Time      `json:"num_cpu_change_date"`                       // Timestamp of the last CPU allocation change
	NumCPUChangeDatePrev                 *time.Time      `json:"num_cpu_change_date_prev"`                  // Timestamp of the last CPU allocation change bevor the last
	BootTime                             *time.Time      `json:"boot_time"`                                 // Timestamp of the last system boot
	GreenItShutdownChangePending         bool            `json:"green_it_shutdown_change_pending"`          // Indicates if a shutdown change is awaiting approval or execution
	GreenItShutdownChangeRejectedDate    *time.Time      `json:"green_it_shutdown_change_rejected_date"`    // Timestamp when a shutdown change was rejected
	GreenItRightsizingChangePending      bool            `json:"green_it_rightsizing_change_pending"`       // Indicates if a rightsizing change is awaiting approval or execution
	GreenItRightsizingChangeRejectedDate *time.Time      `json:"green_it_rightsizing_change_rejected_date"` // Timestamp when a rightsizing change was rejected
	Metrics                              []ServerMetrics `json:"metrics"`                                   // Historical time-series metrics for utilization analysis
}

// ServerMetrics represents a single utilization measurement sample at a specific point in time.
//
// ServerMetrics contains a timestamped snapshot of CPU and memory utilization percentages
// for a server. Multiple ServerMetrics samples are collected over time and stored in
// GreenItServer.Metrics to enable historical analysis and rightsizing recommendations.
//
// These samples are used by the processor to calculate percentiles for
// determining optimal resource allocation recommendations.
type ServerMetrics struct {
	CreatedAt      *time.Time `json:"created_at"`
	CPUUtil        *float64   `json:"cpu_util"`
	MemUsedPercent *float64   `json:"mem_used_percent"`
}

// Rightsizing represents a batch of rightsizing recommendations for multiple servers.
//
// Rightsizing is the top-level container for submission of rightsizing analysis results
// back to the MCMP API. It contains a list of RightsizingServer recommendations that have
// been computed based on the analysis of historical metrics.
//
// This structure is typically created by the Processor and serialized to JSON before
// being sent to the RightsizingEndpoint via SendJSON.
type Rightsizing struct {
	Servers []RightsizingServer `json:"servers"`
}

// RightsizingServer represents a single rightsizing recommendation for a specific server.
//
// RightsizingServer contains the computed resource recommendation (NumCPU, MemoryMB) and
// flags indicating what actions should be taken (rightsizing, shutdown). These recommendations
// are computed by analyzing the historical metrics collected for the server.
//
// The ID field must match a valid GreenItServer.ID from the MCMP API to ensure the
// recommendation is applied to the correct server.
type RightsizingServer struct {
	ID       int64 `json:"id"`
	NumCPU   int   `json:"num_cpu"`
	MemoryMB int   `json:"memory_mb"`
}
