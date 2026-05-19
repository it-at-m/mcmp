<div id="top"></div>

## About The Project

**mcmp-eai-checkmk** is an Enterprise Application Integration (EAI) service that synchronizes Checkmk monitoring data with the Munich Cloud Management Platform (MCMP). The application automatically retrieves performance metrics including CPU utilization and memory usage from Checkmk systems, then transforms and forwards this data to MCMP through secure API calls.

### Key Features

- **Checkmk Integration**: Retrieves comprehensive performance data from multiple Checkmk systems
- **Multi-Source Support**: Configurable connections to multiple Checkmk instances simultaneously
- **MCMP API Integration**: Securely transmits processed data to MCMP endpoints via OAuth2
- **Data Aggregation**: Combines CPU and memory metrics per host
- **Comprehensive Logging**: Configurable log levels with file rotation support
- **Data Export**: Creates local JSON backups of synchronized data
- **Graceful Shutdown**: Signal handling for SIGINT and SIGTERM
- **Single-Instance Lock**: Prevents multiple concurrent instances using file-based locking
- **Concurrent Processing**: Parallel data fetching and processing across multiple sources

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with connection parameters
2. **Lock Acquisition**: Ensures only one instance is running (optional, configurable)
3. **Checkmk Data Retrieval**: Fetches performance data for CPU utilization and memory metrics
4. **Data Aggregation**: Combines metrics and organizes them by host
5. **Data Transmission**: Sends processed data to MCMP API endpoint via OAuth2
6. **Local Backup**: Exports data to local JSON file for audit and debugging

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### Logging Settings
- **Level**: Log level (DEBUG, INFO, WARN, ERROR)
- **Output**: Output destination (console, file, both)
- **Format**: Output format (text, json, plain)
- **Filename**: Log file path (required for file/both output)
- **MaxSize**: Maximum file size in MB before rotation (default: 100)
- **MaxBackups**: Number of old log files to retain (default: 3)
- **MaxAge**: Maximum age in days for log files (default: 28)
- **Compress**: Compress rotated log files with gzip (default: false)

#### Checkmk Configuration (multiple sources supported)
- **Hostname**: Checkmk instance hostname or IP address
- **Username**: Authentication username
- **Password**: Authentication password
- **Enabled**: Enable/disable individual sources
- **VerifyTLS**: TLS certificate verification toggle

#### MCMP API Configuration
- **OAuthUrl**: Keycloak authentication server URL
- **OAuthRealm**: Authentication realm
- **OAuthClientId**: OAuth2 client ID
- **OAuthClientSecret**: OAuth2 client secret
- **ApiEndpoint**: MCMP API endpoint for data submission
- **RequestTimeoutSeconds**: HTTP request timeout in seconds (default: 30)
- **ConnectTimeoutSeconds**: Connection establishment timeout in seconds (default: 60)
- **ReadTimeoutSeconds**: Response header read timeout in seconds (default: 60)
- **MaxRetries**: Maximum number of retries (default: 3)
- **RetryDelaySeconds**: Delay between retries in seconds (default: 2)
- **MaxIdleConns**: Maximum idle connections (default: 100)
- **IdleConnTimeoutSeconds**: Idle connection timeout in seconds (default: 90)

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.26+ - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **OAuth2** - Secure API authentication via Keycloak
- **Structured Logging** - Production-grade logging with slog
- **File-based Locking** - Single-instance enforcement with graceful stale lock cleanup

## Documentation

### Requirements

