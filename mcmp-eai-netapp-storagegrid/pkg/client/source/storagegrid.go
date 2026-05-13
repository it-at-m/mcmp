package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-storagegrid/pkg/client/netapp/storagegrid"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-storagegrid/pkg/processor"
)

const exportFilePattern = "netapp_storagegrid_export_%s.json"

// NewStorageGridSource creates a generic JSON file source configured for StorageGRID
func NewStorageGridSource(
	hostname string,
	enabled bool,
	processor *processor.Processor,
	mcmpClient *mcmp.Client,
	apiEndpoint string,
	logger logging.Logger,
) *datasource.JsonFileSource[*storagegrid.StorageGridData] {
	return &datasource.JsonFileSource[*storagegrid.StorageGridData]{
		Hostname:       hostname,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, hostname),
		Logger:         logger,
		McmpClient:     mcmpClient,
		ApiEndpoint:    apiEndpoint,
		Fetcher:        processor.AggregateData,
	}
}
