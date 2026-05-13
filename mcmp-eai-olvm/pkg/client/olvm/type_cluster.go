package olvm

type ClustersResponse struct {
	Cluster []Cluster `json:"cluster"`
}

type Cluster struct {
	BallooningEnabled                string                           `json:"ballooning_enabled"`
	BiosType                         string                           `json:"bios_type"`
	Cpu                              Cpu                              `json:"cpu"`
	CustomSchedulingPolicyProperties CustomSchedulingPolicyProperties `json:"custom_scheduling_policy_properties"`
	ErrorHandling                    ErrorHandling                    `json:"error_handling"`
	FencingPolicy                    FencingPolicy                    `json:"fencing_policy"`
	FipsMode                         string                           `json:"fips_mode"`
	FirewallType                     string                           `json:"firewall_type"`
	GlusterService                   string                           `json:"gluster_service"`
	HaReservation                    string                           `json:"ha_reservation"`
	Ksm                              Ksm                              `json:"ksm"`
	LogMaxMemoryUsedThreshold        string                           `json:"log_max_memory_used_threshold"`
	LogMaxMemoryUsedThresholdType    string                           `json:"log_max_memory_used_threshold_type"`
	MemoryPolicy                     MemoryPolicyCluster              `json:"memory_policy"`
	Migration                        MigrationCluster                 `json:"migration"`
	RequiredRngSources               RequiredRngSources               `json:"required_rng_sources"`
	SerialNumber                     SerialNumber                     `json:"serial_number"`
	SwitchType                       string                           `json:"switch_type"`
	ThreadsAsCores                   string                           `json:"threads_as_cores"`
	TrustedService                   string                           `json:"trusted_service"`
	TunnelMigration                  string                           `json:"tunnel_migration"`
	UpgradeCorrelationId             string                           `json:"upgrade_correlation_id"`
	UpgradeInProgress                string                           `json:"upgrade_in_progress"`
	UpgradePercentComplete           string                           `json:"upgrade_percent_complete"`
	Version                          Version                          `json:"version"`
	VirtService                      string                           `json:"virt_service"`
	VncEncryption                    string                           `json:"vnc_encryption"`
	DataCenter                       Ref                              `json:"data_center"`
	MacPool                          Ref                              `json:"mac_pool"`
	SchedulingPolicy                 Ref                              `json:"scheduling_policy"`
	Actions                          Actions                          `json:"actions"`
	Name                             string                           `json:"name"`
	Description                      string                           `json:"description"`
	Comment                          string                           `json:"comment"`
	Link                             []Link                           `json:"link"`
	Href                             string                           `json:"href"`
	Id                               string                           `json:"id"`
}

type Cpu struct {
	Architecture string `json:"architecture"`
	Type         string `json:"type"`
}

type CustomSchedulingPolicyProperties struct {
	Property []Property `json:"property"`
}

type Property struct {
	Name  string `json:"name"`
	Value string `json:"value"`
}

type ErrorHandling struct {
	OnError string `json:"on_error"`
}

type FencingPolicy struct {
	Enabled                   string                   `json:"enabled"`
	SkipIfConnectivityBroken  SkipIfConnectivityBroken `json:"skip_if_connectivity_broken"`
	SkipIfGlusterBricksUp     string                   `json:"skip_if_gluster_bricks_up"`
	SkipIfGlusterQuorumNotMet string                   `json:"skip_if_gluster_quorum_not_met"`
	SkipIfSdActive            SkipIfSdActive           `json:"skip_if_sd_active"`
}

type SkipIfConnectivityBroken struct {
	Enabled   string `json:"enabled"`
	Threshold string `json:"threshold"`
}

type SkipIfSdActive struct {
	Enabled string `json:"enabled"`
}

type Ksm struct {
	Enabled          string `json:"enabled"`
	MergeAcrossNodes string `json:"merge_across_nodes"`
}

type MemoryPolicyCluster struct {
	OverCommit           OverCommit           `json:"over_commit"`
	TransparentHugepages TransparentHugepages `json:"transparent_hugepages"`
}

type OverCommit struct {
	Percent string `json:"percent"`
}

type TransparentHugepages struct {
	Enabled string `json:"enabled"`
}

type MigrationCluster struct {
	AutoConverge             string    `json:"auto_converge"`
	Bandwidth                Bandwidth `json:"bandwidth"`
	Compressed               string    `json:"compressed"`
	Encrypted                string    `json:"encrypted"`
	ParallelMigrationsPolicy string    `json:"parallel_migrations_policy"`
	Policy                   Policy    `json:"policy"`
}

type Bandwidth struct {
	AssignmentMethod string `json:"assignment_method"`
}

type Policy struct {
	Id string `json:"id"`
}

type RequiredRngSources struct {
	RequiredRngSource []string `json:"required_rng_source"`
}

type SerialNumber struct {
	Policy string `json:"policy"`
}

type Version struct {
	Major string `json:"major"`
	Minor string `json:"minor"`
}
