// Package processor provides the core rightsizing analysis engine for virtual machines.
//
// This package implements a Kubernetes Vertical Pod Autoscaler (VPA)-inspired algorithm
// to calculate optimal CPU and memory allocations based on historical utilization metrics.
// The processor analyzes server metrics using percentile-based calculations and applies
// configurable safety margins to generate rightsizing recommendations.
//
// Key Features:
//   - Parallel processing of multiple servers using a worker pool pattern
//   - Multi-signal utilization analysis (P90, P95, P99, average)
//   - Peak-aware adjustments for recurring high load
//   - Safety margins to prevent under-provisioning
//   - Minimum sample size validation for reliable recommendations
//   - Comprehensive logging and progress tracking
//
// Algorithm Overview:
// The rightsizing algorithm follows these steps:
// 1. Fetch historical metrics for each server
// 2. Derive effective utilization using multiple signals:
//   - P95 as baseline
//   - P99 for outlier detection
//   - Average as stability floor
//   - Peak detection for recurring load patterns
//
// 3. Compute required resources based on percentile utilization
// 4. Apply safety margins (default 15%) to account for spikes
// 5. Round results to sensible increments (1 CPU core, 1024MB for memory)
// 6. Compare with current allocation and flag if changes are recommended
//
// Example usage:
//
//	cfg := processor.Config{
//	    WorkerCount:             10,
//	    CPUMarginFactor:         1.15,
//	    MemoryMarginFactor:      1.15,
//	    MinSampleSize:           60,
//	    CooldownDays:            7,
//	    CPUHighLoadThreshold:    85,
//	    MemoryHighLoadThreshold: 90,
//	}
//	proc, err := processor.NewProcessor(mcmpClient, logger, cfg)
//	if err != nil {
//	    log.Fatal(err)
//	}
//	rightsizing, err := proc.ComputeRightsizing(ctx)
//	if err != nil {
//	    log.Fatal(err)
//	}
package processor

import (
	"context"
	"errors"
	"fmt"
	"math"
	"regexp"
	"slices"
	"sync"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-rightsizing/pkg/client/mcmp"
)

const (
	// Safety margins (similar to Kubernetes VPA)
	cpuMarginFactor    = 1.15 // 15% overhead for CPU
	memoryMarginFactor = 1.15 // 15% overhead for memory

	// Minimum sample size for reliable recommendations
	minSampleSize = 10080

	cooldownDays = 7 // Default cooldown after resource changes

	// High-load thresholds used for peak detection.
	// If utilization is equal or above the threshold,
	// the metric sample is considered a peak sample.
	CPUHighLoadThreshold    = 85.0 // CPU peak starts at 85%
	MemoryHighLoadThreshold = 90.0 // Memory peak starts at 90%

	progressLogInterval = 100
)

var (
	ErrNilClient        = errors.New("MCMP client must not be nil")
	ErrAllServersFailed = errors.New("all servers failed processing")
	reDP                = regexp.MustCompile(`\w+dp[ps][0-9]{3}.*`)
	reDB                = regexp.MustCompile(`\w+db[cdkps][0-9]{3}.*`)
)

type (
	// Config contains all configurable parameters for the rightsizing processor.
	//
	// Fields:
	//   - WorkerCount: Number of parallel workers for concurrent server processing.
	//     Determines how many servers are analyzed simultaneously.
	//     Default: 5
	//   - CooldownDays: Number of days to skip recommendations after a resource change.
	//     Applies independently per resource type (CPU and memory).
	//     Example: if CPU was changed 3 days ago and CooldownDays is 7, no CPU recommendation
	//     is generated, but a memory recommendation may still be shown.
	//     Default: 7
	//   - CPUMarginFactor: Safety margin multiplier for CPU recommendations (typically >= 1.0).
	//     A factor of 1.15 means 15% overhead is added to prevent under-provisioning.
	//     Example: if P95 CPU is 2.0 cores, recommendation = 2.0 * 1.15 = 2.3 cores → rounds to 2 cores
	//     Default: 1.15
	//   - MemoryMarginFactor: Safety margin multiplier for memory recommendations (typically >= 1.0).
	//     Applied similarly to CPUMarginFactor for memory allocation.
	//     Default: 1.15
	//   - MinSampleSize: Minimum number of valid metric samples required to generate a recommendation.
	//     Servers with fewer samples are skipped to ensure statistical reliability.
	//     Typical recommendation: 60 samples (represents ~1 hour at 1-minute intervals)
	//     Default: 60
	Config struct {
		WorkerCount             int     // Number of parallel workers
		CooldownDays            int     // Cooldown period in days after resource changes
		CPUMarginFactor         float64 // Safety margin for CPU
		MemoryMarginFactor      float64 // Safety margin for memory
		MinSampleSize           int     // Minimum number of metrics samples
		CPUHighLoadThreshold    float64 // Threshold above which CPU samples are counted as peak
		MemoryHighLoadThreshold float64 // Threshold above which memory samples are counted as peaks
	}

	// Processor is the main processor struct that coordinates rightsizing analysis.
	//
	// The Processor handles the entire workflow of analyzing server metrics and
	// generating rightsizing recommendations. It manages parallel processing through
	// a worker pool and applies the VPA-inspired algorithm to compute optimal
	// resource allocations.
	//
	// Fields:
	//   - mcmpClient: MCMP API client for fetching server metrics and sending recommendations
	//   - logger: Logger for recording analysis progress and errors
	//   - config: Configuration parameters controlling the analysis behavior
	Processor struct {
		mcmpClient *mcmp.Client
		logger     logging.Logger
		config     Config
	}

	// serverResult is an internal type used for collecting worker results.
	// It encapsulates either a successful RightsizingServer recommendation or an error.
	serverResult struct {
		rightsizingServer mcmp.RightsizingServer
		err               error
	}
)

