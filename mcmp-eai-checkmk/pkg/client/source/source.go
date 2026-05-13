package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-checkmk/pkg/processor"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const exportFilePattern = "checkmk_export_%s.json"

type CheckMkDataSource = datasource.JsonFileSource[*processor.CheckmkAggregatedData]

func NewCheckMkSource(
	hostname string,
	enabled bool,
	dataProcessor *processor.Processor,
	mcmpClient *mcmp.Client,
	apiEndpoint string,
	logger logging.Logger,
) *CheckMkDataSource {
	return &CheckMkDataSource{
		Hostname:       hostname,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, hostname),
		Logger:         logger,
		McmpClient:     mcmpClient,
		ApiEndpoint:    apiEndpoint,
		Fetcher:        dataProcessor.AggregateData,
	}
}
