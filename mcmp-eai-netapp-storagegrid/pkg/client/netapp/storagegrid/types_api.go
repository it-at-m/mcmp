package storagegrid

// --- API Request Structures ---

type AuthorizeRequest struct {
	Username  string `json:"username"`
	Password  string `json:"password"`
	Cookie    bool   `json:"cookie"`
	CsrfToken bool   `json:"csrfToken"`
}

type AuthorizeResponse struct {
	Data string `json:"data"` // Contains the Auth Token
}

// --- API Response Structures ---

// AccountsResponse wraps the list of accounts
// StorageGRID often wraps lists in a "data" field
type AccountsResponse struct {
	Data []Account `json:"data"`
}

type Account struct {
	ID               string                 `json:"id"`
	Name             string                 `json:"name"`
	Description      *string                `json:"description"`
	Capabilities     []string               `json:"capabilities"`
	SynchronizeRules map[string]interface{} `json:"synchronizeRules"`
	Policy           AccountPolicy          `json:"policy"`
}

type AccountPolicy struct {
	UseAccountIdentitySource         bool          `json:"useAccountIdentitySource"`
	AllowPlatformServices            bool          `json:"allowPlatformServices"`
	AllowSelectObjectContent         bool          `json:"allowSelectObjectContent"`
	AllowedGridFederationConnections []interface{} `json:"allowedGridFederationConnections"`
	QuotaObjectBytes                 int64         `json:"quotaObjectBytes"`
	AllowComplianceMode              bool          `json:"allowComplianceMode"`
	MaxRetentionDays                 *int64        `json:"maxRetentionDays"`
	MaxRetentionYears                int64         `json:"maxRetentionYears"`
}

// AccountUsageResponse matches the structure for /grid/accounts/{id}/usage
type AccountUsageResponse struct {
	Data UsageData `json:"data"`
}

type UsageData struct {
	CalculationTime string        `json:"calculationTime"`
	ObjectCount     int64         `json:"objectCount"`
	DataBytes       int64         `json:"dataBytes"`
	Buckets         []BucketUsage `json:"buckets"`
}

type BucketUsage struct {
	Name                string  `json:"name"`
	ObjectCount         int64   `json:"objectCount"`
	DataBytes           int64   `json:"dataBytes"`
	Consistency         string  `json:"consistency"`
	Encryption          *string `json:"encryption"`
	VersioningEnabled   bool    `json:"versioningEnabled"`
	VersioningSuspended bool    `json:"versioningSuspended"`
	Region              string  `json:"region"`
	QuotaObjectBytes    *int64  `json:"quotaObjectBytes"`
}
