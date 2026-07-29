package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-repo/pkg/processor"
)

const exportFilePattern = "repo_%s.json"

type RepoSource = datasource.JsonFileSource[*processor.RepoExport]

func NewRepoSource(
	identifier string,
	enabled bool,
	dataProcessor *processor.Processor,
	mcmpClients []datasource.JSONSender,
	apiEndpoints []string,
	logger logging.Logger,
) *RepoSource {
	return &RepoSource{
		Hostname:       identifier,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, identifier),
		Logger:         logger,
		McmpClients:    mcmpClients,
		ApiEndpoints:   apiEndpoints,
		Fetcher:        dataProcessor.Fetch,
	}
}
