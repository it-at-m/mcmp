<div id="top"></div>

## About The Project

**mcmp-eai-olvm** is an Enterprise Application Integration (EAI) service that synchronizes oVirt/OLVM (Oracle Linux Virtualization Manager) infrastructure data with the Munich Cloud Management Platform (MCMP). The application automatically retrieves virtual machines, hosts, and cluster information from OLVM instances, then transforms and forwards this data to MCMP through secure API calls.

### Key Features

- **OLVM Data Integration**: Processes virtual machines, hosts, and cluster configurations
- **Multi-Instance Support**: Connects to multiple OLVM instances simultaneously
- **MCMP API Integration**: Securely transmits processed data to multiple MCMP endpoints
- **OAuth2 Authentication**: Supports both password grant and client credentials flows
- **Data Processing**: Transforms OLVM API responses into MCMP-compatible server structures
- **Comprehensive Logging**: Configurable structured logging with rotation support
- **Data Export**: Creates local JSON backups of synchronized data
- **Single-Instance Lock**: Prevents concurrent executions to avoid data conflicts

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with connection parameters
2. **OLVM Data Retrieval**: Fetches VMs, hosts, and clusters from each configured OLVM instance
3. **Data Processing**: Transforms and structures data for MCMP compatibility
4. **Data Transmission**: Sends processed data to configured MCMP API endpoints
5. **Local Backup**: Exports data to local JSON file for audit and debugging

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### Logging Configuration
- **Level**: Log level (DEBUG, INFO, WARN, ERROR)
- **Output**: Output destination (console, file, both)
- **Format**: Output format (text, json, plain)
- **File Rotation**: MaxSize, MaxBackups, MaxAge, Compress

#### OLVM Configuration (Multiple Instances Supported)
- **Hostname**: OLVM server hostname
- **Enabled**: Enable/disable specific instance
- **OAuth Authentication**: Username, password, grant type, scope
- Connection parameters and timeouts

#### MCMP API Configuration (Multiple Endpoints Supported)
- **ApiEndpoint**: MCMP API endpoint for data submission
- **OAuth Authentication**: Client credentials or password flow
- Realm, client ID, and client secret

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.26.1 - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **golang.org/x/oauth2** - OAuth2 client implementation
- **Structured Logging** - JSON/plain text logging with rotation support

## Documentation

### Requirements