- [Go](https://go.dev/) >= 1.26
- Network connectivity to Checkmk REST API (port 443)
- Network connectivity to MCMP API endpoints
- OAuth2 client credentials for MCMP authentication
- Optional: Keycloak instance for OAuth2 token management

### Installation

#### 1. Clone the Repository
```bash
git clone github.com/it-at-m/mcmp.git
cd mcmp-eai-checkmk
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-checkmk` (or `mcmp-eai-checkmk.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-checkmk.toml.example mcmp-eai-checkmk.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-checkmk.toml
```

Configure the following sections:
- **LOGGING**: Set log level, output destination, rotation settings, and log file path
- **CHECKMK**: Checkmk system connection details (multiple sources supported)
- **MCMP**: MCMP API endpoint, OAuth2 credentials, and connection timeouts

#### Configuration Example

```toml
[LOGGING]
Level      = "INFO"
Output     = "file"
Format     = "plain"
Filename   = "/var/log/mcmp-eai-checkmk/mcmp-eai-checkmk.log"
MaxSize    = 10
MaxBackups = 7
MaxAge     = 30
Compress   = true

[[CHECKMK]]
Hostname  = "checkmk1.example.com"
Username  = "your-username"
Password  = "your-password"
Enabled   = true
VerifyTLS = true

[MCMP]
OAuthUrl          = "https://keycloak.example.com/auth"
OAuthRealm        = "mcmp"
OAuthClientId     = "your-client-id"
OAuthClientSecret = "your-client-secret"
ApiEndpoint       = "https://mcmp-api.example.com/api/checkmk/insert"
```

### Execution

#### Standard Operation
```bash
./mcmp-eai-checkmk
```

This command executes the complete data synchronization workflow:
1. Loads configuration from `mcmp-eai-checkmk.toml`
2. Acquires single-instance lock (prevents concurrent execution)
3. Connects to configured Checkmk systems
4. Retrieves and aggregates performance data
5. Authenticates with Keycloak and sends data to MCMP API
6. Creates local backup file (`checkmk_export_<hostname>.json`)
7. Releases lock on completion or error

#### Debug Mode
Enable debug logging by setting `Level = "DEBUG"` in the `[LOGGING]` section of the configuration file. This will output detailed information about API calls, retries, and data processing.

#### Graceful Shutdown
The application responds to SIGINT (Ctrl+C) and SIGTERM signals, ensuring proper cleanup and lock release before termination.

### Output Files

- **checkmk_export_\<hostname\>.json**: Local backup of synchronized data per Checkmk source
- **Application logs**: Configurable log output to console and/or file with rotation support
- **.pid file**: Lock file at `/var/run/user/<uid>/<appname>.pid` (Linux) or system temp directory (other OS)

### Error Handling

The application implements robust error handling with the following features:

- **Retry Logic**: Automatic retries with exponential backoff for transient network errors
- **Timeout Handling**: Configurable timeouts for connection, request, and read operations
- **Lock Management**: Automatic cleanup of stale lock files from crashed instances
- **Graceful Degradation**: Continues processing remaining sources even if individual sources fail
- **Detailed Logging**: Comprehensive error logging with context information

## Architecture

The application is structured with clear separation of concerns following the common EAI patterns:

```
mcmp-eai-checkmk/
├── pkg/
│   ├── client/
│   │   ├── checkmk/         # Checkmk REST API client
│   │   └── source/          # Data source implementation
│   └── processor/           # Data aggregation and processing logic
├── main.go                  # Application entry point
├── go.mod                   # Go module definition
└── mcmp-eai-checkmk.toml.example  # Configuration template

mcmp-eai-common/             # Shared utilities and base classes
├── pkg/
│   ├── app/                 # Generic EAI runner and lifecycle management
│   ├── client/
│   │   ├── httpclient/      # Reusable HTTP client with retry logic
│   │   ├── mcmp/            # MCMP API client with OAuth2 support
│   │   └── webex/           # Optional notification client
│   ├── config/              # Configuration loading utilities
│   ├── datasource/          # Generic data source abstraction
│   ├── lock/                # Single-instance file-based locking
│   └── logging/             # Structured logging implementation
└── go.mod
```

### Key Components

- **Checkmk Client**: Handles REST API communication with Checkmk systems, including performance data queries
- **Processor**: Aggregates performance metrics by host (CPU and memory utilization)
- **Data Source**: Implements the common DataSource interface for the EAI runner
- **MCMP Client**: Handles OAuth2 authentication and API communication with MCMP backend
- **Logger**: Structured logging with configurable levels, formats, and file rotation
- **Locker**: Ensures single-instance execution with automatic stale lock detection
- **EAI Runner**: Generic orchestrator from mcmp-eai-common that manages data source lifecycle

### Concurrency Model

The application processes multiple Checkmk sources concurrently:

1. **Parallel Execution**: Each enabled Checkmk source is processed in its own goroutine
2. **Synchronization**: WaitGroup ensures all sources complete before shutdown
3. **Error Aggregation**: All errors from parallel operations are collected and returned
4. **Resource Limits**: HTTP client connection pooling prevents resource exhaustion

## Data Model

### Aggregated Performance Data Structure

The application aggregates the following metrics per host:

```
CheckmkAggregatedData
├── Hosts (map[string]HostMetrics)
│   ├── HostName → HostMetrics
│   │   ├── CPUUtil (float64)         # CPU utilization as percentage
│   │   └── MemUsedPercent (float64)  # Memory used as percentage
```

### Output JSON Format Example

```json
{
  "hosts": {
    "server1.example.com": {
      "cpu_util": 45.5,
      "mem_used_percent": 68.2
    },
    "server2.example.com": {
      "cpu_util": 32.1,
      "mem_used_percent": 55.8
    },
    "db-server.example.com": {
      "cpu_util": 78.9,
      "mem_used_percent": 92.3
    }
  }
}
```

### API Query Details

The application queries Checkmk's service collection API with the following filters:

- **Endpoint**: `/lhmmon/check_mk/api/1.0/domain-types/service/collections/all`
- **Columns**: `host_name`, `performance_data`
- **Filter**: Services with descriptions matching "Memory" or "CPU utilization"
- **Query Pattern**: Regular expression matching on service descriptions

## Logging Details

### Log Levels

- **DEBUG**: Detailed information including API calls, retries, and data processing steps
- **INFO**: General informational messages about program flow and data synchronization
- **WARN**: Warning messages for non-critical issues
- **ERROR**: Error messages for failures that require attention

### Log Formats

- **plain**: Human-readable format with timestamp, level, and key=value attributes
- **text**: Go slog text format with structured attributes
- **json**: Machine-parseable JSON format for log aggregation systems

### Log Rotation

Logs can be automatically rotated based on:
- **File size**: When MaxSize (MB) is exceeded
- **Age**: Files older than MaxAge (days) are removed
- **Backup count**: Only MaxBackups old files are retained
- **Compression**: Old files can be compressed with gzip

## Contributing

Contributions are welcome and help improve the integration capabilities. To contribute:

1. **Report Issues**: Open an issue with the "enhancement" tag
2. **Fork Repository**: Create your own fork of the project
3. **Create Feature Branch**: `git checkout -b feature/YourFeature`
4. **Commit Changes**: `git commit -m 'Add YourFeature'`
5. **Push Branch**: `git push origin feature/YourFeature`
6. **Open Pull Request**: Submit your changes for review

Please ensure your code follows Go best practices and includes appropriate documentation.

## Troubleshooting

### "Another instance is already running"
If you see this error, check for stale lock files:
```bash
# Linux
ls -la /var/run/user/$(id -u)/mcmp-eai-checkmk.pid

# Other OS
ls -la /tmp/mcmp-eai-checkmk.pid
```

If the file exists but the process is not running, it's safe to delete it manually.

### Timeout Issues
Adjust timeout settings in the `[MCMP]` section:
- Increase `ConnectTimeoutSeconds` for slow network connections
- Increase `ReadTimeoutSeconds` for high-latency networks
- Increase `RequestTimeoutSeconds` for slow API responses

### Authentication Failures
Verify OAuth2 configuration:
1. Check `OAuthUrl` and `OAuthRealm` match your Keycloak instance
2. Verify `OAuthClientId` and `OAuthClientSecret` are correct
3. Ensure the OAuth2 client has appropriate scopes configured
4. Check network connectivity to the Keycloak server


## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-checkmk](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-checkmk)

<p align="right">(<a href="#top">back to top</a>)</p>