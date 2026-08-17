<div id="top"></div>

## About The Project

**mcmp-eai-db-oracle** is an Enterprise Application Integration (EAI) service for the Munich Cloud Management Platform (MCMP). Its primary purpose is to discover Oracle Database instances, analyze database-specific metrics (instance information, users/schemas, tablespace utilization), and report these metrics back to the MCMP backend instances.

### Key Features

- **Oracle Database Metrics Discovery**: Extracts instance metadata, user/schema profiles, and tablespace usage directly from Oracle databases via SQL queries (`v$instance`, `dba_users`, `dba_tablespaces`, `dba_segments`, etc.).
- **Multi-MCMP Support**: Concurrently transmits export data to multiple configured MCMP backend instances via OAuth2.
- **Discovery Backend Integration**: Retrieves the list of target Oracle servers dynamically from a designated primary MCMP backend (`DiscoveryBackend = true`).
- **Parallel Processing**: Configurable worker pool for concurrent Oracle server analysis.
- **Metadata Injection**: Enriches exports with runtime environment, version, VCS commit details, and execution statistics.
- **Data Export & Backup**: Generates local JSON files (`oracle_export_<identifier>.json`) per run for auditability and debugging.
- **Graceful Shutdown**: Handles OS signals (`SIGINT`, `SIGTERM`) for safe cancellation.

### Data Analysis Flow

1. **Configuration Loading**: Reads TOML configuration (`mcmp-eai-db-oracle.toml`) defining logging, MCMP endpoints, and Oracle database credentials.
2. **Server Discovery**: Queries the designated primary MCMP backend (`DiscoveryBackend = true`) via `OracleServerEndpoint` to retrieve the target Oracle database servers (`FQDN` and `PDB`).
3. **Concurrent Database Analysis**: Launches worker goroutines to connect to each Oracle database and execute the metric queries:
    - `instance_info`: Instance name, PDB, character set, startup time.
    - `user_info`: User accounts, profiles, last login, and assigned tablespaces.
    - `tablespace_info`: Tablespace types, data used, and maximum sizes.
4. **Metadata Injection**: Automatically injects runtime information, VCS metadata, execution status, and duration into the dataset.
5. **Persistence & Transmission**: Writes the export data to `oracle_export_mcmp.json` and concurrently sends it via HTTP POST to the `ApiEndpoint` of all configured MCMP instances.

## Documentation

### Requirements

