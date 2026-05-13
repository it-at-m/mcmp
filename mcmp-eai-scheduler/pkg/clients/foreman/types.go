package foreman

import (
	"fmt"
	"time"

	"git.muenchen.de/mcmp/webanwendung/mcmp-eai-common/pkg/client/httpclient"
	"git.muenchen.de/mcmp/webanwendung/mcmp-eai-common/pkg/logging"
)

type (
	// ClientConfig holds the configuration for creating a new SNow client
	ClientConfig struct {
		// Debug enables verbose logging for troubleshooting API communication
		Debug bool

		// Username is the username of the Foreman user to authenticate as
		Username string

		// Password is the password of the Foreman user to authenticate as
		Password string

		// ApiEndpoint is the base URL of the Foreman API
		ApiEndpoint string

		// ParallelQueries controls the number of concurrent queries to the API
		ParallelQueries int

		// EnableTLSVerify controls whether TLS certificate verification is enforced
		// Should be true in production environments for security
		EnableTLSVerify bool

		// RequestTimeout defines the maximum duration for individual HTTP requests
		// Includes connection establishment, request sending, and response reading
		RequestTimeout time.Duration

		ProxyURL string

		// MaxRetries specifies the maximum number of retry attempts for failed requests
		// Applies to retryable errors like temporary network issues or server errors
		MaxRetries int

		// RetryDelay sets the base delay between retry attempts
		// Actual delay increases exponentially with each retry (exponential backoff)
		RetryDelay time.Duration

		// UserAgent string identifies the client in HTTP requests
		// Used for logging and monitoring purposes on the server side
		UserAgent string
	}

	// Client represents a foreman API client with HTTP communication capabilities
	// It encapsulates the HTTP client configuration, API endpoints, and debug logging
	Client struct {
		*logging.DebugLogger
		httpClient *httpclient.Client
		urlHosts   string
		urlHost    string
		debug      bool
		config     ClientConfig
	}

	QueryParams struct {
		Page        int
		PerPage     int
		Search      string
		OrderBy     string
		OrderDir    string // "asc" or "desc"
		ThinResults bool
	}

	APIError struct {
		StatusCode int    `json:"status_code"`
		Message    string `json:"message"`
		Details    string `json:"details,omitempty"`
	}

	HostResponse struct {
		Total    int     `json:"total"`
		Subtotal int     `json:"subtotal"`
		Page     int     `json:"page"`
		PerPage  int     `json:"per_page"`
		Search   *string `json:"search"`
		Sort     Sort    `json:"sort"`
		Results  []Host  `json:"results"`
	}

	Sort struct {
		By    string `json:"by"`
		Order string `json:"order"`
	}

	Host struct {
		AllParameters               []Parameter                  `json:"all_parameters"`
		ArchitectureID              int                          `json:"architecture_id"`
		ArchitectureName            string                       `json:"architecture_name"`
		BMCAvailable                bool                         `json:"bmc_available"`
		Build                       bool                         `json:"build"`
		BuildStatus                 int                          `json:"build_status"`
		BuildStatusLabel            string                       `json:"build_status_label"`
		Capabilities                []string                     `json:"capabilities"`
		Certname                    string                       `json:"certname"`
		CockpitURL                  *string                      `json:"cockpit_url"`
		Comment                     string                       `json:"comment"`
		ComputeProfileID            int                          `json:"compute_profile_id"`
		ComputeProfileName          string                       `json:"compute_profile_name"`
		ComputeResourceID           int                          `json:"compute_resource_id"`
		ComputeResourceName         string                       `json:"compute_resource_name"`
		ComputeResourceProvider     string                       `json:"compute_resource_provider"`
		ConfigurationStatus         int                          `json:"configuration_status"`
		ConfigurationStatusLabel    string                       `json:"configuration_status_label"`
		ContentFacetAttributes      *ContentFacetAttributes      `json:"content_facet_attributes"`
		CreatedAt                   string                       `json:"created_at"`
		Creator                     *string                      `json:"creator"`
		CreatorID                   *int                         `json:"creator_id"`
		Disk                        *string                      `json:"disk"`
		DisplayName                 string                       `json:"display_name"`
		DomainID                    int                          `json:"domain_id"`
		DomainName                  string                       `json:"domain_name"`
		Enabled                     bool                         `json:"enabled"`
		ErrataStatus                int                          `json:"errata_status"`
		ErrataStatusLabel           string                       `json:"errata_status_label"`
		ExecutionStatus             int                          `json:"execution_status"`
		ExecutionStatusLabel        string                       `json:"execution_status_label"`
		ExpiredOn                   *string                      `json:"expired_on"`
		Facts                       map[string]interface{}       `json:"facts"`
		GlobalStatus                int                          `json:"global_status"`
		GlobalStatusLabel           string                       `json:"global_status_label"`
		HostCollections             []HostCollection             `json:"host_collections"`
		HostgroupID                 int                          `json:"hostgroup_id"`
		HostgroupName               string                       `json:"hostgroup_name"`
		HostgroupTitle              string                       `json:"hostgroup_title"`
		ID                          int                          `json:"id"`
		ImageFile                   string                       `json:"image_file"`
		ImageID                     *int                         `json:"image_id"`
		ImageName                   *string                      `json:"image_name"`
		InitiatedAt                 *string                      `json:"initiated_at"`
		InstalledAt                 *string                      `json:"installed_at"`
		Interfaces                  []Interface                  `json:"interfaces"`
		IP                          *string                      `json:"ip"`
		IP6                         *string                      `json:"ip6"`
		LastCompile                 *string                      `json:"last_compile"`
		LastReport                  *string                      `json:"last_report"`
		LocationID                  int                          `json:"location_id"`
		LocationName                string                       `json:"location_name"`
		Mac                         string                       `json:"mac"`
		Managed                     bool                         `json:"managed"`
		MediumID                    *int                         `json:"medium_id"`
		MediumName                  *string                      `json:"medium_name"`
		ModelID                     *int                         `json:"model_id"`
		ModelName                   *string                      `json:"model_name"`
		Name                        string                       `json:"name"`
		OperatingSystemFamily       string                       `json:"operatingsystem_family"`
		OperatingSystemID           int                          `json:"operatingsystem_id"`
		OperatingSystemMajor        string                       `json:"operatingsystem_major"`
		OperatingSystemName         string                       `json:"operatingsystem_name"`
		OrganizationID              int                          `json:"organization_id"`
		OrganizationName            string                       `json:"organization_name"`
		OwnerID                     int                          `json:"owner_id"`
		OwnerName                   string                       `json:"owner_name"`
		OwnerType                   string                       `json:"owner_type"`
		Parameters                  []Parameter                  `json:"parameters"`
		Permissions                 Permissions                  `json:"permissions"`
		ProvisionMethod             string                       `json:"provision_method"`
		PtableID                    *int                         `json:"ptable_id"`
		PtableName                  *string                      `json:"ptable_name"`
		PuppetCaProxy               *ProxyInfo                   `json:"puppet_ca_proxy"`
		PuppetCaProxyID             *int                         `json:"puppet_ca_proxy_id"`
		PuppetCaProxyName           *string                      `json:"puppet_ca_proxy_name"`
		PuppetProxy                 *ProxyInfo                   `json:"puppet_proxy"`
		PuppetProxyID               *int                         `json:"puppet_proxy_id"`
		PuppetProxyName             *string                      `json:"puppet_proxy_name"`
		PuppetStatus                int                          `json:"puppet_status"`
		PxeLoader                   *string                      `json:"pxe_loader"`
		RealmID                     *int                         `json:"realm_id"`
		RealmName                   *string                      `json:"realm_name"`
		RhelLifecycleStatus         int                          `json:"rhel_lifecycle_status"`
		RhelLifecycleStatusLabel    string                       `json:"rhel_lifecycle_status_label"`
		SpIP                        *string                      `json:"sp_ip"`
		SpMac                       *string                      `json:"sp_mac"`
		SpName                      *string                      `json:"sp_name"`
		SpSubnetID                  *int                         `json:"sp_subnet_id"`
		Subnet6ID                   *int                         `json:"subnet6_id"`
		Subnet6Name                 *string                      `json:"subnet6_name"`
		SubnetID                    int                          `json:"subnet_id"`
		SubnetName                  string                       `json:"subnet_name"`
		SubscriptionFacetAttributes *SubscriptionFacetAttributes `json:"subscription_facet_attributes"`
		UpdatedAt                   string                       `json:"updated_at"`
		UseImage                    *bool                        `json:"use_image"`
		UUID                        string                       `json:"uuid"`
	}

	RobustHost struct {
		ArchitectureName      string                 `json:"architecture_name"`
		CreatedAt             string                 `json:"created_at"`
		DisplayName           string                 `json:"display_name"`
		Facts                 map[string]interface{} `json:"facts"`
		HostCollections       []HostCollection       `json:"host_collections"`
		ID                    int                    `json:"id"`
		InitiatedAt           *string                `json:"initiated_at"`
		InstalledAt           *string                `json:"installed_at"`
		Interfaces            []Interface            `json:"interfaces"`
		IP                    *string                `json:"ip"`
		Mac                   string                 `json:"mac"`
		Name                  string                 `json:"name"`
		OperatingSystemFamily string                 `json:"operatingsystem_family"`
		OperatingSystemMajor  string                 `json:"operatingsystem_major"`
		OperatingSystemName   string                 `json:"operatingsystem_name"`
		UUID                  string                 `json:"uuid"`
	}

	ProxyInfo struct {
		ID   int    `json:"id"`
		Name string `json:"name"`
		URL  string `json:"url"`
	}

	ContentFacetAttributes struct {
		ID                          int                      `json:"id"`
		UUID                        string                   `json:"uuid"`
		ContentSourceID             *int                     `json:"content_source_id"`
		ContentSourceName           *string                  `json:"content_source_name"`
		KickstartRepositoryID       *int                     `json:"kickstart_repository_id"`
		KickstartRepositoryName     *string                  `json:"kickstart_repository_name"`
		ErrataCounts                ErrataCounts             `json:"errata_counts"`
		ApplicableDebCount          int                      `json:"applicable_deb_count"`
		UpgradableDebCount          int                      `json:"upgradable_deb_count"`
		ApplicablePackageCount      int                      `json:"applicable_package_count"`
		UpgradablePackageCount      int                      `json:"upgradable_package_count"`
		ApplicableModuleStreamCount int                      `json:"applicable_module_stream_count"`
		UpgradableModuleStreamCount int                      `json:"upgradable_module_stream_count"`
		ContentViewEnvironments     []ContentViewEnvironment `json:"content_view_environments"`
		KickstartRepository         *string                  `json:"kickstart_repository"`
		ContentView                 *ContentView             `json:"content_view"`
		LifecycleEnvironment        *LifecycleEnvironment    `json:"lifecycle_environment"`
		Permissions                 *ContentPermissions      `json:"permissions"`
		ContentViewVersion          *string                  `json:"content_view_version"`
		ContentViewVersionID        *int                     `json:"content_view_version_id"`
		ContentViewVersionLatest    *bool                    `json:"content_view_version_latest"`
		ContentViewDefault          *bool                    `json:"content_view_default?"`
		LifecycleEnvironmentLibrary *bool                    `json:"lifecycle_environment_library?"`
		KatelloTracerInstalled      *bool                    `json:"katello_tracer_installed"`
		KatelloTracerRpmAvailable   *bool                    `json:"katello_tracer_rpm_available"`
	}

	ErrataCounts struct {
		Security    int              `json:"security"`
		Bugfix      int              `json:"bugfix"`
		Enhancement int              `json:"enhancement"`
		Total       int              `json:"total"`
		Applicable  ApplicableErrata `json:"applicable"`
	}

	ApplicableErrata struct {
		Bugfix      int `json:"bugfix"`
		Security    int `json:"security"`
		Enhancement int `json:"enhancement"`
		Total       int `json:"total"`
	}

	ContentViewEnvironment struct {
		ContentView          ContentView          `json:"content_view"`
		LifecycleEnvironment LifecycleEnvironment `json:"lifecycle_environment"`
	}

	ContentView struct {
		ID        int    `json:"id"`
		Name      string `json:"name"`
		Composite bool   `json:"composite"`
	}

	LifecycleEnvironment struct {
		ID   int    `json:"id"`
		Name string `json:"name"`
	}

	ContentPermissions struct {
		ViewLifecycleEnvironments                 bool `json:"view_lifecycle_environments"`
		ViewContentViews                          bool `json:"view_content_views"`
		PromoteOrRemoveContentViewsToEnvironments bool `json:"promote_or_remove_content_views_to_environments"`
		ViewHostCollections                       bool `json:"view_host_collections"`
		CreateJobInvocations                      bool `json:"create_job_invocations"`
		ViewActivationKeys                        bool `json:"view_activation_keys"`
		ViewProducts                              bool `json:"view_products"`
		CreateBookmarks                           bool `json:"create_bookmarks"`
	}

	SubscriptionFacetAttributes struct {
		HostType                 string                    `json:"host_type"`
		DmiUUID                  string                    `json:"dmi_uuid"`
		ID                       int                       `json:"id"`
		UUID                     string                    `json:"uuid"`
		LastCheckin              *string                   `json:"last_checkin"`
		ServiceLevel             *string                   `json:"service_level"`
		AutoAttach               *bool                     `json:"auto_attach"`
		AutoUpdate               *bool                     `json:"auto_update"`
		AutoHeal                 *bool                     `json:"auto_heal"`
		ReleaseVersion           *string                   `json:"release_version"`
		Autoheal                 *bool                     `json:"autoheal"`
		VirtualHost              *VirtualHost              `json:"virtual_host"`
		VirtualGuests            []VirtualGuest            `json:"virtual_guests"`
		HostBillingConfiguration *HostBillingConfiguration `json:"host_billing_configuration"`
		Purpose                  *Purpose                  `json:"purpose"`
		Hypervisor               *bool                     `json:"hypervisor"`
		ActivationKeys           []ActivationKey           `json:"activation_keys"`
		InstalledProducts        []InstalledProduct        `json:"installed_products"`
		Pools                    []Pool                    `json:"pools"`
		RegisteredThrough        *string                   `json:"registered_through"`
		RegisteredAt             *string                   `json:"registered_at"`
		ComplianceStatus         *int                      `json:"compliance_status"`
		ComplianceStatusLabel    *string                   `json:"compliance_status_label"`
		SimpleContentAccess      *bool                     `json:"simple_content_access"`
		SystemPurposeStatus      *string                   `json:"system_purpose_status"`
		SystemPurposeStatusLabel *string                   `json:"system_purpose_status_label"`
		UserID                   *string                   `json:"user_id"`
	}

	VirtualHost struct {
		ID   *int    `json:"id"`
		Name *string `json:"name"`
		UUID *string `json:"uuid"`
	}

	VirtualGuest struct {
		ID   *int    `json:"id"`
		Name *string `json:"name"`
		UUID *string `json:"uuid"`
	}

	HostBillingConfiguration struct {
		ID   *int    `json:"id"`
		Name *string `json:"name"`
	}

	Purpose struct {
		Usage        *string  `json:"usage"`
		Role         *string  `json:"role"`
		Addons       []string `json:"addons"`
		ServiceLevel *string  `json:"service_level"`
	}

	ActivationKey struct {
		ID   int    `json:"id"`
		Name string `json:"name"`
	}

	InstalledProduct struct {
		ProductID   string `json:"product_id"`
		ProductName string `json:"product_name"`
		Arch        string `json:"arch"`
		Version     string `json:"version"`
		Status      string `json:"status"`
		StartDate   string `json:"start_date"`
		EndDate     string `json:"end_date"`
	}

	Pool struct {
		ID                string             `json:"id"`
		CP                string             `json:"cp"`
		SubscriptionID    int                `json:"subscription_id"`
		ProductID         string             `json:"product_id"`
		ProductName       string             `json:"product_name"`
		Quantity          int                `json:"quantity"`
		StartDate         string             `json:"start_date"`
		EndDate           string             `json:"end_date"`
		Consumed          int                `json:"consumed"`
		Available         int                `json:"available"`
		ProductAttributes []ProductAttribute `json:"product_attributes"`
	}

	ProductAttribute struct {
		Name  string `json:"name"`
		Value string `json:"value"`
	}

	Parameter struct {
		AssociatedType string      `json:"associated_type"`
		CreatedAt      string      `json:"created_at"`
		HiddenValue    bool        `json:"hidden_value?"`
		ID             int         `json:"id"`
		Name           string      `json:"name"`
		ParameterType  string      `json:"parameter_type"`
		Priority       int         `json:"priority"`
		UpdatedAt      string      `json:"updated_at"`
		Value          interface{} `json:"value"`
	}

	Interface struct {
		CreatedAt   string  `json:"created_at"`
		DomainID    int     `json:"domain_id"`
		DomainName  string  `json:"domain_name"`
		Execution   bool    `json:"execution"`
		FQDN        string  `json:"fqdn"`
		ID          int     `json:"id"`
		Identifier  *string `json:"identifier"`
		IP          *string `json:"ip"`
		IP6         *string `json:"ip6"`
		Mac         string  `json:"mac"`
		MTU         int     `json:"mtu"`
		Managed     bool    `json:"managed"`
		Name        string  `json:"name"`
		Primary     bool    `json:"primary"`
		Provision   bool    `json:"provision"`
		Subnet6ID   *int    `json:"subnet6_id"`
		Subnet6Name *string `json:"subnet6_name"`
		SubnetID    *int    `json:"subnet_id"`
		SubnetName  *string `json:"subnet_name"`
		Type        string  `json:"type"`
		UpdatedAt   string  `json:"updated_at"`
		Virtual     bool    `json:"virtual"`
	}

	Permissions struct {
		BuildHosts        bool `json:"build_hosts"`
		CockpitHosts      bool `json:"cockpit_hosts"`
		ConsoleHosts      bool `json:"console_hosts"`
		CreateHosts       bool `json:"create_hosts"`
		CreateSnapshots   bool `json:"create_snapshots"`
		DestroyHosts      bool `json:"destroy_hosts"`
		DestroySnapshots  bool `json:"destroy_snapshots"`
		EditHostExpiry    bool `json:"edit_host_expiry"`
		EditHosts         bool `json:"edit_hosts"`
		EditSnapshots     bool `json:"edit_snapshots"`
		ForgetStatusHosts bool `json:"forget_status_hosts"`
		IPMIBootHosts     bool `json:"ipmi_boot_hosts"`
		PowerHosts        bool `json:"power_hosts"`
		RevertSnapshots   bool `json:"revert_snapshots"`
		ViewHosts         bool `json:"view_hosts"`
		ViewSnapshots     bool `json:"view_snapshots"`
	}
	HostCollection struct {
		ID             int    `json:"id"`
		Name           string `json:"name"`
		Description    string `json:"description"`
		MaxHosts       *int   `json:"max_hosts"`
		UnlimitedHosts bool   `json:"unlimited_hosts"`
		TotalHosts     int    `json:"total_hosts"`
	}
)

