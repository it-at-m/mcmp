package processor

import (
	"context"
	"fmt"
	"time"

	awx "github.com/euerla/goawx/client"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const awxPageSize = "500"

var (
	ErrUsernameRequired    = fmt.Errorf("AWX username is required")
	ErrPasswordRequired    = fmt.Errorf("AWX password is required")
	ErrApiEndpointRequired = fmt.Errorf("AWX API endpoint is required")
	ErrInventoryIdRequired = fmt.Errorf("AWX inventory IDs must be positive")
)

type InventoryHost struct {
	Created    time.Time `json:"created"`
	FQDN       string    `json:"fqdn"`
	User       string    `json:"user"`
	ValidUntil string    `json:"valid_until"`
}

type AWXExport struct {
	app.EaiMetadata             `json:"metadata"`
	LinuxHosts                  []InventoryHost `json:"linux_hosts"`
	WindowsHosts                []InventoryHost `json:"windows_hosts"`
	WindowsMaintenanceModeHosts []InventoryHost `json:"windows_maintenance_mode_hosts"`
}

func (e *AWXExport) SetEaiMetadata(meta app.EaiMetadata) { e.EaiMetadata = meta }
func (e *AWXExport) GetEaiMetadata() app.EaiMetadata     { return e.EaiMetadata }

type Config struct {
	Enabled                           bool   `mapstructure:"Enabled"`
	Username                          string `mapstructure:"Username"`
	Password                          string `mapstructure:"Password"`
	ApiEndpoint                       string `mapstructure:"ApiEndpoint"`
	LinuxRootPermitsInventoryId       int    `mapstructure:"LinuxRootPermitsInventoryId"`
	WindowsAdminPermitsInventoryId    int    `mapstructure:"WindowsAdminPermitsInventoryId"`
	WindowsMaintenanceModeInventoryId int    `mapstructure:"WindowsMaintenanceModeInventoryId"`
}

func (c *Config) Validate() error {
	if !c.Enabled {
		return nil
	}
	if c.Username == "" {
		return ErrUsernameRequired
	}
	if c.Password == "" {
		return ErrPasswordRequired
	}
	if c.ApiEndpoint == "" {
		return ErrApiEndpointRequired
	}
	if c.LinuxRootPermitsInventoryId <= 0 ||
		c.WindowsAdminPermitsInventoryId <= 0 ||
		c.WindowsMaintenanceModeInventoryId <= 0 {
		return ErrInventoryIdRequired
	}
	return nil
}

type Processor struct {
	client *awx.AWX
	cfg    Config
	logger logging.Logger
}

func NewProcessor(cfg Config, logger logging.Logger) (*Processor, error) {
	awxClient, err := awx.NewAWX(cfg.ApiEndpoint, cfg.Username, cfg.Password, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create AWX client for %s: %w", cfg.ApiEndpoint, err)
	}

	return &Processor{
		client: awxClient,
		cfg:    cfg,
		logger: logger,
	}, nil
}

func (p *Processor) Fetch(ctx context.Context) (*AWXExport, error) {
	p.logger.Info("Starting AWX inventory discovery", "endpoint", p.cfg.ApiEndpoint)

	export := &AWXExport{
		LinuxHosts:                  make([]InventoryHost, 0),
		WindowsHosts:                make([]InventoryHost, 0),
		WindowsMaintenanceModeHosts: make([]InventoryHost, 0),
	}

	params := map[string]string{"page_size": awxPageSize}

	// Linux
	res, err := p.client.InventoriesService.GetHostsByInventoryID(p.cfg.LinuxRootPermitsInventoryId, params)
	if err != nil {
		return nil, fmt.Errorf("failed to get Linux inventory: %w", err)
	}
	p.processHosts(res.Results, &export.LinuxHosts, "Linux Root Permits")

	// Windows Admin
	res, err = p.client.InventoriesService.GetHostsByInventoryID(p.cfg.WindowsAdminPermitsInventoryId, params)
	if err != nil {
		return nil, fmt.Errorf("failed to get Windows admin inventory: %w", err)
	}
	p.processHosts(res.Results, &export.WindowsHosts, "Windows Admin Permits")

	// Windows Maintenance
	res, err = p.client.InventoriesService.GetHostsByInventoryID(p.cfg.WindowsMaintenanceModeInventoryId, params)
	if err != nil {
		return nil, fmt.Errorf("failed to get Windows maintenance inventory: %w", err)
	}
	p.processHosts(res.Results, &export.WindowsMaintenanceModeHosts, "Windows Maintenance")

	return export, nil
}

func (p *Processor) processHosts(results []*awx.Host, target *[]InventoryHost, hostType string) {
	for _, host := range results {
		if host == nil {
			continue
		}
		vars := host.GetVariablesAsMap()
		if vars == nil {
			continue
		}

		fqdn, _ := vars["fqdn"].(string)
		user, ok := vars["user"].(string)
		if !ok {
			user, _ = vars["requester_username"].(string)
		}
		validUntil, _ := vars["valid_until"].(string)

		if fqdn != "" && user != "" && validUntil != "" {
			*target = append(*target, InventoryHost{
				Created:    host.Created,
				FQDN:       fmt.Sprintf("%v", fqdn),
				User:       fmt.Sprintf("%v", user),
				ValidUntil: fmt.Sprintf("%v", validUntil),
			})
		}
	}
	p.logger.Debug("Processed hosts", "type", hostType, "count", len(*target))
}