- [Go](https://go.dev/) >= 1.26
- Network connectivity to Oracle Database servers (default port: 1521)
- Network connectivity to Keycloak / OAuth2 servers and MCMP API endpoints
- Oracle database user with read privileges for `v$instance`, `v$nls_parameters`, `dba_users`, `dba_tablespaces`, `dba_segments`, `dba_data_files`, and `dba_temp_files`.

### Installation

#### 1. Clone the Repository
```bash
git clone https://github.com/it-at-m/mcmp.git
cd mcmp/mcmp-eai-db-oracle
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build -o mcmp-eai-db-oracle main.go
```

This creates an executable named `mcmp-eai-db-oracle` (or `mcmp-eai-db-oracle..exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-db-oracle.toml.example mcmp-eai-db-oracle.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-db-oracle.toml
```

Configure the following sections:
- **[LOGGING]**: Controls log level (`DEBUG`, `INFO`, `WARN`, `ERROR`), output destination (`console`, `file`, `both`), and log rotation parameters.
- **[[MCMP]]**: A list of MCMP backend instances.
    - `OAuthUrl`, `OAuthRealm`, `OAuthClientId`, `OAuthClientSecret`: OAuth2 / Keycloak credentials.
    - `ApiEndpoint`: Target endpoint to send exported database metrics to (e.g. `/clients/eai-backend-service/db/oracle/import`).
    - `DiscoveryBackend`: Boolean flag. **Exactly one** MCMP backend must have `DiscoveryBackend = true`.
    - `OracleServerEndpoint`: Endpoint URL on the discovery backend to fetch the list of Oracle servers (e.g. `/clients/eai-backend-service/db/oracle/servers`).
- **[PROCESSOR]**: Oracle database credentials and worker configuration.
    - `WorkerCount`: Number of concurrent workers for querying Oracle databases (default: 5).
    - `OracleUser`: Username for connecting to Oracle databases.
    - `OraclePassword`: Password for connecting to Oracle databases.
    - `OraclePort`: Oracle listener port (default: 1521).

#### 3. Example Configuration

```toml
# Logging Configuration
[LOGGING]
Level      = "INFO"
Output     = "file"
Format     = "plain"
Filename   = "/var/log/mcmp-eai-db-oracle.log"
MaxSize    = 10
MaxBackups = 7
MaxAge     = 30
Compress   = true

# MCMP API Configuration (Primary Backend with Discovery)
[[MCMP]]
OAuthUrl              = "https://keycloak.example.com/auth"
OAuthRealm            = "local_realm"
OAuthClientId         = "<client-id>"
OAuthClientSecret     = "<client-secret>"
ApiEndpoint           = "http://localhost:8083/clients/eai-backend-service/db/oracle/import"
DiscoveryBackend      = true
OracleServerEndpoint  = "http://localhost:8083/clients/eai-backend-service/db/oracle/servers"

# Secondary MCMP Backend (Export only)
[[MCMP]]
OAuthUrl              = "https://keycloak-secondary.example.com/auth"
OAuthRealm            = "local_realm"
OAuthClientId         = "<client-id>"
OAuthClientSecret     = "<client-secret>"
ApiEndpoint           = "http://secondary:8083/clients/eai-backend-service/db/oracle/import"
DiscoveryBackend      = false

# Processor Settings
[PROCESSOR]
WorkerCount    = 10
OracleUser     = "<USERNAME>"
OraclePassword = "<PASSWORD>"
```

### Execution

#### Standard Operation
```bash
./mcmp-eai-db-oracle
```

## Architecture

The application is structured with clear separation of concerns:

```
mcmp-eai-db-oracle/
├── pkg/
│   ├── client/
│   │   └── source/      # Implementation of the DataSource interface for Oracle exports
│   └── processor/       # Oracle DB querying, metrics extraction, and worker pool logic
├── main.go              # Application bootstrap, configuration loading, and orchestration
├── mcmp-eai-db-oracle.toml.example
└── README.md
```

### Key Components

- **Processor**: Connects to Oracle databases via go-ora, runs analysis queries, and aggregates results.
- **Source**: Implements the datasource.JsonFileSource interface for standardized file persistence and concurrent MCMP multi-client transmission.
- **Common Framework (mcmp-eai-common)**: Provides unified logging, lifecycle management, locking, and OAuth2 client communications.

## Data Model

### Oracle Export JSON Format

```json
{
  "metadata": {
    "name": "mcmp-eai-db-oracle",
    "version": "1.0.0",
    "status": "SUCCESS",
    "fqdn": "mcmp-runner.example.com",
    "start_time": "2026-08-17T10:00:00Z",
    "end_time": "2026-08-17T10:05:00Z",
    "duration": "5m0s"
  },
  "databases": [
    {
      "fqdn": "dbserver01.example.com",
      "timestamp": "2026-08-17T10:01:23Z",
      "data": [
        {
          "queryName": "instance_info",
          "rows": [
            {
              "pdb_name": "PDB01",
              "host_name": "dbserver01",
              "characterset": "AL32UTF8",
              "startup_time": "2026-08-01 08:00:00"
            }
          ]
        },
        {
          "queryName": "tablespace_info",
          "rows": [
            {
              "tablespace_name": "USERS",
              "tablespace_type": "Persistent Data",
              "data_used_in_b": 524288000,
              "data_max_in_b": 10737418240
            }
          ]
        }
      ]
    }
  ]
}
```

## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [mcmp-eai-db-oracle](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-db-oracle)

<p align="right">(<a href="#top">back to top</a>)</p>