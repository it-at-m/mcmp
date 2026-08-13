package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-awx/pkg/processor"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

const exportFilePattern = "awx_inventory_%s.json"

type AWXSource = datasource.JsonFileSource[*processor.AWXExport]

func NewAWXSource(
	identifier string,
	enabled bool,
	dataProcessor *processor.Processor,
	mcmpClients []datasource.JSONSender,
	apiEndpoints []string,
	logger logging.Logger,
) *AWXSource {
	return &AWXSource{
		Hostname:       identifier,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, identifier),
		Logger:         logger,
		McmpClients:    mcmpClients,
		ApiEndpoints:   apiEndpoints,
		Fetcher:        dataProcessor.Fetch,
	}
}
