package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-db-oracle/pkg/processor"
)

const exportFilePattern = "oracle_export_%s.json"

type OracleSource = datasource.JsonFileSource[*processor.OracleExport]

func NewOracleSource(
	hostname string,
	enabled bool,
	dataProcessor *processor.Processor,
	mcmpClients []datasource.JSONSender,
	apiEndpoints []string,
	logger logging.Logger,
) *OracleSource {
	return &OracleSource{
		Hostname:       hostname,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, hostname),
		Logger:         logger,
		McmpClients:    mcmpClients,
		ApiEndpoints:   apiEndpoints,
		Fetcher:        dataProcessor.FetchDatabaseMetrics,
	}
}
