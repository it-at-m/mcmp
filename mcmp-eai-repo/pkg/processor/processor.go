package processor

import (
	"context"
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/repo"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

type RepoExport struct {
	app.EaiMetadata `json:"metadata"`
	Repositories    []repo.RepositoryInfo `json:"repositories"`
}

func (e *RepoExport) SetEaiMetadata(meta app.EaiMetadata) {
	e.EaiMetadata = meta
}

func (e *RepoExport) GetEaiMetadata() app.EaiMetadata {
	return e.EaiMetadata
}

type Processor struct {
	repoClient *repo.Client
	logger     logging.Logger
}

func NewProcessor(repoClient *repo.Client, logger logging.Logger) *Processor {
	return &Processor{
		repoClient: repoClient,
		logger:     logger,
	}
}

func (p *Processor) Fetch(ctx context.Context) (*RepoExport, error) {
	p.logger.Info("Starting repository discovery")

	repos, err := p.repoClient.ListRepositories(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to list repositories: %w", err)
	}

	if repos == nil {
		repos = []repo.RepositoryInfo{}
	}

	return &RepoExport{
		Repositories: repos,
	}, nil
}
