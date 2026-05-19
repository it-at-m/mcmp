<div id="top"></div>

## About The Project

**mcmp-eai-patchnight** is an Enterprise Application Integration (EAI) service that synchronizes patchnight data with the Munich Cloud Management Platform (MCMP). The application automatically retrieves patchnight schedules, server inclusion/exclusion lists, and maintenance windows, then transforms and forwards this data to MCMP through secure API calls.

### Key Features

- **Patchnight Data Integration**: Processes patchnight schedules, server lists, and maintenance windows
- **MCMP API Integration**: Securely transmits processed data to MCMP endpoints
- **Data Processing**: Transforms and structures patchnight data for MCMP consumption
- **Comprehensive Logging**: Configurable debug logging for troubleshooting and monitoring
- **Data Export**: Creates local JSON backups of synchronized data

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with connection parameters
2. **Patchnight Data Retrieval**: Fetches patchnight dates, included servers, and excluded servers
3. **Data Processing**: Transforms and structures data for MCMP compatibility
4. **Data Transmission**: Sends processed data to MCMP API endpoint
5. **Local Backup**: Exports data to local JSON file for audit and debugging

### Configuration Structure

The application uses a TOML configuration file with the following sections:

#### General Settings
- **Debug**: Enable verbose logging for development and troubleshooting

#### Patchnight Configuration
- Connection parameters for patchnight data sources
- Authentication credentials

#### MCMP API Configuration
- **ApiEndpoint**: MCMP API endpoint for data submission

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.25.5 - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **JSON** - Data serialization and API communication

## Documentation

### Requirements

- [Go](https://go.dev/) >= v1.24.3
- [Viper](https://github.com/spf13/viper) >= v1.20.1
- Access to patchnight data sources
- Network connectivity to MCMP API endpoints

### Installation

#### 1. Clone the Repository
```bash
git clone github.com/it-at-m/mcmp.git
cd mcmp-eai-patchnight
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-patchnight` (or `mcmp-eai-patchnight.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-patchnight.toml.example mcmp-eai-patchnight.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-patchnight.toml
```

Configure the following sections:
- **GENERAL**: Set debug mode preference
- **PATCHNIGHT**: Patchnight data source connection details
- **MCMP**: MCMP API endpoint configuration

### Execution

#### Standard Operation
```bash
./mcmp-eai-patchnight
```

This command executes the complete data synchronization workflow:
1. Loads configuration from `mcmp-eai-patchnight.toml`
2. Connects to patchnight data sources and retrieves data
3. Processes and transforms the data
4. Sends data to MCMP API
5. Creates local backup file (`patchnight_export.json`)

#### Debug Mode
Enable debug logging by setting `Debug = true` in the configuration file or by reviewing the debug output during execution.

### Output Files

- **patchnight_export.json**: Local backup of synchronized data in JSON format
- **Application logs**: Debug information when debug mode is enabled

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── clients/
│   ├── mcmp/        # MCMP API client
│   └── patchnight/  # Patchnight data client
├── logging/         # Logging utilities
└── processor/       # Data processing and transformation logic
```

### Key Components

- **Patchnight Client**: Handles communication with patchnight data sources
- **MCMP Client**: Sends processed data to MCMP endpoints
- **Processor**: Orchestrates data retrieval, transformation, and export
- **Configuration Manager**: Handles TOML configuration parsing

## Data Transformation Example

The application transforms three separate patchnight JSON inputs into a unified MCMP-compatible format. Here's a graphical text example:

### Input Data Sources (3 JSON formats):

#### 1. Patchnight Dates (`patchnight_datum.json`)
```json
{
  "create_date": "2025-07-02",
  "patchnight_dates": [
    {
      "env": "k",
      "date": "2025-07-11",
      "start_date": "2025-07-11T15:00:00",
      "end_date": "2025-07-12T01:00:00"
    },
    {
      "env": "p",
      "date": "2025-07-28",
      "start_date": "2025-07-28T20:00:00",
      "end_date": "2025-07-29T06:00:00"
    }
  ]
}
```

#### 2. Included Servers (`patchnight_includ.json`)
```json
{
  "create_date": "2025-07-02",
  "patchnight_includ_all": [
    {
      "env": "k",
      "name": "linuxk001.example.com",
      "start_time": "15:00",
      "end_time": "17:00",
      "os": "RedHat",
      "os_version": "7.9"
    },
    {
      "env": "k",
      "name": "linuxk002.example.com",
      "start_time": "15:00",
      "end_time": "17:00",
      "os": "RedHat",
      "os_version": "9.6"
    }
  ]
}
```

#### 3. Excluded Servers (`patchnight_exclud.json`)
```json
{
  "create_date": "2025-07-02",
  "patchnight_exclud_all": [
    {
      "name": "linuxk003.example.com",
      "os": "RedHat",
      "os_version": "9.5"
    },
    {
      "name": "linuxk004.example.com",
      "os": "RedHat",
      "os_version": "8.10"
    }
  ]
}
```

### Transformation Process

```
Input JSON 1 (Dates)     ──┐
                           │
Input JSON 2 (Included)  ──┼──► [Data Processor] ──► Output MCMP JSON
                           │
Input JSON 3 (Excluded)  ──┘
```

### Output MCMP JSON Format (`patchnight_export.json`)

```json
{
  "server": [
    {
      "env": "k",
      "name": "linuxk001.example.com",
      "include": true,
      "start_date": "2025-07-11T21:00:00+02:00",
      "end_date": "2025-07-11T23:00:00+02:00"
    },
    {
      "env": "k",
      "name": "linuxk002.example.com",
      "include": true,
      "start_date": "2025-07-11T21:00:00+02:00",
      "end_date": "2025-07-11T23:00:00+02:00"
    },
    {
      "name": "linuxk003.example.com",
      "include": false
    },
    {
      "name": "linuxk004.example.com",
      "include": false
    }
  ]
}
```

### Transformation Logic

The data processor performs the following transformations:

1. **Date Matching**: Matches servers with their corresponding patchnight dates based on environment (`env`)
2. **Server Merging**: Combines included and excluded server lists into a unified structure
3. **Time Zone Conversion**: Converts local times to timezone-aware timestamps (UTC+2)
4. **Include Flag**: Sets `include: true` for servers in the inclusion list and `include: false` for excluded servers
5. **Data Enrichment**: Adds maintenance window times from the patchnight dates to included servers

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

Project Link: [https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-patchnight](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-patchnight)

<p align="right">(<a href="#top">back to top</a>)</p>