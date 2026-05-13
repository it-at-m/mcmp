package checkmk

// PerformanceResponse represents the JSON structure returned by the Checkmk API for performance data.
type PerformanceResponse struct {
	Value []PerformanceItem `json:"value"`
}

// PerformanceItem represents a single item in the performance data array.
type PerformanceItem struct {
	Extensions Extensions `json:"extensions"`
}

// Extensions holds the details of a performance metric.
type Extensions struct {
	PerformanceData map[string]float64 `json:"performance_data"`
	Description     string             `json:"description"`
	HostName        string             `json:"host_name"`
}

// PerformanceRequest represents the JSON payload sent to the Checkmk API for querying performance data.
type PerformanceRequest struct {
	Query   Query    `json:"query"`
	Columns []string `json:"columns"`
}

// Query represents the query structure for filtering Checkmk service data.
type Query struct {
	Op    string `json:"op"`
	Left  string `json:"left"`
	Right string `json:"right"`
}