// NewProcessor creates a new Processor instance with the given configuration.
//
// This constructor initializes a processor with validated configuration, applying
// sensible defaults for any unconfigured parameters. It ensures the MCMP client
// is not nil and sets up a logger if not provided.
//
// Parameters:
//   - client: MCMP API client used for server metrics retrieval and result submission.
//     Must not be nil.
//   - logger: Logger instance for recording analysis progress and debug information.
//     If nil, a no-op logger is used (silently discards all logs).
//   - config: Configuration parameters for the processor. Values <= 0 are replaced
//     with sensible defaults:
//   - WorkerCount defaults to 5
//   - CooldownDays defaults to 7
//   - CPUMarginFactor defaults to 1.15
//   - MemoryMarginFactor defaults to 1.15
//   - MinSampleSize defaults to 60
//   - CPUHighLoadThreshold defaults to 85
//   - MemoryHighLoadThreshold defaults to 90
//
// Returns:
//   - *Processor: A new processor instance ready for use
//   - error: ErrNilClient if the provided client is nil, or nil if successful
//
// Example:
//
//	mcmpClient, _ := mcmp.NewClient(ctx, config, logger)
//	processor, err := processor.NewProcessor(mcmpClient, logger, processorConfig)
//	if err != nil {
//	    log.Fatal(err)
//	}
func NewProcessor(client *mcmp.Client, logger logging.Logger, config Config) (*Processor, error) {
	if client == nil {
		return nil, ErrNilClient
	}

	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	// Validate and set defaults for config
	applyDefault(&config.WorkerCount, 5)
	applyDefault(&config.CooldownDays, cooldownDays)
	applyDefault(&config.CPUMarginFactor, cpuMarginFactor)
	applyDefault(&config.MemoryMarginFactor, memoryMarginFactor)
	applyDefault(&config.MinSampleSize, minSampleSize)
	applyDefault(&config.CPUHighLoadThreshold, CPUHighLoadThreshold)
	applyDefault(&config.MemoryHighLoadThreshold, MemoryHighLoadThreshold)

	return &Processor{
		mcmpClient: client,
		logger:     logger,
		config:     config,
	}, nil
}

