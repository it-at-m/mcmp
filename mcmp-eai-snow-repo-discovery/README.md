<div id="top"></div>

### About The Project

**mcmp-eai-snow-repo-discovery** is an Enterprise Application Integration (EAI) service for the Munich Cloud Management Platform (MCMP). Its primary purpose is to scan repository structures (e.g., Linux repository servers) and report these findings to ServiceNow to trigger or update discovery information before the data is synchronized with the MCMP backend.

The application runs as a scheduled task (e.g., daily), fetching repository lists and sending them to the ServiceNow `Identify & Reconcile` API to ensure the repositories are correctly represented as Configuration Items (CIs) in the CMDB.

### Key Features

- **Repository Discovery**: Automated scanning of web directories (Apache Autoindex compatible).
- **ServiceNow Integration**: Sends discovered repository names to ServiceNow via the `Identify & Reconcile` API for discovery and identification.
- **Multi-Source Support**: Concurrent querying of multiple repository servers.
- **MCMP API Integration**: Seamless data transmission to the MCMP backend via OAuth2.
- **Metadata Injection**: Automatically enriches exports with runtime environment, version, and VCS information.
- **Data Export**: Creates local JSON backups of discovered and reconciled repositories for auditing.
- **Rate Limiting**: Configurable wait times between ServiceNow requests to protect API resources.

### Data Analysis Flow

1. **Configuration Loading**: Reads TOML files for logging, MCMP endpoints, ServiceNow credentials, and repository sources.
2. **Client Initialization**: Sets up HTTP clients for repository servers, ServiceNow (OAuth2), and MCMP.
3. **Concurrent Processing**: Launches parallel workers for each enabled repository source.
4. **Discovery & ServiceNow Reporting**:
  - Fetches repository lists from the source servers.
  - For each repository, sends an identification request to ServiceNow.
  - Captures Status, Sys-IDs, and potential errors from the CMDB response.
5. **Metadata Injection**: Injects version, commit ID, and execution statistics into the data model.
6. **Persistence & Transmission**: Saves results locally as `snow_repo_discovery_<identifier>.json` and concurrently sends them to all configured MCMP instances.

## Documentation

### Requirements

- [Go](https://go.dev/) >= 1.26
- Network connectivity to repository URLs, ServiceNow API, and MCMP backend.
- OAuth2 client credentials for ServiceNow and MCMP.

### Configuration

Configuration is managed via a TOML file (default: `mcmp-eai-snow-repo-discovery.toml`).

#### Section Overview
- **[LOGGING]**: Controls log levels (DEBUG, INFO, etc.), output destination, and rotation settings.
- **[SERVICENOW]**: Endpoint and OAuth2 credentials for ServiceNow.
  - `WaitBetweenRequests`: Duration to pause between API calls (e.g., "2s").
- **[[MCMP]]**: A list of MCMP backend instances including OAuth2 credentials and API endpoints.
- **[[REPO]]**: A list of repository servers to scan.

### Execution

#### Build
```bash
go build -o mcmp-eai-snow-repo-discovery main.go
```

#### Run
```bash
./mcmp-eai-snow-repo-discovery
```

## Architecture

```
mcmp-eai-snow-repo-discovery/
├── pkg/
│   ├── client/
│   │   └── source/      # Implementation of the DataSource interface
│   └── processor/       # Logic for repository listing and SNOW discovery reporting
├── main.go              # Application bootstrap and configuration mapping
└── README.md
```

### Key Components

- **Processor**: andles the logic for parsing repository listings and wrapping them for the ServiceNow **IdentifyReconcile API**.
- **Source**: Acts as the bridge to the `mcmp-eai-common` framework for standardized processing and export..

## Data Model

### Snow Repo Discovery Export Format
The exported JSON body follows this schema:

```json
{
  "metadata": {
    "name": "mcmp-eai-snow-repo-discovery",
    "version": "1.0.0",
    "fqdn": "server.example.com",
    "start_time": "2026-08-03T09:50:03.109878354+02:00",
    "end_time": "2026-08-03T09:55:48.616250247+02:00",
    "duration": "10.5s",
    "status": "SUCCESS"
  },
  "repositories": [
    {
      "name": "repo1",
      "url": "https://repo.example.com/repo1",
      "discovery_success": true,
      "sys_id": "84a3c...",
      "operation_status": "NO_CHANGE",
      "class_name": "x_lam_lhm_packag_0_cmdb_ci_package_repository",
      "error_count": 0,
      "warning_count": 0
    },
    {
      "name": "repo2",
      "url": "https://repo.example.com/repo2/",
      "discovery_success": true,
      "sys_id": "e9ce6...",
      "operation_status": "INSERT",
      "class_name": "x_lam_lhm_packag_0_cmdb_ci_package_repository",
      "error_count": 0,
      "warning_count": 0
    }
  ]
}
```

## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [mcmp-eai-snow-repo-discovery](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-snow-repo-discovery)

<p align="right">(<a href="#top">back to top</a>)</p>