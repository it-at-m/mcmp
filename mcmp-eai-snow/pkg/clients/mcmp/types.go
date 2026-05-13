package mcmp

import (
	"net/http"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

type (
	// HTTPClient defines the interface for HTTP client operations
	// This interface enables dependency injection and testing with mock implementations
	HTTPClient interface {
		Do(req *http.Request) (*http.Response, error)
	}

	// Client represents an MCMP API client with HTTP communication capabilities and OAuth2 support
	// It encapsulates the HTTP client configuration, OAuth2 authentication, and debug logging functionality
	Client struct {
		*logging.DebugLogger            // Embedded debug logger for request/response monitoring
		httpClient           HTTPClient // HTTP client implementation for API calls (OAuth2-enabled)
		debug                bool       // Debug flag to enable verbose logging
	}

	SnowData struct {
		Users       []User       `json:"users"`
		Groups      []Group      `json:"groups"`
		CmdbCIs     []CI         `json:"cis"`
		AppServices []AppService `json:"app_services"`
	}

	User struct {
		SysID      string `json:"sys_id,omitempty"`
		UserID     string `json:"user_id,omitempty"`
		Department string `json:"department,omitempty"`
		Name       string `json:"name,omitempty"`
		Email      string `json:"email,omitempty"`
	}

	Group struct {
		SysID   string   `json:"sys_id,omitempty"`
		Name    string   `json:"name,omitempty"`
		Manager string   `json:"manager,omitempty"`
		Members []string `json:"members,omitempty"`
	}

	CI struct {
		Name                  string `json:"name,omitempty"`
		SysID                 string `json:"sys_id,omitempty"`
		SerialNumber          string `json:"serial_number,omitempty"`
		SysClassName          string `json:"sys_class_name,omitempty"`
		IPAddress             string `json:"ip_address,omitempty"`
		FQDN                  string `json:"fqdn,omitempty"`
		OS                    string `json:"os,omitempty"`
		OSVersion             string `json:"os_version,omitempty"`
		HardwareStatus        string `json:"hardware_status,omitempty"`
		LastDiscovered        string `json:"last_discovered,omitempty"`
		VmInstanceUUID        string `json:"vm_instance_uuid,omitempty"`
		MacAddress            string `json:"mac_address,omitempty"`
		ServerSysID           string `json:"server_sys_id,omitempty"`
		LockedShutdown        bool   `json:"locked_shutdown"`
		ShutdownTaskClosedAt  string `json:"shutdown_task_closed_at,omitempty"`
		LockedRightsize       bool   `json:"locked_rightsize"`
		RightsizeTaskClosedAt string `json:"rightsize_task_closed_at,omitempty"`
	}

	AppService struct {
		SysID                  string   `json:"sys_id,omitempty"`
		Name                   string   `json:"name,omitempty"`
		Number                 string   `json:"number,omitempty"`
		Group                  string   `json:"group,omitempty"`
		UsedFor                string   `json:"used_for,omitempty"`
		Environment            string   `json:"environment,omitempty"`
		CSWEnforced            bool     `json:"csw_enforced,omitempty"`
		OwnedBy                string   `json:"owned_by,omitempty"`
		ServiceOwnerDelegate   string   `json:"service_owner_delegate,omitempty"`
		BusinessServiceNumbers []string `json:"business_service_numbers,omitempty"`
		CIs                    []string `json:"cis,omitempty"`
	}
)