// ComputeRightsizing analyzes all servers and generates rightsizing recommendations.
//
// This is the main entry point for the processor. It orchestrates the complete
// analysis workflow:
//
// 1. Fetches all server IDs from the MCMP API
// 2. Creates a worker pool with the configured number of workers
// 3. Distributes server processing across workers for parallel analysis
// 4. Collects results and returns aggregated rightsizing recommendations
// 5. Logs progress at regular intervals (every 100 servers)
//
// The method uses buffered channels to queue jobs and collect results,
// ensuring efficient concurrent processing without blocking.
//
// Error Handling:
//   - Individual server processing errors are logged and counted but don't stop
//     the overall analysis. The function attempts to process as many servers as possible.
//   - If ALL servers fail to process, an error is returned.
//   - Partial failures (some servers fail, others succeed) result in a successful
//     return with the successfully analyzed servers.
//
// Parameters:
//   - ctx: Context for cancellation and timeout. If cancelled, workers will
//     terminate early and return context errors.
//
// Returns:
//   - *mcmp.Rightsizing: Structure containing rightsizing recommendations for all
//     successfully analyzed servers
//   - error: An error if:
//   - Failed to fetch server IDs from MCMP
//   - All servers failed during processing (complete failure scenario)
//   - Context is cancelled or times out
//
// Example:
//
//	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
//	defer cancel()
//	rightsizing, err := processor.ComputeRightsizing(ctx)
//	if err != nil {
//	    log.Fatal(err)
//	}
//	fmt.Printf("Analyzed %d servers\n", len(rightsizing.Servers))
//
// Performance Notes:
// - With the default configuration, can analyze ~6000 servers in ~10 minutes
// - Actual performance depends on:
//   - Network latency to MCMP API
//   - Number of metrics samples per server
//   - Worker count and system resources
//   - API rate limits
func (p *Processor) ComputeRightsizing(ctx context.Context) (*mcmp.Rightsizing, error) {
	// Step 1: Fetch all server IDs
	serverIDs, err := p.mcmpClient.GetServerIDs(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get server IDs: %w", err)
	}

	p.logger.DebugPrintf("Starting rightsizing analysis for %d servers with %d workers", len(serverIDs), p.config.WorkerCount)

	// Step 2: Create a result structure
	result := &mcmp.Rightsizing{
		Servers: make([]mcmp.RightsizingServer, 0, len(serverIDs)),
	}

	// Step 3: Set up parallel processing with the worker pool
	jobs := make(chan int64, len(serverIDs))
	results := make(chan serverResult, len(serverIDs))

	// Start a worker pool
	var wg sync.WaitGroup
	workerCount := min(p.config.WorkerCount, len(serverIDs))
	for range workerCount {
		wg.Add(1)
		go p.worker(ctx, jobs, results, &wg)
	}

	// Send jobs to workers
	for _, id := range serverIDs {
		jobs <- id
	}
	close(jobs)

	// Wait for all workers to finish in a separate goroutine
	go func() {
		wg.Wait()
		close(results)
	}()

	// Step 4: Collect results
	processedCount := 0
	errorCount := 0

	for res := range results {
		if res.err != nil {
			p.logger.DebugPrintf("Error processing server: %v", res.err)
			errorCount++
			continue
		}

		result.Servers = append(result.Servers, res.rightsizingServer)
		processedCount++

		totalProcessed := processedCount + errorCount
		if totalProcessed%progressLogInterval == 0 {
			p.logger.DebugPrintf("Progress: %d/%d servers processed (%d errors)", totalProcessed, len(serverIDs), errorCount)
		}
	}

	p.logger.DebugPrintf("Rightsizing analysis completed: %d successful, %d errors out of %d total servers", processedCount, errorCount, len(serverIDs))

	if errorCount > 0 && processedCount == 0 {
		return nil, fmt.Errorf("%w: %d servers", ErrAllServersFailed, errorCount)
	}

	return result, nil
}

// worker processes server IDs from the jobs channel and sends results to the results channel.
//
// This method runs in a separate goroutine and is part of the worker pool pattern.
// Each worker continuously processes jobs from the shared jobs channel until the
// channel is closed. This allows multiple servers to be analyzed in parallel.
//
// The worker respects context cancellation and will stop processing and return
// if the context is cancelled mid-analysis.
//
// Parameters:
//   - ctx: Context for cancellation and timeout handling
//   - jobs: Receive-only channel providing server IDs to process
//   - results: Send-only channel for receiving processed results
//   - wg: WaitGroup used to coordinate shutdown
//
// Internal Behavior:
// - Runs until the jobs channel is closed
// - For each server ID, calls processServer() to fetch metrics and compute recommendations
// - Sends results immediately after each server is processed
// - Returns immediately if context is cancelled, sending a result with the context error
//
// Thread Safety:
// - Safe to run concurrently with other workers
// - Jobs channel is coordinated by the caller to ensure proper work distribution
// - Results channel is buffered and large enough for all jobs
func (p *Processor) worker(ctx context.Context, jobs <-chan int64, results chan<- serverResult, wg *sync.WaitGroup) {
	defer wg.Done()

	for serverID := range jobs {
		if ctx.Err() != nil {
			results <- serverResult{rightsizingServer: mcmp.RightsizingServer{ID: serverID}, err: ctx.Err()}
			continue
		}
		server, err := p.processServer(ctx, serverID)
		results <- serverResult{rightsizingServer: server, err: err}
	}
}

// processServer fetches metrics and calculates rightsizing recommendation for a single server.
//
// This method is called by workers and coordinates the steps for analyzing a single server:
// 1. Fetches server metrics from MCMP API using the server ID
// 2. Passes the metrics to calculateRightsizing() for analysis
// 3. Returns the rightsizing recommendation
//
// Error Handling:
// If the MCMP API call fails (e.g., server not found, network error), an error is
// returned wrapped with the server ID for debugging purposes.
//
// Parameters:
//   - ctx: Context for API calls and timeout handling
//   - serverID: The unique identifier of the server to analyze
//
// Returns:
//   - mcmp.RightsizingServer: Rightsizing recommendation for the server.
//     If an error occurs, the recommendation is empty/zero-valued.
//   - error: An error if:
//   - The MCMP API call fails
//   - Context is cancelled or times out
//   - Server not found in MCMP
//
// Example:
//
//	recommendation, err := processor.processServer(ctx, 12345)
//	if err != nil {
//	    log.Printf("Failed to process server 12345: %v", err)
//	}
func (p *Processor) processServer(ctx context.Context, serverID int64) (mcmp.RightsizingServer, error) {
	// Fetch server metrics
	server, err := p.mcmpClient.GetServerMetrics(ctx, serverID)
	if err != nil {
		return mcmp.RightsizingServer{}, fmt.Errorf("failed to fetch metrics for server %d: %w", serverID, err)
	}

	// Calculate rightsizing recommendation
	rightsizingServer := p.calculateRightsizing(server)

	return rightsizingServer, nil
}

