
<div id="top"></div>

## About The Project

**mcmp-eai-awx** is an Enterprise Application Integration (EAI) service that synchronizes data between AWX (Ansible AWX) and the Munich Cloud Management Platform (MCMP). The application automatically retrieves inventory data, job templates, projects, and related information from AWX and forwards this information to MCMP through secure API calls.

### Key Features

- **AWX Integration**: Connects to AWX instances to fetch inventory data, job templates, and projects
- **MCMP API Integration**: Securely transmits processed data to MCMP endpoints
- **OAuth 2.0 Authentication**: Uses Keycloak for secure authentication with MCMP APIs
- **Data Processing**: Transforms and structures AWX data for MCMP consumption
- **Comprehensive Logging**: Configurable debug logging for troubleshooting and monitoring
- **Data Export**: Creates local JSON backups of synchronized data

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with connection parameters
2. **AWX Data Retrieval**: Fetches inventory data, job templates, projects, and related information
3. **Data Processing**: Transforms and structures data for MCMP compatibility
4. **Authentication**: Obtains OAuth 2.0 token from Keycloak
5. **Data Transmission**: Sends processed data to MCMP API endpoint
6. **Local Backup**: Exports data to local JSON file for audit and debugging

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### General Settings
- **Debug**: Enable verbose logging for development and troubleshooting

#### AWX Configuration
- **Hostname**: AWX instance URL
- **Username/Password**: API credentials for AWX access

#### Keycloak OAuth 2.0 Settings
- **AuthServerUrl**: Keycloak authentication server endpoint
- **Realm**: Keycloak realm name
- **ClientId/ClientSecret**: OAuth 2.0 client credentials

#### MCMP API Configuration
- **ApiEndpoint**: MCMP API endpoint for data submission

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.25 - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **OAuth 2.0** - Secure authentication with Keycloak
- **AWX REST API** - Data retrieval from AWX
- **JSON** - Data serialization and API communication

## Documentation

The AWX API is documented here: https://docs.ansible.com/ansible-tower/latest/html/towerapi/

### Requirements

- [Go](https://go.dev/) >= v1.25
- [Viper](https://github.com/spf13/viper) - For configuration management
- Access to AWX instance with API credentials
- Keycloak server with configured OAuth 2.0 client
- Network connectivity to MCMP API endpoints

### Installation

#### 1. Clone the Repository

```bash
git clone github.com/it-at-m/mcmp.git
cd mcmp-eai-awx
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-awx` (or `mcmp-eai-awx.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-awx.toml.example mcmp-eai-awx.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-awx.toml
```

Configure the following sections:
- **GENERAL**: Set debug mode preference
- **AWX**: AWX instance connection details
- **MCMP**: MCMP API endpoint configuration

### Execution

#### Standard Operation
```bash
./mcmp-eai-awx
```

This command executes the complete data synchronization workflow:
1. Loads configuration from `mcmp-eai-awx.toml`
2. Connects to AWX and retrieves data
3. Processes and transforms the data
4. Authenticates with Keycloak
5. Sends data to MCMP API
6. Creates local backup file (`awx_inventory.json`)

#### Debug Mode
Enable debug logging by setting `Debug = true` in the configuration file or by reviewing the debug output during execution.

### Output Files

- **awx_inventory.json**: Local backup of synchronized data in JSON format
- **Application logs**: Debug information when debug mode is enabled

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── clients/
│   └─ mcmp/         # MCMP API client
├── logging/         # Logging utilities
```

### Key Components

- **AWX Client**: Handles REST API communication with AWX
- **MCMP Client**: Sends processed data to MCMP endpoints
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

Project Link: [github.com/it-at-m/mcmp/mcmp-eai-foreman](github.com/it-at-m/mcmp/mcmp-eai-foreman)

<p align="right">(<a href="#top">back to top</a>)</p>
