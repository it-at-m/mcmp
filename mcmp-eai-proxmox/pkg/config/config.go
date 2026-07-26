package config

import (
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
	GENERAL    GeneralConfig      // General EAI configuration.
	DATACENTER []DatacenterConfig // Proxmox Datacenter Manager configuration. Imports from all clusters.
	MCMP       []mcmp.Config      // MCMP configuration. All instances will receive the same data.
	LOGGING    logging.LogConfig  // Logging configuration.
}

// GeneralConfig contains general EAI configuration.
type GeneralConfig struct {
	TimeoutSeconds int  // Timeout for all processing. Negative values disable the timeout.
	SkipProxmox    bool // Skip the Proxmox import and read from the export file.
	SkipMCMP       bool // Skip the MCMP export and only write an export file.
}

// DatacenterConfig contains configuration for connecting to a Proxmox
// Datacenter Manager.
type DatacenterConfig struct {
	URL                string // URL of the PDM instance.
	InsecureSkipVerify bool   // Skip validating the PDM's TLS certificate.
	MaxConns           int    // Maximum amount of parallel connections to PDM.
	APITokenID         string // API Token ID for PDM.
	APITokenSecret     string // API Token Secret for PDM.
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

	for i := range cfg.DATACENTER {
		if cfg.DATACENTER[i].MaxConns == 0 {
			cfg.DATACENTER[i].MaxConns = defaultMaxConns
		} else if cfg.DATACENTER[i].MaxConns < 0 {
			cfg.DATACENTER[i].MaxConns = 0 // no limit
		}
	}

	return cfg, nil
}