// calculateRightsizing calculates rightsizing recommendations based on server metrics.
//
// This is the core algorithm implementation, inspired by Kubernetes Vertical Pod Autoscaler (VPA).
// The method:
//
// 1. Validates that sufficient metrics samples are available (>= MinSampleSize)
// 2. Calculates percentile values for CPU and memory utilization
// 3. Computes recommended resource allocations using the percentile values
// 4. Compares recommendations with current allocations
// 5. Flags the server for rightsizing if changes are recommended
//
// Algorithm Details:
//
// Percentile Calculation:
// - Extracts utilization values from historical metrics
// - Sorts values and calculates the configured percentile (default P95)
// - P95 means the recommendation handles usage levels that exceed actual usage 95% of the time
//
// Resource Recommendation:
// - Recommended = Used * SafetyMargin
// - Used = CurrentAllocation * (PercentileUtilization / 100)
// - Example: If a 4-core server shows P95 CPU of 40%, recommendation = (4 * 0.40) * 1.15 = 1.84 cores → 2 cores
//
// Rounding:
// - CPU: Rounded to nearest integer (minimum 1 core)
// - Memory: Rounded to nearest 1024MB increment (typical VM sizing), minimum 1024 MB
//
// Parameters:
//   - server: Server data including current allocation and historical metrics.
//     Expected to contain CPU, memory, and metrics samples.
//
// Returns:
//   - mcmp.RightsizingServer: A recommendation structure containing:
//   - ID: Server ID (copied from input)
//   - NumCPU, MemoryMB: Current allocation
//   - Recommended NumCPU and MemoryMB
//
// Logging:
// Logs at debug level:
// - When insufficient samples are available (skips recommendation)
// - Calculated percentiles and sample counts
// - Specific rightsizing recommendations when changes are flagged
//
// Example Output Log:
//
//	Server 12345 / myvm-prod-01 metrics: CPU P95=45.50%, Memory P95=72.30%, samples=120
//	Server 12345 / myvm-prod-01 rightsizing recommendation: CPU 8→4, Memory 16384MB→8192MB
func (p *Processor) calculateRightsizing(server *mcmp.GreenItServer) mcmp.RightsizingServer {
	vmName := "<unknown>"
	if server.VMName != nil {
		vmName = *server.VMName
	}

	result := mcmp.RightsizingServer{
		ID:       server.ID,
		NumCPU:   server.NumCPU,
		MemoryMB: server.MemoryMB,
	}

	// Check if we have enough metrics samples
	if len(server.Metrics) < p.config.MinSampleSize {
		p.logger.DebugPrintf("Server %d / %s : insufficient metrics samples (%d < %d), skipping recommendation", server.ID, vmName, len(server.Metrics), p.config.MinSampleSize)
		return result
	}

	// Derive effective utilization for CPU and memory using multi-signal analysis
	metrics := p.normalizeAndFilterMetrics(server)
	cpuPercentile, memPercentile, validSampleCount := p.calculatePercentiles(metrics)

	if validSampleCount < p.config.MinSampleSize {
		p.logger.DebugPrintf("Server %d / %s : insufficient valid samples (%d < %d), skipping recommendation", server.ID, vmName, validSampleCount, p.config.MinSampleSize)
		return result
	}

	p.logger.DebugPrintf("Server %d / %s metrics: CPU %.2f%%, Memory %.2f%%, samples=%d", server.ID, vmName, cpuPercentile, memPercentile, validSampleCount)

	// Check cooldown per resource type independently.
	// A change to one resource does not block recommendations for the other.
	cpuCooldown := server.NumCPUChangeDate != nil &&
		time.Now().Before(server.NumCPUChangeDate.AddDate(0, 0, p.config.CooldownDays))

	memCooldown := server.MemoryMBChangeDate != nil &&
		time.Now().Before(server.MemoryMBChangeDate.AddDate(0, 0, p.config.CooldownDays))

	if cpuCooldown {
		p.logger.DebugPrintf("Server %d / %s : CPU in cooldown until %s, skipping CPU recommendation",
			server.ID, vmName, server.NumCPUChangeDate.AddDate(0, 0, p.config.CooldownDays).Format("2006-01-02"))
	}

	if memCooldown {
		p.logger.DebugPrintf("Server %d / %s : memory in cooldown until %s, skipping memory recommendation",
			server.ID, vmName, server.MemoryMBChangeDate.AddDate(0, 0, p.config.CooldownDays).Format("2006-01-02"))
	}

	// Calculate recommended resources, respecting cooldown per resource type.
	// If a resource is in cooldown, keep the current allocation as recommendation.
	recommendedCPU := server.NumCPU
	if !cpuCooldown {
		recommendedCPU = p.calculateRecommendedCPU(vmName, server.NumCPU, cpuPercentile)
	}

	recommendedMemoryMB := server.MemoryMB
	if !memCooldown {
		recommendedMemoryMB = p.calculateRecommendedMemory(vmName, server.MemoryMB, memPercentile)
	}

	// Check if rightsizing is needed
	if recommendedCPU != server.NumCPU || recommendedMemoryMB != server.MemoryMB {
		result.NumCPU = recommendedCPU
		result.MemoryMB = recommendedMemoryMB

		p.logger.Printf("Server %d / %s rightsizing recommendation: CPU %d→%d, Memory %dMB→%dMB",
			server.ID, vmName, server.NumCPU, recommendedCPU, server.MemoryMB, recommendedMemoryMB)
	}

	return result
}

