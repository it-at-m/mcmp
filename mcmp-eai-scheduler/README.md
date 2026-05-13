<div id="top"></div>

## About The Project

**mcmp-eai-scheduler** is a central orchestration service for the Munich Cloud Management Platform (MCMP). It acts as a scheduler and execution engine that processes jobs defined in the MCMP database, coordinating actions across multiple systems including ServiceNow, AWX (Ansible Tower), and Foreman.

### Key Features

- **Job Orchestration**: Fetches and processes jobs from the MCMP database based on their status.
- **ServiceNow Integration**:
    - **Change Management**: Automatically creates and manages Normal and Standard Change requests.
    - **CMDB Integration**: Triggers Quick Discovery and applies tags to Configuration Items (CIs).
- **AWX Integration**: Launches and monitors Job Templates and Workflow Job Templates.
- **Foreman Integration**: Retrieves host details for tagging and verification.
- **Notification System**: Sends email notifications for specific job events (e.g., Non-Postgres installations).
- **SIEM Logging**: Integrated logging for security and audit trails.

### Job Processing Flow

The application runs as a process that performs the following workflow:

1. **Configuration Loading**: Reads local TOML configuration and fetches system configurations (AWX, ServiceNow) dynamically from the MCMP database.
2. **Job Retrieval**: Queries the MCMP database for jobs in various states (New, Waiting, Running).
3. **Process Logic**:
- **Change Management**: Checks if a change ticket is required and handles its creation/approval flow.
- **AWX Execution**: Triggers Ansible automation in AWX and polls for completion.
- **Quick Discovery**: Triggers ServiceNow discovery for new deployments.
- **Tagging**: Updates CI tags in ServiceNow based on Foreman data.
4. **Status Updates**: Updates job statuses in the MCMP database (e.g., from `JobStatusNew` to `JobStatusAwxRunning` to `JobStatusSuccessful`).

### Configuration Structure

The application uses a TOML configuration file (`mcmp-eai-scheduler.toml`) with the following sections:

#### General Settings
- **Debug**: Enable verbose logging for development and troubleshooting.
- **CallbackUrlChange**: URL for ServiceNow to callback regarding Change status updates.
- **CallbackUrlQuickDiscovery**: URL for ServiceNow to callback regarding Discovery status updates.

#### Database (MCMP)
- **DSN**: Database connection string.
- **Username/Password**: Database credentials.
- **Passphrase**: Encryption passphrase for secure data.

#### Foreman Configuration
- **ApiEndpoint**: Foreman API endpoint.
- **Username/Password**: Credentials for Foreman access.

#### SMTP Configuration
- **Server/Port**: SMTP server details.
- **Username/Password**: SMTP credentials.
- **To/CC**: Default recipients for notifications.

#### SIEM Logging
- **Enabled**: Toggle SIEM logging.
- **Syslog/File**: Configuration for syslog target and local log files.

*Note: Credentials and endpoints for AWX and ServiceNow are loaded from the MCMP database, not this local configuration file.*

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.25 - Primary programming language
- **Viper** - Configuration management and TOML parsing
- **PostgreSQL** - Primary data store (via MCMP client)
- **REST APIs** - Communication with ServiceNow, AWX, and Foreman

## Documentation

The ServiceNow API is documented here: https://git.muenchen.de/servicenow/cmp-api-spec

### Requirements

- [Go](https://go.dev/) >= v1.25
- [Viper](https://github.com/spf13/viper)
- Access to MCMP Database
- Connectivity to ServiceNow, AWX, and Foreman instances

### Installation

#### 1. Clone the Repository
```bash
git clone git.muenchen.de/mcmp/webanwendung/mcmp-eai-scheduler.git
cd mcmp-eai-scheduler
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-scheduler` (or `mcmp-eai-scheduler.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-scheduler.toml.example mcmp-eai-scheduler.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-scheduler.toml
```

Configure the sections as described in the Configuration Structure section.

### Execution

#### Standard Operation
```bash
./mcmp-eai-scheduler
```

This command executes the complete job synchronization and orchestration workflow.

#### Debug Mode
Enable debug logging by setting `Debug = true` in the configuration file under the `GENERAL` section.

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── clients/
│   ├── foreman/     # Foreman API client
│   ├── logging/     # SIEM and general logging utilities
│   ├── mail/        # SMTP mail client
│   ├── mcmp/        # MCMP Database client & entities
│   └── snow/        # ServiceNow API client
├── processor/       # Data processing logic
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

Project Link: [git.muenchen.de/mcmp/webanwendung/mcmp-eai-scheduler](git.muenchen.de/mcmp/webanwendung/mcmp-eai-scheduler)

<p align="right">(<a href="#top">back to top</a>)</p>