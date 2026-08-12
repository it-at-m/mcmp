package processor

import (
	"context"
	"fmt"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/repo"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/snow"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

type RepositoryStatus struct {
	repo.RepositoryInfo
	DiscoverySuccess bool   `json:"discovery_success"`
	SysId            string `json:"sys_id"`
	OperationStatus  string `json:"operation_status"`
	ClassName        string `json:"class_name"`
	ErrorCount       int    `json:"error_count"`
	WarningCount     int    `json:"warning_count"`
}

type RepoExport struct {
	app.EaiMetadata `json:"metadata"`
	Repositories    []RepositoryStatus `json:"repositories"`
}

func (e *RepoExport) SetEaiMetadata(meta app.EaiMetadata) {
	e.EaiMetadata = meta
}

func (e *RepoExport) GetEaiMetadata() app.EaiMetadata {
	return e.EaiMetadata
}

type Processor struct {
	repoClient   *repo.Client
	snowClient   *snow.Client
	logger       logging.Logger
	waitDuration time.Duration
}

func NewProcessor(repoClient *repo.Client, snowClient *snow.Client, logger logging.Logger) *Processor {
	return &Processor{
		repoClient: repoClient,
		snowClient: snowClient,
		logger:     logger,
	}
}

func (p *Processor) SetWaitDuration(d time.Duration) {
	p.waitDuration = d
}

func (p *Processor) Fetch(ctx context.Context) (*RepoExport, error) {
	p.logger.Info("Starting repository discovery")

	repos, err := p.repoClient.ListRepositories(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to list repositories: %w", err)
	}

	results := make([]RepositoryStatus, 0, len(repos))
	hasErrors := false

	if p.snowClient == nil {
		p.logger.Warn("ServiceNow client is not configured, marking discovery as failed")
		hasErrors = true
	}

	for i, r := range repos {
		select {
		case <-ctx.Done():
			return nil, fmt.Errorf("discovery cancelled: %w", ctx.Err())
		default:
		}

		if i > 0 && p.waitDuration > 0 && p.snowClient != nil {
			p.logger.Debug("Waiting before next ServiceNow request", "duration", p.waitDuration.String())
			select {
			case <-ctx.Done():
				return nil, fmt.Errorf("discovery cancelled during wait: %w", ctx.Err())
			case <-time.After(p.waitDuration):
			}
		}

		status := RepositoryStatus{
			RepositoryInfo:   r,
			DiscoverySuccess: true,
		}

		if p.snowClient != nil {
			resp, err := p.snowClient.IdentifyReconcilePackageRepository(ctx, r.Name)
			if err != nil {
				p.logger.Error("ServiceNow reconciliation failed", "repo", r.Name, "error", err)
				status.DiscoverySuccess = false
				hasErrors = true
			} else if resp != nil {
				if resp.Result.HasError {
					p.logger.Error("ServiceNow reconciliation returned error in result", "repo", r.Name)
					status.DiscoverySuccess = false
					hasErrors = true
				}
				if len(resp.Result.Items) > 0 {
					item := resp.Result.Items[0]
					status.SysId = item.SysId
					status.OperationStatus = item.Operation
					status.ClassName = item.ClassName
					status.ErrorCount = item.ErrorCount
					status.WarningCount = item.WarningCount

					if item.ErrorCount > 0 {
						status.DiscoverySuccess = false
						hasErrors = true
					}
				}
			}
		}
		results = append(results, status)
	}

	export := &RepoExport{
		Repositories: results,
	}

	meta := app.NewEaiMetadata("mcmp-eai-snow-repo-discovery", time.Now())
	if hasErrors {
		meta.Status = "ERROR"
	} else {
		meta.Status = "SUCCESS"
	}
	export.SetEaiMetadata(meta)

	return export, nil
}