- [Go](https://go.dev/) >= v1.26.1
- Access to oVirt/OLVM instances with API access
- Network connectivity to MCMP API endpoints
- Valid OAuth2 credentials for OLVM and MCMP

### Installation

#### 1. Clone the Repository
```bash
git clone github.com/it-at-m/mcmp.git
cd webanwendung/mcmp-eai-olvm
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-olvm` (or `mcmp-eai-olvm.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-olvm.toml.example mcmp-eai-olvm.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-olvm.toml
```

Configure the following sections:
- **LOGGING**: Set log level, output, format, and rotation settings
- **OLVM**: Configure one or more OLVM instances with authentication
- **MCMP**: Configure one or more MCMP API endpoints

Example configuration:
```toml
[LOGGING]
Level      = "INFO"
Output     = "file"
Format     = "plain"
Filename   = "/var/log/mcmp-eai-olvm.log"
MaxSize    = 10
MaxBackups = 7
MaxAge     = 30
Compress   = true

[[OLVM]]
Hostname          = 'olvm1.example.com'
Enabled           = true
OAuthUrl          = 'https://olvm1.example.org/ovirt-engine/sso'
OAuthUsername     = 'olvm_user'
OAuthPassword     = '*****'
OAuthGrantType    = 'password'
OAuthScope        = 'ovirt-app-api'

[[MCMP]]
OAuthUrl          = 'http://kubernetes.example.com:8100/auth'
OAuthRealm        = 'local_realm'
OAuthClientId     = 'local'
OAuthClientSecret = 'client_secret'
ApiEndpoint       = 'http://mcmp.example.org/clients/eai-backend-service/olvm/import'
```

### Execution

#### Standard Operation
```bash
./mcmp-eai-olvm
```

This command executes the complete data synchronization workflow:
1. Loads configuration from `mcmp-eai-olvm.toml` (or `$HOME/.mcmp-eai-olvm/mcmp-eai-olvm.toml`)
2. Acquires a file-based lock to prevent concurrent executions
3. Connects to configured OLVM instances and retrieves VMs, hosts, and clusters
4. Processes and transforms the data into MCMP-compatible format
5. Sends data to configured MCMP API endpoints
6. Creates local backup files (e.g., `olvm_export_olvm1.example.com.json`)

#### Debug Mode
Enable debug logging by setting `Level = "DEBUG"` in the LOGGING section:
```toml
[LOGGING]
Level = "DEBUG"
```

### Output Files

- **olvm_export_<hostname>.json**: Local backup of synchronized data per OLVM instance
- **Application logs**: Structured logs with configurable format and rotation

## Architecture

The application is structured with clear separation of concerns:

```
mcmp-eai-olvm/
├── pkg/
│   ├── client/
│   │   ├── olvm/      # OLVM API client with OAuth2
│   │   └── source/    # Data source implementation
│   └── processor/     # Data processing and transformation
├── main.go            # Application entry point
└── mcmp-eai-olvm.toml.example

mcmp-eai-common/
└── pkg/
    ├── app/           # Generic EAI runner framework
    ├── client/
    │   ├── mcmp/      # MCMP API client
    │   └── oauth2client/ # Universal OAuth2 client
    ├── config/        # Configuration loading utilities
    ├── datasource/    # Generic data source interface
    ├── lock/          # Single-instance file lock
    └── logging/       # Structured logging with rotation
```

### Key Components

- **OLVM Client**: Handles OAuth2 authentication and API communication with OLVM instances
- **MCMP Client**: Sends processed data to MCMP endpoints with OAuth2 authentication
- **Processor**: Orchestrates data retrieval, transformation, and aggregation
- **Data Source**: Implements the generic datasource interface for OLVM
- **Configuration Manager**: Handles TOML configuration parsing with Viper
- **Lock Manager**: Prevents concurrent executions using file-based locking

## Data Transformation

The application transforms OLVM API responses into MCMP-compatible server structures:

### Input: OLVM API Responses

#### 1. Virtual Machines API Response
```json
{
  "vm": [
    {
      "id": "vm-uuid-123",
      "name": "webserver01",
      "status": "up",
      "memory": "8589934592",
      "cpu": {
        "topology": {
          "sockets": "2",
          "cores": "4",
          "threads": "1"
        }
      },
      "cluster": {
        "id": "cluster-uuid-abc"
      },
      "host": {
        "id": "host-uuid-xyz"
      },
      "guest_operating_system": {
        "distribution": "Red Hat Enterprise Linux",
        "version": {
          "full_version": "8.6"
        }
      },
      "start_time": 1710000000000
    }
  ]
}
```

#### 2. Hosts API Response
```json
{
  "host": [
    {
      "id": "host-uuid-xyz",
      "name": "hypervisor01.example.com"
    }
  ]
}
```

#### 3. Clusters API Response
```json
{
  "cluster": [
    {
      "id": "cluster-uuid-abc",
      "name": "Production-Cluster"
    }
  ]
}
```

### Transformation Process

```
OLVM VMs      ──┐
                │
OLVM Hosts    ──┼──► [Data Processor] ──► MCMP Cloud JSON
                │
OLVM Clusters ──┘
```

### Output: MCMP Cloud JSON Format

```json
{
  "cloud": "olvm1.example.com",
  "cloud_type": "OLVM",
  "servers": [
    {
      "server_kind": "VIRTUAL",
      "server_type": "OLVM",
      "name": "webserver01",
      "uuid": "vm-uuid-123",
      "vm_id": "vm-uuid-123",
      "instance_uuid": "vm-uuid-123",
      "cluster": "Production-Cluster",
      "host": "hypervisor01.example.com",
      "power_state": "up",
      "memory_mb": 8192,
      "num_cpu": 2,
      "num_cores_per_socket": 4,
      "num_of_threads": 1,
      "overall_status": "up",
      "guest_config_full_name": "Red Hat Enterprise Linux 8.6",
      "boot_time": "2024-03-09T12:00:00Z"
    }
  ]
}
```

### Transformation Logic

The processor performs the following transformations:

1. **ID Resolution**: Maps cluster and host IDs to human-readable names
2. **Memory Conversion**: Converts bytes to mebibytes (MiB)
3. **CPU Topology**: Parses socket, core, and thread counts
4. **Guest OS**: Combines distribution and version information
5. **Timestamp Conversion**: Converts Unix milliseconds to RFC 3339 format
6. **Server Classification**: Sets server_kind to "VIRTUAL" and server_type to "OLVM"

## Multi-Instance and Multi-Endpoint Support

The application supports connecting to multiple OLVM instances and sending data to multiple MCMP endpoints:

```toml
# Multiple OLVM instances
[[OLVM]]
Hostname = 'olvm1.example.com'
Enabled = true
# ... configuration ...

[[OLVM]]
Hostname = 'olvm2.example.com'
Enabled = false  # Can be disabled without removing config
# ... configuration ...

# Multiple MCMP endpoints
[[MCMP]]
ApiEndpoint = 'https://mcmp-prod.example.com/api/v1/olvm'
# ... configuration ...

[[MCMP]]
ApiEndpoint = 'https://mcmp-test.example.com/api/v1/olvm'
# ... configuration ...
```

Each OLVM instance will send its data to **all configured MCMP endpoints**, creating redundancy and supporting multi-environment deployments.

## OAuth2 Authentication

The application supports two OAuth2 flows:

### Password Grant Flow (for OLVM)
```toml
[[OLVM]]
OAuthGrantType    = 'password'
OAuthUsername     = 'admin@internal'
OAuthPassword     = 'secret'
OAuthScope        = 'ovirt-app-api'
```

### Client Credentials Flow (for MCMP)
```toml
[[MCMP]]
OAuthGrantType    = 'client_credentials'  # or omit, this is default
OAuthClientId     = 'eai-client'
OAuthClientSecret = 'client-secret'
```

## Error Handling and Retry Logic

The application includes robust error handling:

- **Connection Retries**: Automatic retry with exponential backoff for transient errors
- **HTTP Status Handling**: Retries for 408, 429, and 5xx status codes
- **Timeout Configuration**: Separate timeouts for connection, read, and request
- **Graceful Degradation**: Continues with other OLVM instances if one fails
- **Detailed Logging**: Comprehensive error messages with context

## Single-Instance Lock

The application uses a file-based lock to ensure only one instance runs at a time:

- **Lock File Location**: `/var/run/user/<uid>/mcmp-eai-olvm.pid` (Linux) or temp directory (other OS)
- **Stale Lock Detection**: Automatically removes locks from crashed processes
- **Error Message**: Clear indication when another instance is running


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

Project Link: [github.com/it-at-m/mcmp/mcmp-eai-olvm](github.com/it-at-m/mcmp/mcmp-eai-olvm)

<p align="right">(<a href="#top">back to top</a>)</p>