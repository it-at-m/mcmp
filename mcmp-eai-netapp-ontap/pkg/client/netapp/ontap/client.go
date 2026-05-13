package ontap

import (
	"context"
	"fmt"
	"net/url"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/httpclient"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const (
	apiPrefixVolumes        = "/api/storage/volumes"
	apiPrefixExportPolicies = "/api/protocols/nfs/export-policies"
	apiPrefixCIFSShares     = "/api/protocols/cifs/shares"
	apiPrefixQTrees         = "/api/storage/qtrees"
	apiPrefixQuotas         = "/api/storage/quota/reports"
	apiPatternSnapshots     = "/api/storage/volumes/%s/snapshots"
	apiPrefixAggregates     = "/api/storage/aggregates"
	fieldsVolumes           = "name,svm.name,svm.uuid,uuid,style,state,type,snapshot_policy.name,nas.path,nas.export_policy.id,clone.is_flexclone,size,space.snapshot.reserve_percent,space.snapshot.reserve_size,space.afs_total,space.logical_space.used_by_afs,space.logical_space.used,space.logical_space.available,space.logical_space.used_percent,space.available_percent,space.snapshot.used,clone.parent_volume.uuid,clone.parent_volume.name,clone.parent_svm.uuid,clone.parent_svm.name,clone.parent_snapshot.uuid,clone.parent_snapshot.name,clone.split_initiated,snaplock.type,snaplock.append_mode_enabled,snaplock.autocommit_period,snaplock.retention.default,snaplock.retention.minimum,snaplock.retention.maximum,aggregates.uuid"
	fieldsExportPolicies    = "name,id,svm.name,rules.protocols,rules.rw_rule,rules.ro_rule,rules.clients.match,rules.index"
	fieldsCIFSShares        = "volume.uuid,volume.name,name,acls.permission,acls.user_or_group,svm.name,svm.uuid,path"
	fieldsSnapshots         = "name,uuid,create_time,volume.name,volume.uuid,svm.name,svm.uuid"
	fieldsQTrees            = "id,volume.uuid,volume.name,nas.path,export_policy.id,name,svm.name,security_style"
	fieldsQuotas            = "svm.name,space.used.hard_limit_percent,space.used.total,space.hard_limit,qtree.id,qtree.name,volume.uuid,volume.name,type"
	fieldsAggregates        = "uuid,name,block_storage.mirror.enabled,block_storage.primary.disk_class"
)

type ResourceResponse[T any] interface {
	GetRecords() []T
}

// Config holds all configuration parameters for the ONTAP Client
type Config struct {
	Hostname  string
	Username  string
	Password  string
	Enabled   bool
	VerifyTLS bool
}

func (c *Config) Validate() error {
	if !c.Enabled {
		return nil // Disabled configurations don't need validation
	}
	if c.Hostname == "" {
		return fmt.Errorf("ONTAP hostname is required")
	}
	if c.Username == "" {
		return fmt.Errorf("ONTAP username is required")
	}
	if c.Password == "" {
		return fmt.Errorf("ONTAP password is required")
	}
	return nil
}

type Client struct {
	config Config
	client *httpclient.Client
	logger logging.Logger
}

func NewClient(config Config, logger logging.Logger) (*Client, error) {
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	httpClientConfig := httpclient.Config{
		Username:        config.Username,
		Password:        config.Password,
		EnableTLSVerify: config.VerifyTLS,
	}

	client, err := httpclient.NewClient(httpClientConfig, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize http client: %w", err)
	}

	return &Client{
		config: config,
		client: client,
		logger: logger,
	}, nil
}

func (c *Client) Hostname() string {
	return c.config.Hostname
}

func (c *Client) IsEnabled() bool {
	return c.config.Enabled
}

func (c *Client) getBaseURL() string {
	return "https://" + c.config.Hostname
}

func fetchResources[T any, R ResourceResponse[T]](ctx context.Context, c *Client, endpointPath string, fields string, queryParams ...string) ([]T, error) {
	var res R

	// Construct Full URL
	baseURL := c.getBaseURL()
	fullPath, err := url.JoinPath(baseURL, endpointPath)
	if err != nil {
		return nil, fmt.Errorf("failed to construct base URL: %w", err)
	}

	u, err := url.Parse(fullPath)
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}

	q := u.Query()
	q.Set("fields", fields)

	for _, param := range queryParams {
		if key, val, ok := splitQueryParam(param); ok {
			q.Add(key, val)
		}
	}
	u.RawQuery = q.Encode()

	if err := c.client.GetJSON(ctx, u.String(), &res); err != nil {
		return nil, err
	}
	return res.GetRecords(), nil
}

// splitQueryParam simple helper to split "key=value"
func splitQueryParam(param string) (string, string, bool) {
	for i := 0; i < len(param); i++ {
		if param[i] == '=' {
			return param[:i], param[i+1:], true
		}
	}
	return "", "", false
}

func (c *Client) FetchVolumes(ctx context.Context) ([]Volume, error) {
	return fetchResources[Volume, *VolumeResponse](ctx, c, apiPrefixVolumes, fieldsVolumes)
}

func (c *Client) FetchExportPolicies(ctx context.Context) ([]ExportPolicy, error) {
	return fetchResources[ExportPolicy, *ExportPolicyResponse](ctx, c, apiPrefixExportPolicies, fieldsExportPolicies)
}

func (c *Client) FetchCIFSShares(ctx context.Context) ([]CIFSShare, error) {
	return fetchResources[CIFSShare, *CIFSShareResponse](ctx, c, apiPrefixCIFSShares, fieldsCIFSShares)
}

func (c *Client) FetchSnapshots(ctx context.Context, volumeUUID string) ([]Snapshot, error) {
	endpoint := fmt.Sprintf(apiPatternSnapshots, volumeUUID)
	return fetchResources[Snapshot, *SnapshotResponse](ctx, c, endpoint, fieldsSnapshots)
}

func (c *Client) FetchQTrees(ctx context.Context) ([]QTree, error) {
	// Filter out qtrees that represent the volume itself (name is empty)
	// 'name=!' means "name is not empty" in ONTAP REST API syntax
	return fetchResources[QTree, *QTreeResponse](ctx, c, apiPrefixQTrees, fieldsQTrees, "id=>0")
}

func (c *Client) FetchQuotas(ctx context.Context) ([]Quota, error) {
	return fetchResources[Quota, *QuotasResponse](ctx, c, apiPrefixQuotas, fieldsQuotas, "type=tree")
}

func (c *Client) FetchAggregates(ctx context.Context) ([]Aggregate, error) {
	return fetchResources[Aggregate, *AggregateResponse](ctx, c, apiPrefixAggregates, fieldsAggregates)
}