// calculatePercentiles derives effective CPU and memory utilization from historical metrics.
//
// Instead of relying on a single percentile (e.g. P95), this function combines multiple
// statistical signals to produce a more robust and production-safe utilization estimate.
//
// The algorithm addresses common shortcomings of single-percentile approaches:
//   - Bimodal workloads (idle + peak)
//   - Short-lived spikes (bursty traffic)
//   - Periodic workloads (cron jobs, batch processing)
//
// CPU Utilization Strategy:
//   - P95 is used as the primary baseline
//   - P99 is used to detect extreme outliers
//     → if P99 >> P95, the baseline is adjusted upward
//   - Average is used as a lower bound (floor) to prevent aggressive downsizing
//   - PeakFactor adds additional headroom for recurring high-load patterns
//   - Final value is selected via max() across signals to avoid underestimation
//
// Memory Utilization Strategy (more conservative than CPU):
//   - Stronger reaction to outliers (P99 influence is higher)
//   - Higher baseline floor (avg * 1.3)
//   - Additional safety buffer when utilization exceeds 85%
//   - Designed to minimize risk of OOM situations
//
// Peak Handling:
//   - Recurring high-load samples increase utilization via peakFactor()
//   - Sustained peaks are weighted higher than sporadic spikes
//
// Returns:
//   - cpuPercentile: Effective CPU utilization in percent
//   - memPercentile: Effective memory utilization in percent
//   - validSampleCount: Number of valid samples used in calculation
//
// Design Rationale:
// This multi-signal approach prioritizes safety over aggressive downsizing,
// while still allowing efficient resource reduction for stable workloads.
func (p *Processor) calculatePercentiles(metrics []mcmp.ServerMetrics) (cpuPercentile, memPercentile float64, validSampleCount int) {
	cpuValues := make([]float64, 0, len(metrics))
	memValues := make([]float64, 0, len(metrics))

	// Collect valid metric values
	for _, metric := range metrics {
		if metric.CPUUtil != nil && metric.MemUsedPercent != nil {
			cpuValues = append(cpuValues, *metric.CPUUtil)
			memValues = append(memValues, *metric.MemUsedPercent)
			validSampleCount++
		}
	}

	if validSampleCount == 0 {
		return 0, 0, 0
	}

	// Calculate percentiles
	// --- CPU ---
	cpuP90 := percentile(cpuValues, 90)
	cpuP95 := percentile(cpuValues, 95)
	cpuP99 := percentile(cpuValues, 99)
	cpuAvg := average(cpuValues)
	cpuStdDev := standardDeviation(cpuValues)

	// Detect strong outliers (P99 >> P95)
	if cpuP99 > cpuP95*1.3 {
		cpuP95 += (cpuP99 - cpuP95) * 0.3
	}

	// Apply peak factor (your existing logic)
	cpuPeak := p.peakFactor(cpuValues, p.config.CPUHighLoadThreshold)

	// Add extra safety for highly volatile workloads.
	// Stable workloads should be resized more aggressively,
	// while bursty workloads require additional headroom.
	cpuVarianceBonus := math.Min(cpuStdDev*0.1, 5.0)

	// Combine signals (weighted)
	cpuPercentile = max(
		cpuP95+cpuPeak*0.5+cpuVarianceBonus,
		cpuAvg*1.2,
		cpuP90+cpuPeak,
	)

	// --- Memory ---
	memP95 := percentile(memValues, 95)
	memP99 := percentile(memValues, 99)
	memAvg := average(memValues)
	memStdDev := standardDeviation(memValues)

	// Stronger reaction to outliers
	if memP99 > memP95*1.2 {
		memP95 += (memP99 - memP95) * 0.5
	}

	memPeak := p.peakFactor(memValues, p.config.MemoryHighLoadThreshold)

	// Memory volatility is treated more conservatively,
	// because unstable memory usage increases OOM risk.
	memVarianceBonus := math.Min(memStdDev*0.05, 3.0)

	// Memory-specific conservative adjustment
	memPercentile = max(
		memP95+memPeak*0.3+memVarianceBonus,
		memAvg*1.3,
	)

	return cpuPercentile, memPercentile, validSampleCount
}

