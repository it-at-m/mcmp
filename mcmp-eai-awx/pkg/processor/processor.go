package processor

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	awx "github.com/euerla/goawx/client"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const awxPageSize = "200"

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
	cfg.ApiEndpoint = strings.TrimRight(strings.TrimSpace(cfg.ApiEndpoint), "/")
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

	// Linux
	linuxHosts, err := p.fetchAllHosts(ctx, p.cfg.LinuxRootPermitsInventoryId)
	if err != nil {
		return nil, fmt.Errorf("failed to get Linux inventory: %w", err)
	}
	p.processHosts(linuxHosts, &export.LinuxHosts, "Linux Root Permits")

	// Windows Admin
	winAdminHosts, err := p.fetchAllHosts(ctx, p.cfg.WindowsAdminPermitsInventoryId)
	if err != nil {
		return nil, fmt.Errorf("failed to get Windows admin inventory: %w", err)
	}
	p.processHosts(winAdminHosts, &export.WindowsHosts, "Windows Admin Permits")

	// Windows Maintenance
	winMaintHosts, err := p.fetchAllHosts(ctx, p.cfg.WindowsMaintenanceModeInventoryId)
	if err != nil {
		return nil, fmt.Errorf("failed to get Windows maintenance inventory: %w", err)
	}
	p.processHosts(winMaintHosts, &export.WindowsMaintenanceModeHosts, "Windows Maintenance")

	return export, nil
}

// fetchAllHosts loads all hosts of an inventory across every page.
// AWX returns at most `page_size` hosts per request; subsequent pages are
// requested via an explicit `page` counter.
// ctx is evaluated between requests because the AWX requester itself does
// not accept a context.
func (p *Processor) fetchAllHosts(ctx context.Context, inventoryID int) ([]*awx.Host, error) {
	var all []*awx.Host
	var expected int

	for page := 1; ; page++ {
		if err := ctx.Err(); err != nil {
			return all, err
		}

		res, err := p.client.InventoriesService.GetHostsByInventoryID(inventoryID, map[string]string{
			"page":      strconv.Itoa(page),
			"page_size": awxPageSize,
		})
		if err != nil {
			return all, fmt.Errorf("page %d: %w", page, err)
		}

		if page == 1 {
			expected = res.Count
		}

		all = append(all, res.Results...)

		p.logger.Debug("Fetched inventory host page",
			"inventory_id", inventoryID,
			"page", page,
			"page_size", awxPageSize,
			"results_on_page", len(res.Results),
			"total_so_far", len(all),
			"expected", expected,
			"has_next", res.Next != nil,
		)

		if res.Next == nil || len(res.Results) == 0 {
			break
		}
	}

	if expected != len(all) {
		p.logger.Warn("Host count mismatch after pagination",
			"inventory_id", inventoryID,
			"expected", expected,
			"got", len(all),
		)
	}

	p.logger.Debug("Fetched all inventory hosts",
		"inventory_id", inventoryID,
		"count", len(all),
	)

	return all, nil
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
