package mcmp

import (
	"context"
	"errors"
	"fmt"

	commonmcmp "github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

var ErrServerIDRequired = errors.New("server ID is required")

// Client wraps the common MCMP client and adds rightsizing-specific methods.
// It embeds commonmcmp.Client, making all base client methods automatically available.
// This design allows for clean separation of concerns while reusing core HTTP and OAuth2 functionality.
//
// The Client extends the common MCMP client by adding domain-specific operations for
// fetching server lists and detailed server metrics from the MCMP API.
type Client struct {
	*commonmcmp.Client   // Embedded: all methods of commonmcmp.Client are automatically available
	logger               logging.Logger
	serverListEndpoint   string
	serverMetricEndpoint string
}

// NewClient creates a new rightsizing-specific MCMP client.
//
// NewClient initializes a Client by creating the underlying common MCMP client
// and wrapping it with rightsizing-specific functionality. It reuses the OAuth2
// authentication and HTTP configuration from the common client, adding only the
// endpoint URLs specific to rightsizing operations.
//
// This constructor establishes a connection to the MCMP API, validates OAuth2
// credentials, and initializes the HTTP client with appropriate timeouts and
// retry policies.
//
// Parameters:
//   - ctx: Context for managing request lifecycle and timeouts during client initialization
//   - commonConfig: CommonMCMP HTTP and OAuth2 configuration (from Config.ToClientConfig())
//   - config: Rightsizing-specific configuration containing endpoint URLs
//   - logger: Logger instance for debugging and operational logging
//
// Returns:
//   - *Client: A fully initialized rightsizing MCMP client ready for API calls
//   - error: An error if the common client initialization fails or configuration is invalid
//
// Example:
//
//	commonConfig := cfg.MCMP.Config.ToClientConfig()
//	mcmpClient, err := mcmp.NewClient(ctx, commonConfig, cfg.MCMP, logger)
//	if err != nil {
//	    return fmt.Errorf("failed to create MCMP client: %w", err)
//	}
func NewClient(ctx context.Context, commonConfig commonmcmp.ClientConfig, config Config, logger logging.Logger) (*Client, error) {
	// Create the base common client
	baseClient, err := commonmcmp.NewClient(ctx, commonConfig, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to create base MCMP client: %w", err)
	}

	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	// Wrap it with rightsizing-specific functionality
	return &Client{
		Client:               baseClient,
		logger:               logger,
		serverListEndpoint:   config.ServerListEndpoint,
		serverMetricEndpoint: config.ServerMetricEndpoint,
	}, nil
}

// GetServerIDs retrieves all available server IDs from the MCMP API.
//
// GetServerIDs performs a GET request to the server list endpoint and unmarshals
// the response into a slice of server IDs. This method is typically called first
// in a rightsizing analysis workflow to obtain the complete list of servers that
// require metric analysis.
//
// The method leverages the embedded common client's GetJSONUnmarshal method,
// which handles OAuth2 authentication, retries with exponential backoff, and
// comprehensive error handling.
//
// Parameters:
//   - ctx: Context for controlling request timeout and cancellation
//
// Returns:
//   - []int64: A slice of server IDs retrieved from the API, in response order
//   - error: An error if the API request fails, the response is malformed, or
//     the context is cancelled. The error is wrapped with contextual information
//     about the operation that failed.
//
// Example:
//
//	serverIDs, err := mcmpClient.GetServerIDs(ctx)
//	if err != nil {
//	    logger.Error("failed to fetch server IDs", "error", err)
//	    return
//	}
//	logger.Info("found servers", "count", len(serverIDs))
//	for _, id := range serverIDs {
//	    metrics, _ := mcmpClient.GetServerMetrics(ctx, id)
//	    // process metrics...
//	}
func (c *Client) GetServerIDs(ctx context.Context) ([]int64, error) {
	var serverIDs []int64
	err := c.GetJSONUnmarshal(ctx, c.serverListEndpoint, &serverIDs)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch server IDs: %w", err)
	}

	c.logger.DebugPrintf("Retrieved %d server IDs from MCMP", len(serverIDs))
	return serverIDs, nil
}

// GetServerMetrics retrieves detailed metrics and resource information for a specific server.
//
// GetServerMetrics fetches comprehensive server data including current CPU and memory
// allocation, power state, boot time, and historical metrics (CPU utilization and
// memory usage samples). The metrics are essential for calculating rightsizing
// recommendations using percentile-based analysis.
//
// The server ID is interpolated into the serverMetricEndpoint URL pattern (which should
// contain %d as a placeholder). The method validates the server ID to prevent invalid API calls.
//
// Parameters:
//   - ctx: Context for controlling request timeout and cancellation
//   - serverId: The unique identifier of the server to fetch metrics for. Must be greater than 0.
//
// Returns:
//   - *GreenItServer: A pointer to a GreenItServer struct containing:
//   - Server identification: ID, CloudName, VMName, FQDN
//   - Current resources: NumCPU, MemoryMB
//   - Resource history: NumCPUPrev, MemoryMBPrev with change dates
//   - Power state and pending changes
//   - Metrics: A slice of historical ServerMetrics with CPU and memory utilization samples
//   - error: An error if:
//   - serverId is invalid (< 1)
//   - The API request fails
//   - The response cannot be unmarshaled into GreenItServer
//   - The context is cancelled or times out
//
// Example:
//
//	server, err := mcmpClient.GetServerMetrics(ctx, 12345)
//	if err != nil {
//	    logger.Error("failed to fetch metrics", "server_id", 12345, "error", err)
//	    return
//	}
//	logger.Info("server metrics retrieved",
//	    "vm_name", *server.VMName,
//	    "cpu", server.NumCPU,
//	    "memory_mb", server.MemoryMB,
//	    "metric_samples", len(server.Metrics),
//	)
func (c *Client) GetServerMetrics(ctx context.Context, serverId int64) (*GreenItServer, error) {
	if serverId < 1 {
		return nil, ErrServerIDRequired
	}

	endpoint := fmt.Sprintf(c.serverMetricEndpoint, serverId)

	var server GreenItServer
	err := c.GetJSONUnmarshal(ctx, endpoint, &server)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch server data for ID %d: %w", serverId, err)
	}

	c.logger.DebugPrintf("Retrieved data for server: %d", serverId)
	return &server, nil
}
