package snow

import (
	"net/http"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

type (
	HttpClient interface {
		Do(req *http.Request) (res *http.Response, err error)
	}

	// ClientConfig holds the configuration for creating a new SNow client
	ClientConfig struct {
		Debug           bool
		AuthServerURL   string // OAuth2 Authentication URL
		ClientID        string // OAuth2 Client ID
		ClientSecret    string // OAuth2 Client Secret
		ApiEndpoint     string
		ProxyURL        string // HTTP Proxy URL for ServiceNow access
		EnableTLSVerify bool
		RequestTimeout  time.Duration
		Scopes          []string
	}

	Client struct {
		*logging.DebugLogger
		httpClient        HttpClient
		baseUrl           string
		urlAppservice     string
		urlTag            string
		urlCmdbCi         string
		urlGroup          string
		urlVMwareInstance string
		urlChangeStandard string
		urlChangeNormal   string
		urlChangeClose    string
		urlQuickDiscovery string
		urlChangeAddCI    string
		debug             bool
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

	TagPostRequest struct {
		Key string `json:"key"`
		CI  string `json:"ci"`
	}

	TagPostResponse struct {
		SysID   string `json:"sys_id,omitempty"`
		Key     string `json:"key,omitempty"`
		Value   string `json:"value,omitempty"`
		CI      string `json:"ci,omitempty"`
		Message string `json:"message,omitempty"`
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

	// NormalChangeRequest represents the request structure for creating a change
	NormalChangeRequest struct {
		CallbackUrl string                `json:"callbackUrl"`
		Change      NewNormalChangeObject `json:"change"`
		Variables   ChangeVariables       `json:"variables"`
	}

	StandardChangeRequest struct {
		CallbackUrl string                  `json:"callbackUrl"`
		Change      NewStandardChangeObject `json:"change"`
	}

	ChangeCloseRequest struct {
		CloseCode       string `json:"close_code"`
		CloseNotes      string `json:"close_notes"`
		ActualStartDate string `json:"actual_start_date"`
		ActualEndDate   string `json:"actual_end_date"`
	}

	ChangeVariables struct {
		Action string `json:"action"`
	}

	// NewNormalChangeObject represents the change details for creation
	NewNormalChangeObject struct {
		CIs                []string `json:"cis"`
		ApplicationService string   `json:"application_service"`
		ShortDescription   string   `json:"short_description"`
		Description        string   `json:"description"`
		StartDate          string   `json:"start_date"`
		EndDate            string   `json:"end_date"`
		RequestedBy        string   `json:"requested_by"`
		AssignedTo         string   `json:"assigned_to,omitempty"`
		AssignmentGroup    string   `json:"assignment_group,omitempty"`
		Justification      string   `json:"justification"`
		ImplementationPlan string   `json:"implementation_plan"`
		RiskImpactAnalysis string   `json:"risk_impact_analysis"`
		BackoutPlan        string   `json:"backout_plan"`
	}

	// NewStandardChangeObject represents the change details for creation
	NewStandardChangeObject struct {
		CIs                    []string `json:"cis"`
		ApplicationService     string   `json:"application_service"`
		ShortDescription       string   `json:"short_description"`
		Description            string   `json:"description"`
		StartDate              string   `json:"start_date"`
		EndDate                string   `json:"end_date"`
		RequestedBy            string   `json:"requested_by"`
		AssignedTo             string   `json:"assigned_to,omitempty"`
		AssignmentGroup        string   `json:"assignment_group,omitempty"`
		StandardChangeTemplate string   `json:"standard_change_template"`
	}

	// ChangeResponse represents the response structure when creating a change
	ChangeResponse struct {
		SysID                  string   `json:"sys_id"`
		Number                 string   `json:"number"`
		ChangeType             string   `json:"change_type"`
		StandardChangeTemplate string   `json:"standard_change_template"`
		ShortDescription       string   `json:"short_description"`
		Description            string   `json:"description"`
		ApprovalState          string   `json:"approval_state"`
		State                  string   `json:"state"`
		Phase                  string   `json:"phase"`
		StartDate              string   `json:"start_date"`
		EndDate                string   `json:"end_date"`
		CIs                    []string `json:"cis"`
	}

	// QuickDiscoveryRequest DiscoveryWithCallback repräsentiert den Request Body für die Quick Discovery Methode
	QuickDiscoveryRequest struct {
		CallbackURL string `json:"callbackUrl" binding:"required,url"`
		DiscoveryIP string `json:"discovery_ip" binding:"required,ip"`
	}

	ChangeAddCiRequest struct {
		CI string `json:"ci"`
	}
)

// IsError prüft, ob die Response einen Fehler enthält
func (r *TagPostResponse) IsError() bool {
	return r.Message != ""
}
