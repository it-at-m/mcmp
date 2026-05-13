package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-ucs/pkg/processor"
)

const exportFilePattern = "%s_export_%s.json"

type UCSSource = datasource.JsonFileSource[*processor.Cloud]

func NewUCSSource(
	hostname string,
	enabled bool,
	dataProcessor *processor.Processor,
	mcmpClients []datasource.JSONSender,
	apiEndpoints []string,
	logger logging.Logger,
	isCIMC bool,
) *UCSSource {
	exportType := "ucsm"
	if isCIMC {
		exportType = "cimc"
	}
	return &UCSSource{
		Hostname:       hostname,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, exportType, hostname),
		Logger:         logger,
		McmpClients:    mcmpClients,
		ApiEndpoints:   apiEndpoints,
		Fetcher:        dataProcessor.AggregateData,
	}
}
