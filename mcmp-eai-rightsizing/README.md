<div id="top"></div>

## About The Project

**mcmp-eai-rightsizing** is an Enterprise Application Integration (EAI) service that analyzes server resource utilization metrics and generates intelligent rightsizing recommendations for the Munich Cloud Management Platform (MCMP). The application implements a Kubernetes VPA-inspired algorithm to automatically calculate optimal CPU and memory allocations based on historical performance data, helping organizations optimize infrastructure costs while maintaining service performance.

### Key Features

- **Intelligent Rightsizing**: VPA-inspired algorithm using percentile-based resource calculations
- **Parallel Processing**: Concurrent analysis of multiple servers using a configurable worker pool
- **Statistical Analysis**: P95 percentile-based recommendations with configurable safety margins
- **MCMP API Integration**: Seamless integration with MCMP backend for metrics retrieval and recommendation submission
- **Metrics Validation**: Minimum sample size requirements ensure statistical reliability
- **Comprehensive Logging**: Configurable log levels with file rotation support
- **Data Export**: Creates local JSON backups of generated recommendations

### Data Analysis Flow

The application follows this rightsizing analysis workflow:

1. **Configuration Loading**: Reads TOML configuration file with API and processor parameters
2. **Server Discovery**: Fetches complete list of server IDs from MCMP API
3. **Metrics Retrieval**: Parallel fetching of historical CPU and memory utilization metrics for each server
4. **Statistical Analysis**: Calculates P95 percentiles for utilization with configured safety margins
5. **Recommendation Generation**: Computes optimal CPU and memory allocations
6. **Results Submission**: Sends recommendations back to MCMP API for persistence and change management
7. **Local Backup**: Exports recommendations to local JSON file for audit and debugging

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### Logging Settings
- **Level**: Log level (DEBUG, INFO, WARN, ERROR)
- **Output**: Output destination (console, file, both)
- **Format**: Output format (text, json, plain)
- **File Rotation**: Configurable size, backups, and age limits

#### MCMP API Configuration
- **OAuthUrl**: Keycloak authentication server URL
- **OAuthRealm**: Authentication realm
- **OAuthClientId**: OAuth2 client ID
- **OAuthClientSecret**: OAuth2 client secret
- **RightsizingEndpoint**: MCMP API endpoint for submitting recommendations
- **ServerListEndpoint**: MCMP API endpoint for fetching server IDs
- **ServerMetricEndpoint**: MCMP API endpoint for fetching server metrics (supports %d for server ID)
- **Request Timeouts**: Configurable timeout values for API calls

#### Processor Configuration
- **WorkerCount**: Number of parallel workers for concurrent server analysis (default: 5)
- **CPUMarginFactor**: Safety margin multiplier for CPU allocations (default: 1.15 = 15%)
- **MemoryMarginFactor**: Safety margin multiplier for memory allocations (default: 1.15 = 15%)
- **MinSampleSize**: Minimum number of metric samples required for reliable recommendations (default: 60)
- **CPUHighLoadThreshold**: Threshold above which CPU samples are counted as peak
- **MemoryHighLoadThreshold**: Threshold above which memory samples are counted as peaks

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.26+ - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **errgroup** - Concurrent goroutine management with error handling
- **OAuth2** - Secure API authentication
- **Statistical Analysis** - Percentile-based calculations for rightsizing recommendations

## Documentation

### Requirements

