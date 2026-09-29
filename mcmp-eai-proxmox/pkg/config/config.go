package config

import (
	"slices"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const (
	defaultTimeoutSeconds = 30
	defaultMaxConns       = 10
)

// Config contains the EAI's full configuration.
type Config struct {
	GENERAL GeneralConfig     // General EAI configuration.
	PROXMOX []ProxmoxConfig   // Proxmox Datacenter Manager configuration. Imports from all clusters.
	MCMP    []mcmp.Config     // MCMP configuration. All instances will receive the same data.
	LOGGING logging.LogConfig // Logging configuration.
}

// GeneralConfig contains general EAI configuration.
type GeneralConfig struct {
	TimeoutSeconds      int      // Timeout for all processing. Negative values disable the timeout.
	SkipProxmox         bool     // Skip the Proxmox import and read from the export file.
	SkipMCMP            bool     // Skip the MCMP export and only write an export file.
	CompleteImport      bool     // Perform a complete import of all VMs.
	CompleteImportUUIDs []string // Perform a complete import of specific VMs.
	DisableLock         bool     // Disable PID locking and allow multiple concurrent instances.
}

// ProxmoxConfig contains configuration for connecting to a Proxmox
// Datacenter Manager.
type ProxmoxConfig struct {
	URL                 string          // URL of the PDM instance.
	InsecureSkipVerify  bool            // Skip validating the TLS certificate.
	MaxConns            int             // Maximum amount of parallel connections.
	APITokenID          string          // API Token ID.
	APITokenSecret      string          // API Token Secret.
	CompleteImport      bool            // Perform a complete import of all VMs managed by this datacenter.
	CompleteImportUUIDs []string        // Perform a complete import of specific VMs.
	CLUSTER             []ClusterConfig // Configuration for individual clusters.
}

type ClusterConfig struct {
	Cluster             string   // Name of the Cluster (as it is known to PDM).
	URL                 string   // URL of a PVE node or loadbalancer.
	InsecureSkipVerify  bool     // Skip validating the TLS certificate.
	MaxConns            int      // Maximum amount of parallel connections.
	APITokenID          string   // API Token ID.
	APITokenSecret      string   // API Token Secret.
	CompleteImport      bool     // Perform a complete import of all VMs in this cluster.
	CompleteImportUUIDs []string // Perform a complete import of specific VMs.
}

// LoadConfig loads the configuration from a TOML file.
func LoadConfig(appname string) (*Config, error) {
	cfg, err := config.LoadConfig[Config](appname)
	if err != nil {
		return nil, err
	}

	if cfg.GENERAL.TimeoutSeconds == 0 {
		cfg.GENERAL.TimeoutSeconds = defaultTimeoutSeconds
	}

	for i := range cfg.PROXMOX {
		if cfg.PROXMOX[i].MaxConns == 0 {
			cfg.PROXMOX[i].MaxConns = defaultMaxConns
		} else if cfg.PROXMOX[i].MaxConns < 0 {
			cfg.PROXMOX[i].MaxConns = 0 // no limit
		}
	}

	cfg.PushCompleteImportConfig()

	return cfg, nil
}

// PushCompleteImportConfig ensures that all Proxmox- and Cluster-
// level configuration structs inherit the complete import
// configuration of any higher level configuration structs.
func (cfg *Config) PushCompleteImportConfig() {
	for di := range cfg.PROXMOX {
		cfg.PROXMOX[di].CompleteImport =
			cfg.PROXMOX[di].CompleteImport || cfg.GENERAL.CompleteImport
		cfg.PROXMOX[di].CompleteImportUUIDs =
			slices.Concat(cfg.PROXMOX[di].CompleteImportUUIDs, cfg.GENERAL.CompleteImportUUIDs)

		for ci := range cfg.PROXMOX[di].CLUSTER {
			cfg.PROXMOX[di].CLUSTER[ci].CompleteImport =
				cfg.PROXMOX[di].CLUSTER[ci].CompleteImport || cfg.PROXMOX[di].CompleteImport
			cfg.PROXMOX[di].CLUSTER[ci].CompleteImportUUIDs =
				slices.Concat(cfg.PROXMOX[di].CLUSTER[ci].CompleteImportUUIDs, cfg.PROXMOX[di].CompleteImportUUIDs)
		}
	}
}
