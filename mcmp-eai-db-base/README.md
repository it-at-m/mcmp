<div id="top"></div>

## About The Project

**mcmp-eai-db-base** is the shared base module for all database EAI services of the Munich Cloud Management Platform (MCMP). It contains all database-agnostic logic used by the concrete adapters (`mcmp-eai-db-oracle`, `mcmp-eai-db-postgres`, `mcmp-eai-db-mysql`, `mcmp-eai-db-mariadb`, `mcmp-eai-db-mssql`, …). An adapter only implements the SQL dialect-specific mapping and the server discovery.

### Key Features

- **Unified processing pipeline**: Worker-pool based processing of all database servers including error aggregation and statistics logging.
- **Dialect abstraction**: Driver name, DSN construction and the SQL queries to execute are provided per database via the `Dialect` interface.
- **Pluggable server discovery**: The list of servers to analyze (`FQDN`, `PDB`) is obtained via a `ServerProvider` interface – typically from the MCMP Discovery backend.
- **Multi-MCMP support**: Export data is sent in parallel to multiple configured MCMP backends (OAuth2); exactly one backend acts as the discovery backend.
- **Standardized data source**: `source.NewSource` creates a fully configured `datasource.JsonFileSource`, including a local JSON export file per run.
- **Shared runner**: `runner.Run` handles logging setup, validation, client creation, locking/timeout and lifecycle management (graceful shutdown).
- **Unified export data model**: `processor.Export` / `processor.DatabaseMetrics` / `processor.QueryResult` with automatic type conversion of SQL results.

### Processing Flow

1. **Runner start**: `runner.Run` creates the global logger, validates the configuration (`CommonConfig.Validate`) and creates a client for each `[[MCMP]]` configuration.
2. **Determine discovery backend**: Exactly one backend must set `DiscoveryBackend = true`; it is passed as the discovery client to `Params.NewProcessor`.
3. **Create processor**: The adapter builds a `*processor.Processor` from the discovery client, the `Dialect` and `processor.Config` inside the `NewProcessor` callback.
4. **Server discovery**: `Processor.FetchDatabaseMetrics` calls `ServerProvider.GetServers` and receives the list of target systems.
5. **Parallel analysis**: `Config.WorkerCount` goroutines process the servers; per server the `NamedQuery` entries of the dialect are executed (max. 5 in parallel) against a shared `*sql.DB`.
6. **Aggregation & statistics**: Failed servers are logged and counted; if **all** servers failed, `ErrAllServersFailed` is returned. On partial failure, `Export.Status` is set to `ERROR`. Afterwards metrics are logged (count, fastest/slowest server, top 10).
7. **Persistence & transmission**: The `DatabaseSource` writes the JSON export and sends it in parallel to all `ApiEndpoint`s.

## Documentation

### Requirements

