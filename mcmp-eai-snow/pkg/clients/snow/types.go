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
		httpClient         HttpClient
		urlAppservice      string
		urlTag             string
		urlCmdbCi          string
		urlGroup           string
		urlVMwareInstance  string
		urlLockedShutdown  string
		urlLockedRightsize string
		urlVMwareServer    string
		debug              bool
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
)
