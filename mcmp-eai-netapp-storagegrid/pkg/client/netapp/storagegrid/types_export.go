package storagegrid

// --- Aggregated Export Structure ---

// StorageGridData holds the final aggregated data structure for export
type StorageGridData struct {
	Hostname string             `json:"hostname"`
	Accounts []AccountWithUsage `json:"accounts"`
}

// AccountWithUsage combines Account details with aggregated Usage data
type AccountWithUsage struct {
	ID                               string                 `json:"id"`
	Name                             string                 `json:"name"`
	Description                      *string                `json:"description"`
	Capabilities                     []string               `json:"capabilities"`
	SynchronizeRules                 map[string]interface{} `json:"synchronizeRules"`
	UseAccountIdentitySource         bool                   `json:"useAccountIdentitySource"`
	AllowPlatformServices            bool                   `json:"allowPlatformServices"`
	AllowSelectObjectContent         bool                   `json:"allowSelectObjectContent"`
	AllowedGridFederationConnections []interface{}          `json:"allowedGridFederationConnections"`
	AllowComplianceMode              bool                   `json:"allowComplianceMode"`
	MaxRetentionDays                 *int64                 `json:"maxRetentionDays"`
	MaxRetentionYears                int64                  `json:"maxRetentionYears"`
	QuotaObjectBytes                 int64                  `json:"quotaObjectBytes"`
	DataBytes                        int64                  `json:"dataBytes"`
	ObjectCount                      int64                  `json:"objectCount"`
	CalculationTime                  string                 `json:"calculationTime"`
	Buckets                          []BucketUsage          `json:"buckets"`
}
