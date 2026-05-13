<div id="top"></div>

## About The Project

**mcmp-eai-netapp-ontap** is an Enterprise Application Integration (EAI) service that synchronizes NetApp ONTAP storage data with the Munich Cloud Management Platform (MCMP). The application automatically retrieves storage information including volumes, CIFS shares, export policies, snapshots, and qtrees from NetApp ONTAP systems, then transforms and forwards this data to MCMP through secure API calls.

### Key Features

- **NetApp ONTAP Integration**: Retrieves comprehensive storage data from multiple ONTAP clusters
- **Multi-Source Support**: Configurable connections to multiple ONTAP systems simultaneously
- **Parallel Data Fetching**: Concurrent API calls for optimized performance
- **MCMP API Integration**: Securely transmits processed data to MCMP endpoints via OAuth2
- **Data Aggregation**: Combines volumes with related export policies, CIFS shares, snapshots, and qtrees
- **Comprehensive Logging**: Configurable log levels with file rotation support
- **Data Export**: Creates local JSON backups of synchronized data

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with connection parameters
2. **ONTAP Data Retrieval**: Fetches volumes, export policies, CIFS shares, snapshots, and qtrees
3. **Data Aggregation**: Combines related data entities into unified volume structures
4. **Data Transmission**: Sends processed data to MCMP API endpoint
5. **Local Backup**: Exports data to local JSON file for audit and debugging

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### Logging Settings
- **Level**: Log level (DEBUG, INFO, WARN, ERROR)
- **Output**: Output destination (console, file, both)
- **Format**: Output format (text, json, plain)
- **File Rotation**: Configurable size, backups, and age limits

#### General Settings
- **Timeout**: Global operation timeout in seconds

#### ONTAP Configuration (multiple sources supported)
- **Hostname**: ONTAP cluster hostname or IP address
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

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.25+ - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **errgroup** - Concurrent goroutine management with error handling
- **OAuth2** - Secure API authentication

## Documentation

### Requirements

- [Go](https://go.dev/) >= 1.24
- Network connectivity to NetApp ONTAP REST API (port 443)
- Network connectivity to MCMP API endpoints
- OAuth2 client credentials for MCMP authentication

### Installation

#### 1. Clone the Repository
```bash
git clone github.com/it-at-m/mcmp.git
cd mcmp-eai-netapp-storagegrid
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-netapp-ontap` (or `mcmp-eai-netapp-ontap.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-netapp-storagegrid.toml.example mcmp-eai-netapp-storagegrid.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-netapp-storagegrid.toml
```

Configure the following sections:
- **LOGGING**: Set log level, output destination, and rotation settings
- **GENERAL**: Set operation timeout
- **ONTAP**: NetApp ONTAP cluster connection details (multiple sources supported)
- **MCMP**: MCMP API endpoint and OAuth2 configuration

### Execution

#### Standard Operation
```bash
./mcmp-eai-netapp-storagegrid
```

This command executes the complete data synchronization workflow:
1. Loads configuration from `mcmp-eai-netapp-ontap.toml`
2. Connects to configured NetApp ONTAP systems
3. Retrieves and aggregates storage data
4. Authenticates with Keycloak and sends data to MCMP API
5. Creates local backup file (`netapp_ontap_export_<hostname>.json`)

#### Debug Mode
Enable debug logging by setting `Level = "DEBUG"` in the `[LOGGING]` section of the configuration file.

### Output Files

- **netapp_ontap_export_\<hostname\>.json**: Local backup of synchronized data per ONTAP source
- **Application logs**: Configurable log output to console and/or file

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── client/
│   ├── netapp/
│   │   └── ontap/       # NetApp ONTAP REST API client
│   └── source/          # Data source abstraction
└── processor/           # Data aggregation and processing logic
```

### Key Components

- **ONTAP Client**: Handles REST API communication with NetApp ONTAP systems
- **Processor**: Aggregates data from multiple API endpoints into unified structures
- **Source**: Implements the DataSource interface for the EAI runner
- **EAI Runner**: Generic runner from mcmp-eai-common for orchestrating data sources

## Data Model

### Aggregated Volume Structure

The application aggregates the following data for each volume:

```
AggregatedVolume
├── Volume (base volume information)
│   ├── UUID, Name, Size
│   ├── SVM reference
│   ├── Snapshot Policy
│   ├── NAS Path and Export Policy ID
│   ├── Space utilization
│   └── Clone information
├── ExportPolicy (NFS export rules)
│   ├── Policy ID and Name
│   └── Rules (clients, protocols, permissions)
├── CIFSShares[] (Windows shares)
│   ├── Share name and path
│   └── ACLs (user/group permissions)
├── Snapshots[] (point-in-time copies)
│   ├── UUID and Name
│   └── Creation timestamp
└── QTrees[] (quota trees)
    ├── ID and Name
    ├── NAS path
    └── Quota information
```

### Output JSON Format Example

```json
{
  "name": "ontap1.example.com",
  "volumes": [
    {
      "uuid": "12345678-1234-1234-1234-123456789012",
      "name": "vol_data_01",
      "size": 107374182400,
      "svm": {
        "name": "svm_prod",
        "uuid": "..."
      },
      "export_policy_details": {
        "id": 1,
        "name": "default",
        "rules": [...]
      },
      "cifs_shares": [...],
      "snapshots": [...],
      "qtrees": [...]
    }
  ]
}
```

## Contributing

Contributions are welcome and help improve the integration capabilities. To contribute:

1. **Report Issues**: Open an issue with the "enhancement" tag
2. **Fork Repository**: Create your own fork of the project
3. **Create Feature Branch**: `git checkout -b feature/YourFeature`
4. **Commit Changes**: `git commit -m 'Add YourFeature'`
5. **Push Branch**: `git push origin feature/YourFeature`
6. **Open Pull Request**: Submit your changes for review

Please ensure your code follows Go best practices and includes appropriate documentation.

## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [github.com/it-at-m/mcmp/mcmp-eai-patchnight](github.com/it-at-m/mcmp/mcmp-eai-patchnight)

<p align="right">(<a href="#top">back to top</a>)</p>