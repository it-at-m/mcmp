package snow

import (
	"net/http"
	"sort"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

type (
	HttpClient interface {
		Do(req *http.Request) (res *http.Response, err error)
	}

	// ClientConfig holds the configuration for creating a new SNow client
	ClientConfig struct {
		Debug           bool   `mapstructure:"Debug"`
		AuthServerURL   string `mapstructure:"OAuthUrl"`
		ClientID        string `mapstructure:"OAuthClientId"`
		ClientSecret    string `mapstructure:"OAuthClientSecret"`
		ApiEndpoint     string `mapstructure:"ApiEndpoint"`
		ProxyURL        string `mapstructure:"ProxyUrl"`
		EnableTLSVerify bool   `mapstructure:"EnableTLSVerify"`
		RequestTimeout  time.Duration
		Scopes          []string
	}

	Client struct {
		*logging.DebugLogger
		httpClient            HttpClient
		urlAppservice         string
		urlTag                string
		urlCmdbCi             string
		urlGroup              string
		urlLockedShutdown     string
		urlLockedRightsize    string
		urlVMwareServer       string
		urlCmdbKeyValue       string
		urlCmdbDataTable      string
		urlIdentifyReconcile  string
		urlOraclePdbToServer  string
		urlDbInstanceToServer string
		debug                 bool
	}

	GetCmdbDataTableParams struct {
		TableName            string
		Query                string
		Fields               []string
		Limit                int
		ExcludeReferenceLink bool
	}

	GetCmdbKeyValueQueryParams struct {
		Key          string
		SysClassName string
		Fields       []string
		Limit        int
	}

	User struct {
		SysID      string `json:"sys_id"`
		Name       string `json:"name"`
		Email      string `json:"email"`
		Department string `json:"department"`
		Company    string `json:"company"`
		UserID     string `json:"user_id"`
		LockedOut  bool   `json:"locked_out"`
		Active     bool   `json:"active"`
	}

	AppService struct {
		SysID                  string   `json:"sys_id"`
		Name                   string   `json:"name"`
		Number                 string   `json:"number"`
		ChangeControl          string   `json:"change_control"`
		AssignmenGroup         string   `json:"assignment_group"`
		UsedFor                string   `json:"used_for"`
		Environment            string   `json:"environment"`
		CSWEnforced            bool     `json:"csw_enforced"`
		OwnedBy                User     `json:"owned_by"`
		ServiceOwnerDelegate   User     `json:"service_owner_delegate"`
		BusinessServiceNumbers []string `json:"deprecated_do_not_use_for_new_development_business_service_number"`
	}

	AppServiceResponse struct {
		Result []AppService `json:"result"`
	}

	TagEntry struct {
		SysID string `json:"sys_id"`
		Key   string `json:"key"`
		Value string `json:"value"`
		CI    string `json:"ci"`
	}

	TagResponse struct {
		Result []TagEntry `json:"result"`
	}

	CmdbCi struct {
		AssetTag          string `json:"asset_tag"`
		SysId             string `json:"sys_id"`
		HardwareStatus    string `json:"hardware_status"`
		HardwareSubstatus string `json:"hardware_substatus"`
		Os                string `json:"os"`
		Fqdn              string `json:"fqdn"`
		OsVersion         string `json:"os_version"`
		SerialNumber      string `json:"serial_number"`
		LastDiscovered    string `json:"last_discovered"`
		SysClassName      string `json:"sys_class_name"`
		IpAddress         string `json:"ip_address"`
		HostName          string `json:"host_name"`
		Name              string `json:"name"`
		VmInstanceUUID    string `json:"vm_instance_uuid,omitempty"`
		MacAddress        string `json:"mac_address,omitempty"`
	}

	CmdbCiResponse struct {
		Result CmdbCi `json:"result"`
	}

	VMwareInstance struct {
		Result []CmdbCi `json:"result"`
	}

	FoundationData struct {
		Name    string `json:"name"`
		SysID   string `json:"sys_id"`
		Manager User   `json:"manager"`
		Members []User `json:"members"`
	}

	FoundationDataResponse struct {
		Result FoundationData `json:"result"`
	}

	GreenItData struct {
		CiSysID      string `json:"ci_sys_id"`
		TaskClosedAt string `json:"task_closed_at"`
	}

	GreenItResponse struct {
		Result []GreenItData `json:"result"`
	}

	Server struct {
		SysID string `json:"sys_id"`
		Name  string `json:"name"`
	}

	ServerResponse struct {
		Result Server `json:"result"`
	}

	// IdentifyReconcilePayload represents the payload for the ServiceNow Identify and Reconcile API
	IdentifyReconcilePayload struct {
		Items []IdentifyReconcileItem `json:"items"`
	}

	IdentifyReconcileItem struct {
		ClassName           string               `json:"className"`
		Lookup              []any                `json:"lookup"`
		Values              map[string]any       `json:"values"`
		InternalID          string               `json:"internal_id,omitempty"`
		SysObjectSourceInfo *SysObjectSourceInfo `json:"sys_object_source_info,omitempty"`
	}

	SysObjectSourceInfo struct {
		SourceName             string `json:"source_name"`
		SourceRecencyTimestamp string `json:"source_recency_timestamp"`
	}

	// IdentifyReconcileResponse represents the response from the Identify and Reconcile API
	IdentifyReconcileResponse struct {
		Result struct {
			Items      []IdentifyReconcileResultItem `json:"items"`
			HasError   bool                          `json:"hasError"`
			HasWarning bool                          `json:"hasWarning"`
		} `json:"result"`
	}

	IdentifyReconcileResultItem struct {
		ClassName            string `json:"className"`
		Operation            string `json:"operation"` // INSERT, UPDATE, NO_CHANGE
		SysId                string `json:"sysId"`
		IdentifierEntrySysId string `json:"identifierEntrySysId"`
		ErrorCount           int    `json:"errorCount"`
		WarningCount         int    `json:"warningCount"`
		InputIndices         []int  `json:"inputIndices"`
	}

	OraclePdbToServer struct {
		OraPdbSysID      string `json:"orapdb_sys_id"`
		OraInstanceSysID string `json:"orainstance_sys_id"`
		ServerSysID      string `json:"server_sys_id"`
	}

	OraclePdbToServerResponse struct {
		Result []OraclePdbToServer `json:"result"`
	}

	DbInstanceToServer struct {
		DbInstanceSysID string `json:"dbinstance_sys_id"`
		ServerSysID     string `json:"server_sys_id"`
	}

	DbInstanceToServerResponse struct {
		Result []DbInstanceToServer `json:"result"`
	}
)

type ConfigurationItemWithAppServices struct {
	RawCI              map[string]any
	AppServicesNumbers map[string]struct{}
}

func NewConfigurationItemWithAppServices(rawCI map[string]any) ConfigurationItemWithAppServices {
	return ConfigurationItemWithAppServices{
		RawCI:              rawCI,
		AppServicesNumbers: make(map[string]struct{}),
	}
}

func (c *ConfigurationItemWithAppServices) GetSysID() string {
	if val, ok := c.RawCI["sys_id"].(string); ok {
		return val
	}
	return ""
}

func (c *ConfigurationItemWithAppServices) GetName() string {
	if val, ok := c.RawCI["name"].(string); ok {
		return val
	}
	return ""
}

func (c *ConfigurationItemWithAppServices) GetSysClassName() string {
	if val, ok := c.RawCI["sys_class_name"].(string); ok {
		return val
	}
	return ""
}

func (c *ConfigurationItemWithAppServices) GetLastDiscovered() string {
	if val, ok := c.RawCI["last_discovered"].(string); ok {
		return val
	}
	return ""
}

func (c *ConfigurationItemWithAppServices) GetLifeCycleStage() string {
	if val, ok := c.RawCI["life_cycle_stage"].(string); ok {
		return val
	}
	return ""
}

func (c *ConfigurationItemWithAppServices) GetLifeCycleStageStatus() string {
	if val, ok := c.RawCI["life_cycle_stage_status"].(string); ok {
		return val
	}
	return ""
}

func (c *ConfigurationItemWithAppServices) GetAppServiceNumbers() []string {
	numbers := make([]string, 0, len(c.AppServicesNumbers))
	for num := range c.AppServicesNumbers {
		numbers = append(numbers, num)
	}
	sort.Strings(numbers)
	return numbers
}

func (c *ConfigurationItemWithAppServices) AddAppServiceNumber(number string) {
	if number != "" {
		if c.AppServicesNumbers == nil {
			c.AppServicesNumbers = make(map[string]struct{})
		}
		c.AppServicesNumbers[number] = struct{}{}
	}
}

func (c *ConfigurationItemWithAppServices) IsSysClassName(className string) bool {
	return c.GetSysClassName() == className
}