// calculateRecommendedCPU calculates recommended CPU cores based on utilization.
//
// This method computes the optimal CPU allocation for a server based on its
// historical usage patterns and configured safety margins.
//
// Calculation Steps:
// 1. Calculate used CPU: currentCPU * (cpuUtilPercent / 100)
// 2. Apply safety margin: usedCPU * CPUMarginFactor
// 3. Round up to nearest integer
// 4. Ensure minimum of 1 core
//
// Example Calculation:
// - Current: 8 cores
// - P95 utilization: 40%
// - Safety margin: 1.15 (15%)
// - Used CPU: 8 * 0.40 = 3.2 cores
// - Recommended: 3.2 * 1.15 = 3.68 cores
// - Rounded: 4 cores
//
// Parameters:
//   - currentCPU: Current CPU core allocation
//   - cpuUtilPercent: Percentile utilization in percent (0-100).
//     Typically a P95 value, but could be any percentile.
//
// Returns:
//   - int: Recommended number of CPU cores (minimum 1)
//
// Edge Cases:
// - If cpuUtilPercent <= 0, returns currentCPU unchanged
// - Very small utilization (< 1% of 1 core) still results in 1 core minimum
// - Rounding uses standard banker's rounding (round up on 0.5)
//
// Safety Margin Rationale:
// The safety margin prevents recommendations that are too aggressive. It ensures:
// - Headroom for temporary usage spikes beyond the percentile
// - Buffer for other workloads on the system
// - Prevents thrashing between resize operations
//
// Note:
// cpuUtilPercent is not a raw percentile anymore.
// It represents an "effective utilization" derived from multiple signals
// (P95, P99, average, peak adjustments).
//
// Example:
//
//	// Server with 8 cores, P95 CPU util = 40%
//	recommended := processor.calculateRecommendedCPU(8, 40.0)
//	// Returns 4 (rounds from 3.68)
func (p *Processor) calculateRecommendedCPU(vmName string, currentCPU int, cpuUtilPercent float64) int {
	if cpuUtilPercent <= 0 {
		return currentCPU
	}

	// Calculate required CPU based on actual usage
	recommendedCPU := float64(currentCPU) * (cpuUtilPercent / 100.0)

	// Apply safety margin if reducing the CPU (similar to Kubernetes VPA)
	if recommendedCPU < float64(currentCPU) {
		recommendedCPU = recommendedCPU * p.config.CPUMarginFactor
	}

	// Round up to nearest integer, minimum 1 CPU
	result := int(recommendedCPU + 0.5)

	// dp-server: special CPU floor rules
	if reDP.MatchString(vmName) {
		if currentCPU <= 4 {
			// Already at or below minimum — never downsize
			result = currentCPU
		} else {
			// Above minimum — don't go below 4 cores
			result = max(result, 4)
		}
	} else {
		// All other servers: never go below 2 cores
		result = max(result, 2)
	}

	// Never recommend less than half the current allocation, regardless of server class.
	if float64(result) < float64(currentCPU)/2 {
		result = int(math.Round(float64(currentCPU) / 2))
	}

	return result
}

// calculateRecommendedMemory calculates recommended memory in MB based on utilization.
//
// This method computes the optimal memory allocation for a server based on its
// historical usage patterns, with rounding to practical memory increments.
//
// Calculation Steps:
// 1. Calculate used memory: currentMemoryMB * (memUtilPercent / 100)
// 2. Apply safety margin: usedMemory * MemoryMarginFactor
// 3. Round to nearest 1024MB increment (standard VM memory sizing)
//
// Example Calculation:
// - Current: 16,384 MB (16 GB)
// - P95 utilization: 50%
// - Safety margin: 1.15 (15%)
// - Used memory: 16384 * 0.50 = 8192 MB
// - Recommended: 8192 * 1.15 = 9420.8 MB
// - Rounded: (9420.8 + 128) / 1024 * 1024 = 10240 MB
//
// Parameters:
//   - currentMemoryMB: Current memory allocation in MB
//   - memUtilPercent: Percentile memory utilization in percent (0-100).
//     Typically a P95 value, but could be any percentile.
//
// Returns:
//   - int: Recommended memory in MB, rounded to 1024MB increments
//
// Edge Cases:
// - If memUtilPercent <= 0, returns currentMemoryMB unchanged
//
// Note:
// memUtilPercent is a conservatively adjusted utilization value,
// not a simple percentile. It includes outlier handling and safety buffers.
//
// Example:
//
//	// Server with 16384 MB, P95 memory util = 50%
//	recommended := processor.calculateRecommendedMemory(16384, 50.0)
//	// Returns 10240 MB (rounded from 9420.8 to nearest 1024MB)
func (p *Processor) calculateRecommendedMemory(vmName string, currentMemoryMB int, memUtilPercent float64) int {
	if memUtilPercent <= 0 {
		return currentMemoryMB
	}

	// Calculate required memory based on actual usage
	recommendedMemoryMB := float64(currentMemoryMB) * (memUtilPercent / 100.0)

	// Apply safety margin (similar to Kubernetes VPA)
	if recommendedMemoryMB < float64(currentMemoryMB) {
		recommendedMemoryMB = recommendedMemoryMB * p.config.MemoryMarginFactor
	}

	// Round to nearest 1024MB increment (common VM memory sizing)
	result := int((recommendedMemoryMB+512)/1024) * 1024

	if reDB.MatchString(vmName) && result < 6*1024 {
		result = 6 * 1024
	} else if result < 4*1024 {
		result = 4 * 1024
	}

	// Never recommend less than half the current allocation, regardless of server class.
	if float64(result) < float64(currentMemoryMB)/2 {
		result = int(math.Round(float64(currentMemoryMB)/2/1024)) * 1024
	}

	return result
}

