package mcmp

import "time"

// OracleServer represents the data structure returned by the Oracle DB controller's server list endpoint.
type OracleServer struct {
	FQDN string `json:"fqdn"`
	PDB  string `json:"pdb"`
}

type OracleDatabaseMetrics struct {
	FQDN      string        `json:"fqdn"`
	Timestamp time.Time     `json:"timestamp"`
	Data      []QueryResult `json:"data"`
}

type QueryResult struct {
	QueryName string           `json:"queryName"`
	Rows      []map[string]any `json:"rows"`
}

type OracleExport struct {
	Databases []OracleDatabaseMetrics `json:"databases"`
}
