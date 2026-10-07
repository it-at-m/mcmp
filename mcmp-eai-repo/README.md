<div id="top"></div>

## About The Project

**mcmp-eai-repo** is an Enterprise Application Integration (EAI) service for the Munich Cloud Management Platform (MCMP). Its primary purpose is to scan repository structures (e.g., Linux repository servers) and automatically report available repositories to the MCMP backend.

The application parses Apache-style directory listings, extracts repository information, and provides this data to MCMP for further processing and management.

### Key Features

- **Repository Discovery**: Automated scanning of web directories (Apache Autoindex compatible).
- **Multi-Source Support**: Concurrent querying of multiple repository servers.
- **MCMP API Integration**: Seamless data transmission to the MCMP backend via OAuth2.
- **Metadata Injection**: Automatically enriches exports with runtime environment, version, and VCS information.
- **Data Export**: Creates local JSON backups of discovered repositories for auditing.
- **Graceful Shutdown**: Handles OS signals (SIGINT, SIGTERM) for clean termination.

### Data Analysis Flow

1. **Configuration Loading**: Reads TOML files for logging, MCMP endpoints, and repository sources.
2. **Client Initialization**: Sets up HTTP clients for repository servers and OAuth2-enabled clients for MCMP.
3. **Concurrent Processing**: Launches parallel workers for each enabled repository source.
4. **HTML Parsing**: Fetches the directory listing and extracts links pointing to sub-directories (repositories). Hidden directories (e.g. `.repostatus/`) are skipped.
5. **Lock Status**: Fetches `<RepoUrl>/<StatusPath>` (default `.repostatus/locked.json`) and sets the `status` of every repository (see [Lock Status](#lock-status)). If the file can't be read (other than 404), the run is aborted so that no repository is unlocked by accident.
6. **Metadata Injection**: Injects version, commit ID, and execution statistics into the data model.
7. **Persistence & Transmission**: Saves results locally as `repo_<identifier>.json` and concurrently sends them to all configured MCMP instances.

## Documentation

### Requirements

- [Go](https://go.dev/) >= 1.26
- Network connectivity to configured repository URLs (HTTP/HTTPS)
- OAuth2 client credentials for the MCMP backend

### Configuration

Configuration is managed via a TOML file (default: `mcmp-eai-repo.toml`).

#### Section Overview
- **[LOGGING]**: Controls log levels (DEBUG, INFO, etc.), output destination (file/console), and rotation settings.
- **[[MCMP]]**: A list of MCMP backend instances including OAuth2 credentials and API endpoints.
- **[[REPO]]**: A list of repository servers to scan.
  - `Enabled`: Flag to activate/deactivate the source.
  - `RepoUrl`: The base URL of the repository server.
  - `StatusPath` (optional): Path of the lock status file relative to `RepoUrl`. Default: `.repostatus/locked.json`.

### Execution

#### Build
```bash
go build -o mcmp-eai-repo main.go
```

#### Run
```bash
./mcmp-eai-repo
```

## Architecture

```
mcmp-eai-repo/
├── pkg/
│   ├── client/
│   │   └── source/      # Implementation of the DataSource interface
│   └── processor/       # Business logic for parsing repository listings
├── main.go              # Application bootstrap and configuration mapping
└── README.md
```

### Key Components

- **Processor**: Handles the logic for parsing Apache-style HTML listings and converting them into `RepositoryInfo` objects.
- **Source**: Acts as the bridge to the `mcmp-eai-common` framework.
- **Common Library**: Utilizes centralized functions for logging, locking, and the standardized EAI runtime.

## Data Model

### Repo Export Format
The exported JSON body follows this schema:

```json
{
  "metadata": {
    "name": "mcmp-eai-repo",
    "version": "1.0.0",
    "status": "SUCCESS",
    "fqdn": "server.example.com",
    "duration": "450ms"
  },
  "repositories": [
    {
      "name": "rhel-9-appstream",
      "url": "https://repo.example.com/rhel-9-appstream/",
      "status": "OPEN"
    },
    {
      "name": "rhel-9-baseos",
      "url": "https://repo.example.com/rhel-9-baseos/",
      "status": "SELF_ONLY"
    }
  ]
}
```

### Lock Status
The lock status of the repositories is read from `<RepoUrl>/<StatusPath>` (default `.repostatus/locked.json`):

```json
{
  "LOCKED": ["repo-that-is-locked"],
  "SELF_ONLY": ["rhel-9-baseos"]
}
```

| Status      | Meaning                                                                                  |
|-------------|------------------------------------------------------------------------------------------|
| `LOCKED`    | The repository can't be changed or attached in MCMP.                                     |
| `SELF_ONLY` | The repository can only be attached by its owners (change group of its appservice).      |
| `OPEN`      | No restrictions. Repositories that aren't listed in the file are `OPEN`.                 |

If a repository is listed under both keys, `LOCKED` wins. A missing file (404) means all repositories are `OPEN`.

## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [mcmp-eai-repo](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-repo)

<p align="right">(<a href="#top">back to top</a>)</p>