// percentile calculates the specified percentile from a slice of values.
//
// This utility function implements percentile calculation using linear interpolation.
// It's used internally to compute specific percentiles (e.g., P95) from metric samples.
//
// Algorithm:
// 1. Creates a copy of input values and sorts them
// 2. Calculates the fractional index: (p / 100) * (n - 1)
// 3. Uses linear interpolation between surrounding values for fractional indices
// 4. Returns exact value for integer indices
//
// Interpolation Method (Linear):
// For index 94.05 in a 100-element sorted array:
// - Lower value: sorted[94]
// - Upper value: sorted[95]
// - Weight: 0.05
// - Result: sorted[94] * 0.95 + sorted[95] * 0.05
//
// This provides smooth percentile values without discontinuities.
//
// Parameters:
//   - values: Unsorted slice of float64 values to analyze
//   - p: Percentile to calculate, range 0-100
//   - p=0: returns minimum value
//   - p=50: returns median
//   - p=95: returns value exceeded 5% of the time
//   - p=100: returns maximum value
//
// Returns:
//   - float64: The calculated percentile value
//
// Edge Cases:
// - Empty slice returns 0.0
// - Single element returns that element for any percentile
// - Boundary values (p=0 and p=100) return min and max respectively
//
// Example:
//
//	values := []float64{10, 20, 30, 40, 50}
//	p50 := percentile(values, 50.0)  // Returns 30.0 (median)
//	p95 := percentile(values, 95.0)  // Returns 49.0
//	p100 := percentile(values, 100.0) // Returns 50.0 (max)
//
// Performance Notes:
// - Sorting complexity: O(n log n)
// - Interpolation: O(1)
// - Total: O(n log n), dominated by sorting
// - Consider caching results if called multiple times on the same dataset
func percentile(values []float64, p float64) float64 {
	if len(values) == 0 {
		return 0
	}

	sorted := make([]float64, len(values))
	copy(sorted, values)
	slices.Sort(sorted)

	index := (p / 100.0) * float64(len(sorted)-1)
	lower := int(index)
	upper := min(lower+1, len(sorted)-1)

	weight := index - float64(lower)
	return sorted[lower]*(1-weight) + sorted[upper]*weight
}

func average(values []float64) float64 {
	if len(values) == 0 {
		return 0
	}
	sum := 0.0
	for _, v := range values {
		sum += v
	}
	return sum / float64(len(values))
}

// applyDefault applies a default value to a configuration parameter if it's not already set.
//
// This utility function is used during processor initialization to ensure all
// configuration parameters have sensible values. A parameter is considered "not set"
// if its value is <= 0.
//
// This works with generic numeric types (int and float64) and modifies the value
// in-place through a pointer.
//
// Parameters:
//   - val: Pointer to a configuration value (int or float64).
//     Will be modified in-place if its current value is <= 0.
//   - fallback: The default value to apply if val is <= 0
//
// Type Constraints:
// Accepts only int and float64 types (enforced by generic constraints).
//
// Usage Pattern:
// Typically used during initialization to set defaults:
//
//	applyDefault(&config.WorkerCount, 5)
//
// Example:
//
//	var workers int = 0
//	applyDefault(&workers, 5)
//	// workers is now 5
//
//	var percentile float64 = 90.0
//	applyDefault(&percentile, 95.0)
//	// percentile remains 90.0 (unchanged, was already set)
func applyDefault[T int | float64](val *T, fallback T) {
	if *val <= 0 {
		*val = fallback
	}
}

