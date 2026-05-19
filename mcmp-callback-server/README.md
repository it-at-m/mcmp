<div id="top"></div>

## About The Project

**mcmp-callback-server** is a lightweight HTTP service that receives asynchronous callbacks (Change approvals and QuickDiscovery results) and updates job records in the MCMP PostgreSQL database in real time.

### Key Features

- Change approval callbacks: updates job ChangeStatus based on approval results
- QuickDiscovery callbacks: stores discovery outcome and CI identifiers (sys_id, name)
- Direct PostgreSQL integration via GORM with retry/backoff for transient errors
- Graceful shutdown on SIGINT/SIGTERM with configurable timeout
- Request logging (method, path, status, duration) and panic recovery middleware
- Strict input validation and 1 MB request body limit
- Clean JSON success responses

### Callback Processing Flow

1. Startup: load TOML configuration and establish DB connection
2. Receive POST callback at /callback/{type}/{job_id}
3. Validate URL, ID, method and JSON payload
4. Retrieve job by ID
5. Verify job state (waiting_for_approval for Change, waiting for QuickDiscovery)
6. Update status and optional metadata/error details
7. Persist changes to PostgreSQL (with retries on transient failures)
8. Respond with JSON containing the new status

### Built With

- Go 1.25
- GORM (PostgreSQL)
- Viper (TOML configuration)
- PostgreSQL
- JSON

## API

Endpoints:
- POST `/callback/change/{job_id}` — Change approval callback
- POST `/callback/quickdiscovery/{job_id}` — QuickDiscovery result callback

Request/response examples:

- Change callback

  Request approved:
```json
{
    "success": true,
    "error_message": "",
    "result": {
    "approval": "approved",
    "approval_set": "2025-10-08T05:32:19.000+0000Z",
    "approval_history": [
        {
            "sys_created_on": "2025-10-08T05:32:19.000+0000Z",
            "value": "Change Request has been approved by CMP Change Policy (Change Approval Policy Action)."
        },
        {
            "sys_created_on": "2025-10-08T05:32:18.000+0000Z",
            "value": "Die Gruppengenehmigung für Service: Hardware Server wurde vom Anwender Erika Mustermann genehmigt."
        },
        {
            "sys_created_on": "2025-10-08T05:32:16.000+0000Z",
            "value": "Erika Mustermann hat die Aufgabe genehmigt."
        },
        {
            "sys_created_on": "2025-10-08T05:22:31.000+0000Z",
            "value": "No Decisions matched. CMP Change Policy has been skipped (Change Approval Policy Action)."
        }
    ]
    }
}
```

Request rejected:
```json
{
    "success": false,
    "error_message": "Für den Change Request wurde keine Freigabe erteilt.",
    "result": {
    "approval": "rejected",
    "approval_set": "2025-10-08T13:37:14.000+0000Z",
    "approval_history": [
        {
            "sys_created_on": "2025-10-08T13:37:14.000+0000Z",
            "value": "Change Request has been rejected by CMP Change Policy (Change Approval Policy Action)."
        },
        {
            "sys_created_on": "2025-10-08T13:37:14.000+0000Z",
            "value": "Die Gruppengenehmigung für Service: Hardware Server wurde vom Anwender Erika Mustermann abgelehnt."
        },
        {
            "sys_created_on": "2025-10-08T13:37:12.000+0000Z",
            "value": "Erika Mustermann  hat die Aufgabe abgelehnt.\n\nFreigabeanmerkungen:\n08.10.2025 15:37:12 - Erika Mustermann (Kommentare)\nNicht berechtigt!\n\n"
        },
        {
            "sys_created_on": "2025-10-08T13:35:46.000+0000Z",
            "value": "No Decisions matched. CMP Change Policy has been skipped (Change Approval Policy Action)."
        }
    ]
    }
}
```

  Success response (200):
```json
{
    "status": "success",
    "job_id": 123,
    "new_status": "approved"
}
```
  Possible status codes: 200, 400, 404, 405, 500

- QuickDiscovery callback

  Request:
```json
{
    "success": true,
    "error_message": "",
    "result": {
        "ci_sysid": "abc123def456",
        "ci_name": "server01.example.com"
    }
}
```

  Success response (200):
```json
{
    "status": "success",
    "job_id": 123,
    "new_status": "successful"
}
```
  Possible status codes: 200, 400, 404, 405, 500

## Configuration

The application reads a TOML configuration file. Search order:
1. `$HOME/.mcmp-callback-server/mcmp-callback-server.toml`
2. `./mcmp-callback-server.toml` (current directory)
3. `/opt/lhm/mcmp-callback-server/mcmp-callback-server.toml`

Structure:
```
[GENERAL]
Debug = true        # enable verbose logging
Port  = 9005        # HTTP port

[DATABASE]
DSN      = "host=db.example port=5432 dbname=db_schema_name sslmode=disable"
Username = "db_username"
Password = "db_password"
```
## Requirements

- Go >= 1.25
- PostgreSQL database with MCMP schema
- Network connectivity for incoming HTTP callbacks
- Reverse proxy (e.g., Apache/Nginx) for HTTPS termination (recommended)

## Installation

1) Clone the repository
```bash
git clone https://github.com/it-at-m/mcmp.git
cd mcmp/mcmp-callback-server
```

2) Install dependencies
```bash
go mod tidy
```

3) Build
```bash
go build
```

This creates an executable named `mcmp-callback-server` (Windows: `mcmp-callback-server.exe`).

## Running

1) Provide configuration (see “Configuration”)
2) Start the server
```bash
./mcmp-callback-server
```

- The server listens on the configured port (default example: 9005)
- Graceful shutdown on SIGINT/SIGTERM (timeout 30s)
- Request logs include method, path, status and duration

## Example Requests

- Change approval:
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"success":true,"error_message":"","result":{"approval":"approved"}}' \
  http://localhost:9005/callback/change/123
```

- QuickDiscovery:
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"success":true,"error_message":"","result":{"ci_sysid":"abc123","ci_name":"server01.example.com"}}' \
  http://localhost:9005/callback/quickdiscovery/123
```

## Architecture

```
.
├── main.go                 # HTTP server, routing, handlers, middleware
└── pkg/
    ├── clients/
    │   └── mcmp/           # GORM models and DB client with retry/backoff
    └── config/             # Viper-based configuration loader
```

## Contributing

Contributions are welcome:

1. Open an issue (bug/enhancement)
2. Fork the repository
3. Create a feature branch: `git checkout -b feature/YourFeature`
4. Commit: `git commit -m "Add YourFeature"`
5. Push: `git push origin feature/YourFeature`
6. Open a Pull Request

Please follow Go best practices and include documentation/tests as appropriate.

## License

Distributed under the MIT License. See `LICENSE` for details.

## Contact

opensource@muenchen.de

Project Link: [https://github.com/it-at-m/mcmp/tree/main/mcmp-callback-server](https://github.com/it-at-m/mcmp/tree/main/mcmp-callback-server)

<p align="right">(<a href="#top">back to top</a>)</p>
