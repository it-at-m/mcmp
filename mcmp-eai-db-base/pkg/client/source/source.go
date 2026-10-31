package source

import (
	"fmt"
	"strings"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-db-base/pkg/processor"
)

const exportFilenamePattern = "export_db_%s_%%s.json"

type DatabaseSource = datasource.JsonFileSource[*processor.Export]

func NewSource(
	dbType string,
	hostname string,
	enabled bool,
	dataProcessor *processor.Processor,
	mcmpClients []datasource.JSONSender,
	apiEndpoints []string,
	logger logging.Logger,
) *DatabaseSource {
	return &DatabaseSource{
		Hostname:       hostname,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilenamePattern, strings.ToLower(strings.Join(strings.Fields(dbType), "_"))),
		Logger:         logger,
		McmpClients:    mcmpClients,
		ApiEndpoints:   apiEndpoints,
		Fetcher:        dataProcessor.FetchDatabaseMetrics,
	}
}
