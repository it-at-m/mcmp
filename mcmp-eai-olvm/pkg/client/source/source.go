// Package source provides the data source implementation for the OLVM EAI integration.
//
// It bridges the OLVM processor layer with the generic datasource infrastructure
// from mcmp-eai-common, enabling the collection, serialization, and forwarding of
// OLVM virtual machine and host data to one or more MCMP API endpoints.
package source

import (
	"fmt"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/datasource"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-olvm/pkg/processor"
)

const exportFilePattern = "olvm_export_%s.json"

// OLVMSource is a specialized JSON file data source for OLVM cloud infrastructure data.
//
// OLVMSource is a type alias that extends [datasource.JsonFileSource] to handle
// OLVM-specific data structures. It implements the app.DataSource interface and is
// responsible for:
//
//   - Fetching aggregated OLVM data (VMs, hosts, clusters) via [processor.Processor.AggregateData]
//   - Marshaling the collected data into an indented JSON representation
//   - Writing the JSON output to a hostname-specific local file
//   - Sending the results to one or more MCMP API endpoints for persistence and
//     further processing
//
// The generic type parameter [*processor.Cloud] specifies that this data source
// operates on a [processor.Cloud] value, which aggregates all servers discovered
// from an OLVM environment.
//
// OLVMSource supports fan-out delivery: multiple [datasource.JSONSender] clients and
// their corresponding API endpoints can be supplied so that the same payload is
// forwarded to several MCMP instances in a single run.
//
// Usage:
//
//	olvmSource := source.NewOLVMSource(
//	    "olvm-prod",
//	    true,
//	    dataProcessor,
//	    []datasource.JSONSender{mcmpClient},
//	    []string{"https://mcmp.example.com/api/v1/olvm"},
//	    logger,
//	)
//
// See [datasource.JsonFileSource] for the underlying implementation and the full
// set of interface methods (Name, IsEnabled, FetchData, ProcessData).
type OLVMSource = datasource.JsonFileSource[*processor.Cloud]

// NewOLVMSource creates and initializes a new [OLVMSource] instance for processing
// and exporting OLVM infrastructure data.
//
// NewOLVMSource is a factory function that constructs a properly configured
// OLVMSource. It wires together the OLVM processor responsible for aggregating raw
// API responses with the MCMP clients used for delivering the resulting JSON payload,
// and configures file-based export at the same time.
//
// Parameters:
//   - hostname: A human-readable identifier for this data source. It is used in log
//     messages and to derive the export filename. Example values: "olvm-prod",
//     "datacenter-1".
//   - enabled: Controls whether the data source is active. When false the EAI
//     framework will skip this source without removing it from the pipeline, which is
//     useful for temporarily disabling an environment.
//   - dataProcessor: A [processor.Processor] instance that knows how to query the
//     OLVM API and aggregate VMs, hosts, and clusters into a [processor.Cloud]
//     structure. Must not be nil; its [processor.Processor.AggregateData] method is
//     registered as the Fetcher.
//   - mcmpClients: A slice of [datasource.JSONSender] implementations to which the
//     serialized payload will be sent. Each entry is paired with the endpoint at the
//     same index in apiEndpoints. Entries where the client or its paired endpoint is
//     empty are silently skipped.
//   - apiEndpoints: A slice of fully qualified MCMP API endpoint URLs, one per entry
//     in mcmpClients. Example: "https://mcmp.example.com/api/v1/olvm".
//   - logger: A [logging.Logger] used throughout the lifecycle of this source for
//     informational messages, warnings, and error reporting.
//
// Returns:
//   - *OLVMSource: A fully initialized OLVMSource ready to be registered with the
//     EAI application runner.
//
// The export filename is derived automatically from hostname using the pattern
// "olvm_export_<hostname>.json". For hostname "olvm-prod" the resulting file would
// be "olvm_export_olvm-prod.json".
//
// Example usage in application initialization:
//
//	olvmClient, err := olvm.NewClient(config.OLVM, logger)
//	if err != nil {
//	    return fmt.Errorf("failed to create OLVM client: %w", err)
//	}
//
//	dataProcessor, err := processor.NewProcessor(olvmClient, logger)
//	if err != nil {
//	    return fmt.Errorf("failed to create processor: %w", err)
//	}
//
//	olvmSource := source.NewOLVMSource(
//	    config.OLVM.Hostname,
//	    config.OLVM.Enabled,
//	    dataProcessor,
//	    []datasource.JSONSender{mcmpClient},
//	    []string{config.MCMP.OLVMEndpoint},
//	    logger,
//	)
//
//	sources := []app.DataSource[*processor.Cloud]{olvmSource}
//	if err := app.RunEAI(ctx, config, sources, logger); err != nil {
//	    return fmt.Errorf("failed to run EAI: %w", err)
//	}
//
// See also:
//   - [datasource.JsonFileSource]: The underlying generic data source implementation.
//   - [processor.Processor]: Aggregates raw OLVM API responses into a [processor.Cloud].
//   - [datasource.JSONSender]: Interface that MCMP clients must satisfy.
func NewOLVMSource(
	hostname string,
	enabled bool,
	dataProcessor *processor.Processor,
	mcmpClients []datasource.JSONSender,
	apiEndpoints []string,
	logger logging.Logger,
) *OLVMSource {
	return &OLVMSource{
		Hostname:       hostname,
		Enabled:        enabled,
		ExportFilename: fmt.Sprintf(exportFilePattern, hostname),
		Logger:         logger,
		McmpClients:    mcmpClients,
		ApiEndpoints:   apiEndpoints,
		Fetcher:        dataProcessor.AggregateData,
	}
}
