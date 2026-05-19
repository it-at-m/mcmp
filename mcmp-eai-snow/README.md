<div id="top"></div>

## About The Project

**mcmp-eai-snow** is an Enterprise Application Integration (EAI) service that synchronizes data between ServiceNow and the Munich Cloud Management Platform (MCMP). The application automatically retrieves application services, configuration items (CIs), user groups, and user data from ServiceNow and forwards this information to MCMP through secure API calls.

### Key Features

- **ServiceNow Integration**: Connects to ServiceNow instances to fetch application services and related data
- **MCMP API Integration**: Securely transmits processed data to MCMP endpoints
- **OAuth 2.0 Authentication**: Uses Keycloak for secure authentication with MCMP APIs
- **Data Processing**: Transforms and structures ServiceNow data for MCMP consumption
- **Comprehensive Logging**: Configurable debug logging for troubleshooting and monitoring
- **Data Export**: Creates local JSON backups of synchronized data

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with connection parameters
2. **ServiceNow Data Retrieval**: Fetches application services, CIs, groups, and users
3. **Data Processing**: Transforms and structures data for MCMP compatibility
4. **Authentication**: Obtains OAuth 2.0 token from Keycloak
5. **Data Transmission**: Sends processed data to MCMP API endpoint
6. **Local Backup**: Exports data to local JSON file for audit and debugging

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### General Settings
- **Debug**: Enable verbose logging for development and troubleshooting

#### ServiceNow Configuration
- **Hostname**: ServiceNow instance URL
- **Username/Password**: API credentials for ServiceNow access
- **Proxy**: Optional HTTP proxy configuration

#### Keycloak OAuth 2.0 Settings
- **AuthServerUrl**: Keycloak authentication server endpoint
- **Realm**: Keycloak realm name
- **ClientId/ClientSecret**: OAuth 2.0 client credentials

#### MCMP API Configuration
- **ApiEndpoint**: MCMP API endpoint for data submission

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.24.2 - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **OAuth 2.0** - Secure authentication with Keycloak
- **ServiceNow REST API** - Data retrieval from ServiceNow
- **JSON** - Data serialization and API communication

## Documentation

The ServiceNow API is documented here: https://git.muenchen.de/servicenow/cmp-api-spec

### Requirements

- [Go](https://go.dev/) >= v1.24.2
- [Viper](https://github.com/spf13/viper) >= v1.20.1
- Access to ServiceNow instance with API credentials
- Keycloak server with configured OAuth 2.0 client
- Network connectivity to MCMP API endpoints

### Installation

#### 1. Clone the Repository
```bash
git clone https://github.com/it-at-m/mcmp.git
cd mcmp/mcmp-eai-snow
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-snow` (or `mcmp-eai-snow.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-snow.toml.example mcmp-eai-snow.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-snow.toml
```

Configure the following sections:
- **GENERAL**: Set debug mode preference
- **SERVICENOW**: ServiceNow instance connection details
- **KEYCLOAK**: OAuth 2.0 authentication parameters
- **CMP**: MCMP API endpoint configuration

### Execution

#### Standard Operation
```bash
./mcmp-eai-snow
```

This command executes the complete data synchronization workflow:
1. Loads configuration from `mcmp-eai-snow.toml`
2. Connects to ServiceNow and retrieves data
3. Processes and transforms the data
4. Authenticates with Keycloak
5. Sends data to MCMP API
6. Creates local backup file (`snowdata_export.json`)

#### Debug Mode
Enable debug logging by setting `Debug = true` in the configuration file or by reviewing the debug output during execution.

### Output Files

- **snowdata_export.json**: Local backup of synchronized data in JSON format
- **Application logs**: Debug information when debug mode is enabled

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── clients/
│   ├── keycloak/    # OAuth 2.0 authentication client
│   ├── mcmp/        # MCMP API client
│   └── snow/        # ServiceNow API client
├── logging/         # Logging utilities
└── processor/       # Data processing and transformation logic
```

### Key Components

- **ServiceNow Client**: Handles REST API communication with ServiceNow
- **Keycloak Client**: Manages OAuth 2.0 authentication flow
- **MCMP Client**: Sends processed data to MCMP endpoints
- **Processor**: Orchestrates data retrieval, transformation, and export
- **Configuration Manager**: Handles TOML configuration parsing

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

Project Link: https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-snow

<p align="right">(<a href="#top">back to top</a>)</p>
