package source

import (
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-loadbalancer/pkg/processor"
)

const ExportFilename = "loadbalancer_export.json"

// LoadBalancerDataSource is the concrete DataSource implementation for the loadbalancer management API.
type LoadBalancerDataSource = datasource.JsonFileSource[*processor.LoadBalancerData]

// NewLoadBalancerSource wires a Processor into a JsonFileSource that writes a local JSON
// export file and forwards the data to the MCMP backend API.
func NewLoadBalancerSource(
	enabled bool,
	proc *processor.Processor,
	mcmpClient *mcmp.Client,
	apiEndpoint string,
	logger logging.Logger,
) *LoadBalancerDataSource {
	return &LoadBalancerDataSource{
		Hostname:       "loadbalancer",
		Enabled:        enabled,
		ExportFilename: ExportFilename,
		Logger:         logger,
		McmpClient:     mcmpClient,
		ApiEndpoint:    apiEndpoint,
		Fetcher:        proc.FetchData,
	}
}