func (p *Processor) normalizeAndFilterMetrics(server *mcmp.GreenItServer) []mcmp.ServerMetrics {
	normalized := make([]mcmp.ServerMetrics, 0, len(server.Metrics))

	for _, m := range server.Metrics {
		newMetric := m

		// CPU
		if m.CPUUtil != nil {
			util := *m.CPUUtil

			if server.NumCPUPrev != nil &&
				server.NumCPUChangeDate != nil &&
				m.CreatedAt != nil &&
				m.CreatedAt.Before(*server.NumCPUChangeDate) &&
				server.NumCPU > 0 {

				oldCPU := float64(*server.NumCPUPrev)
				newCPU := float64(server.NumCPU)

				if newCPU > 0 {
					util = util * (oldCPU / newCPU)
				}
			}

			if server.NumCPUChangeDatePrev == nil || !m.CreatedAt.Before(*server.NumCPUChangeDatePrev) {
				newMetric.CPUUtil = &util
			}
		}

		// Memory
		if m.MemUsedPercent != nil {
			util := *m.MemUsedPercent

			if server.MemoryMBPrev != nil &&
				server.MemoryMBChangeDate != nil &&
				m.CreatedAt != nil &&
				m.CreatedAt.Before(*server.MemoryMBChangeDate) &&
				server.MemoryMB > 0 {

				oldMem := float64(*server.MemoryMBPrev)
				newMem := float64(server.MemoryMB)

				if newMem > 0 {
					util = util * (oldMem / newMem)
				}
			}

			if server.MemoryMBChangeDatePrev == nil || !m.CreatedAt.Before(*server.MemoryMBChangeDatePrev) {
				newMetric.MemUsedPercent = &util
			}
		}

		normalized = append(normalized, newMetric)
	}

	return normalized
}

// peakFactor calculates an additive adjustment based on recurring high-load peaks.
//
// Instead of returning a multiplicative factor, this function returns an
// additive bonus in percentage points that is applied to the selected percentile.
//
// The algorithm evaluates multiple peak characteristics:
//   - Frequency: how often utilization exceeds the threshold
//   - Duration: how long sustained peak runs last
//   - Intensity: how far values exceed the threshold
//
// This produces a smoother and more adaptive adjustment than fixed threshold steps.
//
// Logic:
// 1. Count how many samples exceed the threshold
// 2. Measure the longest consecutive peak run
// 3. Measure how strongly peaks exceed the threshold
// 4. Derive a continuous bonus from:
//   - Peak frequency
//   - Sustained peak duration
//   - Peak intensity
//
// 5. Cap the result to avoid excessive over-allocation
//
// Important:
// This factor is additive (percentage points), not multiplicative.
// It intentionally reacts gradually instead of using hard step thresholds.
//
// Example:
//   - Rare/light peaks        -> +0% to +2%
//   - Frequent moderate peaks -> +3% to +6%
//   - Sustained heavy peaks   -> +7% to +10%
//
// Parameters:
//   - values: Utilization values in percent
//   - threshold: High-load threshold in percent
//
// Returns:
//   - float64: Additive bonus in percentage points
func (p *Processor) peakFactor(values []float64, threshold float64) float64 {
	if len(values) == 0 {
		return 0
	}

	totalHigh := 0
	longestRun := 0
	currentRun := 0

	// Sum of how much values exceed the threshold
	totalExcess := 0.0

	for _, v := range values {
		if v >= threshold {
			totalHigh++
			currentRun++

			// Measure intensity above threshold
			totalExcess += (v - threshold)

			if currentRun > longestRun {
				longestRun = currentRun
			}
		} else {
			currentRun = 0
		}
	}

	if totalHigh == 0 {
		return 0
	}

	// Ratio of peak samples
	highRate := float64(totalHigh) / float64(len(values))

	// Average amount by which peaks exceed the threshold
	avgExcess := totalExcess / float64(totalHigh)

	bonus := 0.0

	// Frequency contribution
	//
	// Example:
	//   5% peaks  -> +2
	//   10% peaks -> +4
	//   20% peaks -> +8
	bonus += highRate * 40

	// Sustained peak contribution
	//
	// Longer continuous peak runs indicate persistent load
	// and therefore require additional headroom.
	bonus += math.Min(float64(longestRun), 10) * 0.5

	// Intensity contribution
	//
	// Peaks barely above threshold should have little impact,
	// while extreme peaks should increase the recommendation more.
	bonus += avgExcess * 0.3

	// Normalize bonus for small sample counts.
	//
	// A few isolated peaks in a very small dataset should not
	// have the same impact as recurring peaks in large datasets.
	sampleFactor := math.Min(float64(totalHigh)/5.0, 1.0)

	bonus *= sampleFactor

	// Cap to prevent excessive adjustments
	if bonus > 10 {
		bonus = 10
	}

	return bonus
}

func standardDeviation(values []float64) float64 {
	if len(values) == 0 {
		return 0
	}

	avg := average(values)

	var variance float64

	for _, v := range values {
		diff := v - avg
		variance += diff * diff
	}

	variance /= float64(len(values))

	return math.Sqrt(variance)
}