- [Go](https://go.dev/) >= 1.27
- Network connectivity to Keycloak/OAuth2 servers and the MCMP API endpoints
- Network connectivity to the database servers to be analyzed (ports are adapter-specific)

### Dependencies

```go
require (
	github.com/it-at-m/mcmp/mcmp-eai-common v0.0.0-20261025192727-49576af51ef4
	golang.org/x/sync v0.24.0
)
```

The module is wired into the repository via `go.work`:

```bash
go work sync
go mod tidy
```

### Usage in an Adapter

#### 1. Implement a `Dialect`

The dialect encapsulates driver, DSN and the queries. Result column names are automatically lower-cased.

```go
type Dialect struct {
	cfg ProcessorConfig
}

func (d Dialect) DriverName() string { return "pgx" }

func (d Dialect) DSN(s processor.Server) string {
	return fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=require",
		s.FQDN, d.cfg.Port, d.cfg.User, d.cfg.Password, s.PDB)
}

func (d Dialect) Queries() []processor.NamedQuery {
	return []processor.NamedQuery{
		{Name: "instance_info", SQL: `SELECT version() AS version, current_database() AS database_name`},
		{Name: "user_info", SQL: `SELECT usename AS username, usesuper AS is_superuser FROM pg_user`},
	}
}
```

#### 2. Implement a `ServerProvider`

```go
func NewProvider(client *mcmp.Client) processor.ServerProviderFunc {
	return func(ctx context.Context) ([]processor.Server, error) {
		return client.GetServers(ctx) // returns []processor.Server{FQDN, PDB}
	}
}
```

For simple cases `processor.ServerProviderFunc` (a function adapter for the interface) is sufficient.

#### 3. Provide the configuration (`CommonConfig`)

```go
type ProcessorConfig struct {
	WorkerCount int    `toml:"WorkerCount"`
	User        string `toml:"User"`
	Password    string `toml:"Password"`
	Port        int    `toml:"Port"`
}

type AppConfig struct {
	Logging   logging.LogConfig `toml:"LOGGING"`
	Mcmp      []mcmp.Config     `toml:"MCMP"`
	Processor ProcessorConfig   `toml:"PROCESSOR"`
}

func (c AppConfig) MCMPConfigs() []mcmp.Config       { return c.Mcmp }
func (c AppConfig) LoggingConfig() logging.LogConfig { return c.Logging }
func (c AppConfig) ProcessorConfig() ProcessorConfig { return c.Processor }

func (c AppConfig) Validate() error {
	if len(c.Mcmp) == 0 {
		return errors.New("at least one MCMP backend must be configured")
	}
	return nil
}
```

#### 4. Call `runner.Run`

```go
func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	cfg := loadConfig() // read TOML

	err := runner.Run(ctx, cfg, runner.Params[ProcessorConfig]{
		AppName:     "mcmp-eai-db-postgres",
		Timeout:     30 * time.Minute,
		LockEnabled: true,
		DBType:      "PostgreSQL",
		NewProcessor: func(ctx context.Context, client *mcmp.Client, logger *logging.StructuredLogger, pcfg ProcessorConfig) (*processor.Processor, error) {
			return processor.NewProcessor(
				NewProvider(client),
				Dialect{cfg: pcfg},
				logger,
				processor.Config{WorkerCount: pcfg.WorkerCount},
			)
		},
		NewSource: func(proc *processor.Processor, senders []datasource.JSONSender, endpoints []string, logger *logging.StructuredLogger) app.DataSource[*processor.Export] {
			return source.NewSource("PostgreSQL", hostname(), true, proc, senders, endpoints, logger)
		},
	})
	if err != nil {
		log.Fatal(err)
	}
}
```

## Architecture

```
mcmp-eai-db-base/
├── pkg/
│   ├── client/
│   │   └── source/    # DatabaseSource: JsonFileSource[*processor.Export] incl. filename scheme
│   ├── processor/     # Dialect/ServerProvider abstraction, worker pool, export data model
│   └── runner/        # Generic bootstrap: logging, validation, clients, lifecycle
├── go.mod
├── go.sum
└── README.md
```

A concrete adapter therefore only consists of `main.go` (configuration + callbacks), the dialect and an example TOML – see e.g. `mcmp-eai-db-oracle`.

## API Reference

### Package `processor`

| Symbol | Description |
| --- | --- |
| `Server` | `{FQDN, PDB string}` – a target system to analyze (`PDB` is Oracle-only). |
| `ServerProvider` / `ServerProviderFunc` | Interface resp. function adapter: `GetServers(ctx) ([]Server, error)`. |
| `Dialect` | `DriverName() string`, `DSN(server Server) string`, `Queries() []NamedQuery`. |
| `NamedQuery` | `{Name, SQL string}` – named query, `Name` ends up as `queryName` in the export. |
| `Config` | `{WorkerCount int}` – number of parallel server workers, default **5** if `<= 0`. |
| `NewProcessor(provider, dialect, logger, config)` | Creates the processor; `logger == nil` ⇒ no-op logger. |
| `(*Processor).FetchDatabaseMetrics(ctx)` | Runs discovery and analysis, returns `*Export`. |
| `Export` | `app.EaiMetadata` (`json:"metadata"`) + `Databases []DatabaseMetrics`. |
| `DatabaseMetrics` | `fqdn`, `pdb` (omitempty), `type` (omitempty), `startTime`, `endTime`, `duration`, `data`. |
| `QueryResult` | `queryName`, `duration`, `rows []map[string]any`. |

**Error values**

- `ErrNilProvider` – no `ServerProvider` supplied.
- `ErrNoDialect` – no `Dialect` supplied.
- `ErrAllServersFailed` – not a single server could be processed.

**SQL value conversion**: Column names are lower-cased; `NULL` ⇒ `null`; values parseable as integer ⇒ integer, otherwise float, otherwise string; `[]byte` is interpreted as string.

### Package `client/source`

| Symbol | Description |
| --- | --- |
| `DatabaseSource` | Alias for `datasource.JsonFileSource[*processor.Export]`. |
| `NewSource(dbType, hostname string, enabled bool, proc *processor.Processor, mcmpClients []datasource.JSONSender, apiEndpoints []string, logger logging.Logger) *DatabaseSource` | Builds the data source including the export filename `export_db_<dbtype>_<identifier>.json` (`dbType` is lower-cased, spaces become `_`). |

### Package `runner`

| Symbol | Description |
| --- | --- |
| `CommonConfig[P]` | `MCMPConfigs() []mcmp.Config`, `LoggingConfig() logging.LogConfig`, `ProcessorConfig() P`, `Validate() error`. |
| `Params[P]` | `AppName`, `Timeout`, `LockEnabled`, `DBType` as well as the `NewProcessor` and `NewSource` callbacks. |
| `Run[P, C](ctx, cfg, params) error` | Executes the complete run: timeout context, logger, validation, clients, processor, source, `app.RunEAI`. |

**Runner notes**

- `Params.Timeout` bounds the entire run (`context.WithTimeout`).
- **Exactly one** MCMP backend must set `DiscoveryBackend = true`, otherwise the run aborts with an error.
- `LockEnabled` prevents concurrent runs of the same EAI (`app.RunEAI`).
- `DBType` is only used for log output (e.g. `Starting Oracle database analysis`).

## Data Model

### Export JSON

```json
{
  "metadata": {
    "name": "mcmp-eai-db-postgres",
    "version": "1.0.0",
    "status": "SUCCESS",
    "fqdn": "mcmp-runner.example.com",
    "startTime": "2026-08-17T10:00:00Z",
    "endTime": "2026-08-17T10:05:00Z"
  },
  "databases": [
    {
      "fqdn": "dbserver01.example.com",
      "pdb": "PDB01",
      "startTime": "2026-08-17T10:01:23Z",
      "endTime": "2026-08-17T10:01:25Z",
      "duration": 2000000000,
      "data": [
        {
          "queryName": "instance_info",
          "duration": 1500000000,
          "rows": [
            {
              "version": "PostgreSQL 16.3",
              "database_name": "mcmp"
            }
          ]
        }
      ]
    }
  ]
}
```

`metadata` is produced by the shared framework (`app.EaiMetadata`) and injected afterwards; `pdb` and `type` are omitted when empty.

## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [mcmp-eai-db-base](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-db-base)

<p align="right">(<a href="#top">back to top</a>)</p>
```