package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-ontap/pkg/client/netapp/ontap"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-ontap/pkg/processor"
)

const exportFilePattern = "netapp_ontap_export_%s.json"

// NewOntapSource create a generic JSON file source configured for ONTAP
func NewOntapSource(
	hostname string,
	enabled bool,
	processor *processor.Processor,
	mcmpClients []datasource.JSONSender,
	apiEndpoints []string,
	logger logging.Logger,
) *datasource.JsonFileSource[*ontap.OntapData] {
	return &datasource.JsonFileSource[*ontap.OntapData]{
		Hostname:       hostname,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, hostname),
		Logger:         logger,
		McmpClients:    mcmpClients,
		ApiEndpoints:   apiEndpoints,
		Fetcher:        processor.AggregateData,
	}
}
