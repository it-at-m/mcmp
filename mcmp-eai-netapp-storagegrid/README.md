<div id="top"></div>

## About The Project

**mcmp-eai-netapp-storagegrid** is an Enterprise Application Integration (EAI) service that synchronizes NetApp StorageGRID data with the Munich Cloud Management Platform (MCMP). The application automatically retrieves tenant account information, usage statistics, and bucket details from NetApp StorageGRID systems, then transforms and forwards this data to MCMP through secure API calls.

### Key Features

- **NetApp StorageGRID Integration**: Retrieves comprehensive data from StorageGRID Grid Management APIs.
- **Multi-Source Support**: Configurable connections to multiple StorageGRID systems simultaneously.
- **Parallel Data Fetching**: Concurrent API calls to fetch usage data for multiple accounts efficiently.
- **MCMP API Integration**: Securely transmits processed data to MCMP endpoints via OAuth2.
- **Data Aggregation**: Combines tenant accounts with their respective usage statistics and bucket details.
- **Comprehensive Logging**: Configurable log levels with file rotation support.
- **Data Export**: Creates local JSON backups of synchronized data.

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with connection parameters.
2. **StorageGRID Data Retrieval**:
    - Authenticates against the Grid Management API.
    - Fetches the list of Tenant Accounts.
    - Concurrently fetches usage data (DataBytes, ObjectCount, Buckets) for each account.
3. **Data Aggregation**: Combines accounts and usage data into a unified structure.
4. **Data Transmission**: Sends processed data to MCMP API endpoint.
5. **Local Backup**: Exports data to local JSON file for audit and debugging.

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### Logging Settings
- **Level**: Log level (DEBUG, INFO, WARN, ERROR)
- **Output**: Output destination (console, file, both)
- **Format**: Output format (text, json, plain)
- **File Rotation**: Configurable size, backups, and age limits

#### General Settings
- **Timeout**: Global operation timeout in seconds

#### StorageGRID Configuration (multiple sources supported)
- **Hostname**: StorageGRID Admin Node hostname or IP address
- **Username**: Grid Administrator username
- **Password**: Grid Administrator password
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

- [Go](https://go.dev/) >= 1.25
- Network connectivity to NetApp StorageGRID Grid Management API (usually port 443)
- Network connectivity to MCMP API endpoints
- OAuth2 client credentials for MCMP authentication

### Installation

#### 1. Clone the Repository
```bash
git clone github.com/it-at-m/mcmp.git
cd webanwendung/mcmp-eai-netapp-storagegrid
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-netapp-storagegrid` (or `mcmp-eai-netapp-storagegrid.exe` on Windows).

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
- **STORAGEGRID**: NetApp StorageGRID connection details (multiple sources supported)
- **MCMP**: MCMP API endpoint and OAuth2 configuration

### Execution

#### Standard Operation
```bash
./mcmp-eai-netapp-storagegrid
```

This command executes the complete data synchronization workflow:
1. Loads configuration from `mcmp-eai-netapp-storagegrid.toml`
2. Connects to configured NetApp StorageGRID systems
3. Retrieves and aggregates account and usage data
4. Authenticates with Keycloak and sends data to MCMP API
5. Creates local backup file (`netapp_storagegrid_export_<hostname>.json`)

#### Debug Mode
Enable debug logging by setting `Level = "DEBUG"` in the `[LOGGING]` section of the configuration file.

### Output Files

- **netapp_storagegrid_export_\<hostname\>.json**: Local backup of synchronized data per StorageGRID source
- **Application logs**: Configurable log output to console and/or file

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── client/
│   ├── netapp/
│   │   └── storagegrid/ # NetApp StorageGRID REST API client and types
│   └── source/          # Data source abstraction
└── processor/           # Data aggregation and processing logic
```

### Key Components

- **StorageGRID Client**: Handles REST API communication with NetApp StorageGRID systems.
- **Processor**: Aggregates data from Account and Usage API endpoints into unified structures using concurrency.
- **Source**: Implements the DataSource interface for the EAI runner.
- **EAI Runner**: Generic runner from mcmp-eai-common for orchestrating data sources.

## Data Model

### Aggregated Data Structure

The application aggregates the following data for the StorageGRID system:

```
StorageGridData
├── Hostname (Source system)
└── Accounts[] (List of Tenant Accounts)
    ├── ID and Name
    ├── Quota (QuotaObjectBytes)
    ├── Usage Statistics
    │   ├── DataBytes (Total size used)
    │   ├── ObjectCount (Total objects)
    │   └── CalculationTime
    └── Buckets[] (List of buckets for this account)
        ├── Name
        ├── DataBytes
        └── ObjectCount
```

### Output JSON Format Example

```json
{
  "hostname": "storagegrid.example.com",
  "accounts": [
    {
      "id": "12345678-1234-1234-1234-123456789012",
      "name": "tenant-a",
      "quotaObjectBytes": 107374182400,
      "dataBytes": 5368709120,
      "objectCount": 1500,
      "calculationTime": "2023-10-27T10:00:00Z",
      "buckets": [
        {
          "name": "bucket-01",
          "objectCount": 1000,
          "dataBytes": 4000000000
        },
         {
          "name": "bucket-02",
          "objectCount": 500,
          "dataBytes": 1368709120
        }
      ]
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

Project Link: [https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-netapp-storagegrid](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-netapp-storagegrid)

<p align="right">(<a href="#top">back to top</a>)</p>