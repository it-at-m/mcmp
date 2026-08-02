package mcmp

import (
	"net/http"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
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
		EaiInfo              app.EaiMetadata       `json:"eai_info"`
		Users                []User                `json:"users"`
		Groups               []Group               `json:"groups"`
		CmdbCIs              []ServerCI            `json:"cis"`
		AppServices          []AppService          `json:"app_services"`
		KubernetesClusterCIs []KubernetesClusterCI `json:"kubernetes_clusters"`
		StorageServerCIs     []ServerCI            `json:"storage_server"`
		StorageVolumeCIs     []StorageCI           `json:"storage_volumes"`
		StorageQTreeCIs      []StorageCI           `json:"storage_qtrees"`
		StorageAccountCIs    []CloudObjectCI       `json:"storage_accounts"`
		StorageBucketCIs     []CloudObjectCI       `json:"storage_buckets"`
		LbServiceCI          []LbServiceCI         `json:"lb_services"`
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

	ServerCI struct {
		Name                  string   `json:"name,omitempty"`
		SysID                 string   `json:"sys_id,omitempty"`
		SerialNumber          string   `json:"serial_number,omitempty"`
		SysClassName          string   `json:"sys_class,omitempty"`
		LifeCycleStage        string   `json:"life_cycle_stage,omitempty"`
		LifeCycleStageStatus  string   `json:"life_cycle_stage_status,omitempty"`
		LastDiscovered        string   `json:"last_discovered,omitempty"`
		IPAddress             string   `json:"ip_address,omitempty"`
		FQDN                  string   `json:"fqdn,omitempty"`
		OS                    string   `json:"os,omitempty"`
		OSVersion             string   `json:"os_version,omitempty"`
		HardwareStatus        string   `json:"hardware_status,omitempty"`
		VmInstanceUUID        string   `json:"vm_instance_uuid,omitempty"`
		MacAddress            string   `json:"mac_address,omitempty"`
		ServerSysID           string   `json:"server_sys_id,omitempty"`
		LockedShutdown        bool     `json:"locked_shutdown"`
		ShutdownTaskClosedAt  string   `json:"shutdown_task_closed_at,omitempty"`
		LockedRightsize       bool     `json:"locked_rightsize"`
		RightsizeTaskClosedAt string   `json:"rightsize_task_closed_at,omitempty"`
		Company               string   `json:"company,omitempty"`
		DefaultGateway        string   `json:"default_gateway,omitempty"`
		DnsDomain             string   `json:"dns_domain,omitempty"`
		Environment           string   `json:"environment,omitempty"`
		HostName              string   `json:"host_name,omitempty"`
		InstallDate           string   `json:"install_date,omitempty"`
		InstallStatus         string   `json:"install_status,omitempty"`
		Manufacturer          string   `json:"manufacturer,omitempty"`
		ModelID               string   `json:"model_id,omitempty"`
		OperationalStatus     string   `json:"operational_status,omitempty"`
		OsDomain              string   `json:"os_domain,omitempty"`
		Virtual               string   `json:"virtual,omitempty"`
		BiosUUID              string   `json:"bios_uuid,omitempty"`
		ObjectID              string   `json:"object_id,omitempty"`
		VcenterUUID           string   `json:"vcenter_uuid,omitempty"`
		Template              string   `json:"template,omitempty"`
		AppServiceNumbers     []string `json:"app_service_number,omitempty"`
	}

	KubernetesClusterCI struct {
		Name                   string                  `json:"name,omitempty"`
		SysID                  string                  `json:"sys_id,omitempty"`
		SysClass               string                  `json:"sys_class,omitempty"`
		LastDiscovered         string                  `json:"last_discovered,omitempty"`
		LifeCycleStage         string                  `json:"life_cycle_stage,omitempty"`
		LifeCycleStageStatus   string                  `json:"life_cycle_stage_status,omitempty"`
		K8SUID                 string                  `json:"k8s_uid,omitempty"`
		Environment            string                  `json:"environment,omitempty"`
		KubernetesNamespaceCIs []KubernetesNamespaceCI `json:"kubernetes_namespaces,omitempty"`
	}

	KubernetesNamespaceCI struct {
		Name                 string   `json:"name,omitempty"`
		SysID                string   `json:"sys_id,omitempty"`
		SysClass             string   `json:"sys_class,omitempty"`
		LastDiscovered       string   `json:"last_discovered,omitempty"`
		LifeCycleStage       string   `json:"life_cycle_stage,omitempty"`
		LifeCycleStageStatus string   `json:"life_cycle_stage_status,omitempty"`
		K8sUID               string   `json:"k8s_uid,omitempty"`
		ClusterSysID         string   `json:"cluster_sys_id,omitempty"`
		Environment          string   `json:"environment,omitempty"`
		AppServiceNumbers    []string `json:"app_service_number,omitempty"`
	}

	CloudObjectCI struct {
		Name              string   `json:"name,omitempty"`
		SysID             string   `json:"sys_id,omitempty"`
		SysClass          string   `json:"sys_class,omitempty"`
		AccountId         string   `json:"account_id,omitempty"`
		AppServiceNumbers []string `json:"app_service_number,omitempty"`
	}

	StorageCI struct {
		Name                 string   `json:"name,omitempty"`
		SysID                string   `json:"sys_id,omitempty"`
		SysClass             string   `json:"sys_class,omitempty"`
		LifeCycleStage       string   `json:"life_cycle_stage,omitempty"`
		LifeCycleStageStatus string   `json:"life_cycle_stage_status,omitempty"`
		LastDiscovered       string   `json:"last_discovered,omitempty"`
		StorageType          string   `json:"storage_type,omitempty"`
		VolumeID             string   `json:"volume_id,omitempty"`
		QTreeID              string   `json:"qtree_id,omitempty"`
		ClusterID            string   `json:"cluster_id,omitempty"`
		ObjectID             string   `json:"object_id,omitempty"`
		SvmUUID              string   `json:"svm_uuid,omitempty"`
		AppServiceNumbers    []string `json:"app_service_number,omitempty"`
	}

	LbServiceCI struct {
		Name                 string   `json:"name,omitempty"`
		SysID                string   `json:"sys_id,omitempty"`
		SysClass             string   `json:"sys_class,omitempty"`
		LifeCycleStage       string   `json:"life_cycle_stage,omitempty"`
		LifeCycleStageStatus string   `json:"life_cycle_stage_status,omitempty"`
		LastDiscovered       string   `json:"last_discovered,omitempty"`
		AppServiceNumbers    []string `json:"app_service_number,omitempty"`
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
		ServerCIs              []string `json:"server_cis,omitempty"`
		KubernetesNamespaceCIs []string `json:"kubernetes_namespace_cis,omitempty"`
		CloudObjectStorageCI   []string `json:"cloud_object_storage_cis,omitempty"`
		StorageVolumeCIs       []string `json:"storage_volume_cis,omitempty"`
		LbServiceCIs           []string `json:"lb_service_cis,omitempty"`
	}
)

func (sd *SnowData) GetEaiMetadata() app.EaiMetadata  { return sd.EaiInfo }
func (sd *SnowData) SetEaiMetadata(m app.EaiMetadata) { sd.EaiInfo = m }