// Error implements the error interface
func (e *APIError) Error() string {
	if e.Details != "" {
		return fmt.Sprintf("Foreman API error %d: %s (%s)", e.StatusCode, e.Message, e.Details)
	}
	return fmt.Sprintf("Foreman API error %d: %s", e.StatusCode, e.Message)
}

// Utility methods for Host
func (h *Host) GetPrimaryInterface() *Interface {
	for _, iface := range h.Interfaces {
		if iface.Primary {
			return &iface
		}
	}
	return nil
}

func (h *Host) GetIPAddress() string {
	if h.IP != nil {
		return *h.IP
	}
	if primary := h.GetPrimaryInterface(); primary != nil && primary.IP != nil {
		return *primary.IP
	}
	return ""
}

func (h *Host) IsManaged() bool {
	return h.Managed
}

func (h *Host) GetFactValue(key string) interface{} {
	if h.Facts == nil {
		return nil
	}
	return h.Facts[key]
}

// DefaultQueryParams returns standard query parameters
func DefaultQueryParams() QueryParams {
	return QueryParams{
		Page:        1,
		PerPage:     100,
		OrderBy:     "name",
		OrderDir:    "asc",
		ThinResults: true,
	}
}

func (rh *RobustHost) ToHost() *Host {
	return &Host{
		ID:                    rh.ID,
		Name:                  rh.Name,
		Mac:                   rh.Mac,
		IP:                    rh.IP,
		UUID:                  rh.UUID,
		DisplayName:           rh.DisplayName,
		ArchitectureName:      rh.ArchitectureName,
		OperatingSystemName:   rh.OperatingSystemName,
		OperatingSystemFamily: rh.OperatingSystemFamily,
		OperatingSystemMajor:  rh.OperatingSystemMajor,
		CreatedAt:             rh.CreatedAt,
		InitiatedAt:           rh.InitiatedAt,
		InstalledAt:           rh.InstalledAt,
		Interfaces:            rh.Interfaces,
		Facts:                 rh.Facts,
		HostCollections:       rh.HostCollections,
	}
}