- [Go](https://go.dev/) >= 1.26
- Network connectivity to MCMP API endpoints
- OAuth2 client credentials for MCMP authentication
- Sufficient metrics history in MCMP (minimum sample size as configured, typically 60 samples)

### Installation

#### 1. Clone the Repository
```bash
git clone https://github.com/it-at-m/mcmp.git
cd mcmp-eai-rightsizing
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-rightsizing` (or `mcmp-eai-rightsizing.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-rightsizing.toml.example mcmp-eai-rightsizing.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-rightsizing.toml
```

Configure the following sections:
- **LOGGING**: Set log level, output destination, and rotation settings
- **MCMP**: MCMP API endpoints and OAuth2 configuration
    - Ensure all three endpoints are configured: RightsizingEndpoint, ServerListEndpoint, ServerMetricEndpoint
    - Set appropriate request timeouts (60+ seconds recommended for metric retrieval)
- **PROCESSOR**: Configure rightsizing algorithm parameters
    - Adjust WorkerCount based on your system resources and API rate limits
    - Set percentile thresholds based on your availability requirements
    - Configure safety margins to balance cost optimization with performance headroom
    - Set MinSampleSize based on your metrics history (60 samples ≈ 1 hour at 1-minute intervals)

### Execution

#### Standard Operation
```bash
./mcmp-eai-rightsizing
```

This command executes the complete rightsizing analysis workflow:
1. Loads configuration from `mcmp-eai-rightsizing.toml`
2. Authenticates with Keycloak using OAuth2 credentials
3. Fetches list of servers from MCMP API
4. Retrieves historical metrics for each server in parallel
5. Analyzes metrics using statistical percentile calculations
6. Generates rightsizing recommendations with safety margins
7. Submits recommendations to MCMP API for change management
8. Creates local backup file (`rightsizing_export_mcmp.json`)
9. Logs completion status and statistics

#### Debug Mode
Enable debug logging by setting `Level = "DEBUG"` in the `[LOGGING]` section of the configuration file.

### Output Files

- **rightsizing_export_mcmp.json**: Local backup of generated recommendations per run
- **Application logs**: Configurable log output to console and/or file

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── client/
│   ├── mcmp/
│   │   ├── client.go       # MCMP API client with OAuth2
│   │   ├── config.go       # Configuration validation
│   │   └── types.go        # Data structures (GreenItServer, Rightsizing, etc.)
│   └── source/
│       └── source.go       # Data source abstraction for EAI framework
└── processor/
    └── processor.go        # Rightsizing algorithm implementation
```

### Key Components

- **MCMP Client**: Handles OAuth2 authentication and REST API communication with MCMP backend
- **Processor**: Implements VPA-inspired statistical algorithm for rightsizing calculations
- **Source**: Implements the DataSource interface for integration with the EAI runner framework
- **EAI Runner**: Generic orchestrator from mcmp-eai-common for managing the analysis pipeline

## Data Model

### Server Metrics Structure

The application processes server metrics with the following structure:

```go
GreenItServer {
  ID              int64              // Unique server identifier
  VMName          string             // Virtual machine name
  FQDN            string             // Fully qualified domain name
  NumCPU          int                // Currently allocated CPU cores
  MemoryMB        int                // Currently allocated memory in MB
  PowerState      string             // Current power state
  Metrics[]       {                  // Historical utilization samples
    CreatedAt       time.Time        // Sample timestamp
    CPUUtil         float64          // CPU utilization percentage (0-100)
    MemUsedPercent  float64          // Memory utilization percentage (0-100)
  }
}
```

### Rightsizing Recommendations Structure

The processor generates recommendations with the following structure:

```go
Rightsizing {
  Servers[] {
    ID                  int64   // Server ID from MCMP
    NumCPU              int     // Recommended CPU cores
    MemoryMB            int     // Recommended memory in MB
  }
}
```

### Output JSON Format Example

```json
{
  "servers": [
    {
      "id": 12345,
      "num_cpu": 4,
      "memory_mb": 8192,
      "change_rightsizing": true,
      "change_shutdown": false
    },
    {
      "id": 12346,
      "num_cpu": 8,
      "memory_mb": 16384,
      "change_rightsizing": false,
      "change_shutdown": false
    }
  ]
}
```

## Algorithm Details

### Rightsizing Calculation

The processor implements a VPA-inspired statistical algorithm:

1. **Data Collection**: Gathers historical CPU and memory utilization metrics
2. **Validation**: Ensures minimum sample size (configurable, default 60 samples)
3. **Percentile Calculation**: Computes configured percentile (default P95) for both metrics
4. **Resource Calculation**:
    - `RecommendedCPU = CurrentCPU × (PercentileUtilization / 100) × CPUMarginFactor`
    - `RecommendedMemory = CurrentMemory × (PercentileUtilization / 100) × MemoryMarginFactor`
5. **Rounding**:
    - CPU: Rounded to nearest integer core (minimum 1)
    - Memory: Rounded to nearest 256MB increment (minimum 256MB)
6. **Change Detection**: Flags server for rightsizing if recommendations differ from current allocation

### Example Calculation

```
Server Configuration:
- Current CPU:     8 cores
- Current Memory:  16384 MB
- P95 CPU Util:    40%
- P95 Memory Util: 50%
- CPU Margin:      1.15 (15%)
- Memory Margin:   1.15 (15%)

Calculation:
- Recommended CPU = 8 × (40/100) × 1.15 = 3.68 → 4 cores
- Recommended Mem = 16384 × (50/100) × 1.15 = 9420.8 → 9472 MB

Result:
- Change Rightsizing: true
- From: 8 cores / 16384 MB
- To:   4 cores / 9472 MB
- Cost Savings: ~50% CPU, ~42% Memory
```

## Performance Characteristics

- **Scalability**: Tested with ~6000 servers in 10 minutes
- **Parallelism**: Configurable worker pool for optimal throughput
- **API Efficiency**: Batch operations where possible, individual metric requests as needed
- **Timeout**: Global 10-minute operation timeout provides failsafe mechanism
- **Memory**: Efficient streaming of results without loading entire datasets

## Contributing

Contributions are welcome and help improve the rightsizing capabilities. To contribute:

1. **Report Issues**: Open an issue with detailed description of the problem
2. **Fork Repository**: Create your own fork of the project
3. **Create Feature Branch**: `git checkout -b feature/YourFeature`
4. **Commit Changes**: `git commit -m 'Add YourFeature'`
5. **Push Branch**: `git push origin feature/YourFeature`
6. **Open Pull Request**: Submit your changes for review

Please ensure your code follows Go best practices, includes appropriate documentation, and passes all linting checks.

## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [github.com/it-at-m/mcmp/mcmp-eai-patchnight](github.com/it-at-m/mcmp/mcmp-eai-patchnight)

<p align="right">(<a href="#top">back to top</a>)</p>