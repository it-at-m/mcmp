<div id="top"></div>

## About The Project

**mcmp-eai-ucs** is an Enterprise Application Integration (EAI) service that synchronizes Cisco UCS (Unified Computing System) infrastructure data with the Munich Cloud Management Platform (MCMP). The application supports both **UCS Manager (UCSM)** for blade systems and **Cisco Integrated Management Controller (CIMC)** for rack-mount servers. It automatically retrieves hardware inventory, server configurations, and operational states, then transforms and forwards this data to MCMP through secure API calls.

### Key Features

- **Cisco UCS Integration**: Comprehensive support for both UCS Manager (XML API) and CIMC.
- **Hardware Inventory**: Synchronizes details for Blade Servers and Rack-Unit Servers (Model, Serial, CPU, Memory, etc.).
- **Multi-Source Support**: Configurable connections to multiple UCSM and CIMC instances simultaneously.
- **MCMP API Integration**: Securely transmits processed data to multiple MCMP endpoints via OAuth2.
- **Operational State Monitoring**: Tracks power states and overall server health.
- **Comprehensive Logging**: Structured logging with file rotation and configurable log levels.
- **Data Export**: Creates local JSON backups (`ucs_export_<hostname>.json`) of synchronized data.
- **Single-Instance Lock**: Prevents concurrent executions using file-based locking.

### Data Synchronization Flow

1. **Configuration Loading**: Reads TOML configuration for UCS systems and MCMP endpoints.
2. **UCS Data Retrieval**:
    - Authenticates against the UCS XML API.
    - Resolves classes for `computeBlade` and `computeRackUnit`.
    - Extracts hardware attributes (Inventory, CPU topology, Memory, Manufacturing time).
3. **Data Processing**: Transforms UCS-specific attributes into the standardized MCMP Cloud/Server data model.
4. **Data Transmission**: Sends processed JSON data to all configured MCMP API endpoints.
5. **Local Backup**: Saves the exported data to local JSON files for audit and troubleshooting.

### Configuration Structure

The application uses a TOML configuration file (`mcmp-eai-ucs.toml`) with the following sections:

#### Logging Configuration

- **Level**: DEBUG, INFO, WARN, ERROR.
- **Output**: console, file, or both.
- **Format**: text, json, or plain.

#### UCSM / CIMC Configuration (Arrays)

- **Hostname**: Management IP or FQDN of the UCS Manager or CIMC.
- **Username / Password**: API credentials.
- **Enabled**: Toggle for specific sources.
- **VerifyTLS**: SSL certificate verification setting.

#### MCMP API Configuration (Array)

- **OAuthUrl / OAuthRealm**: Keycloak authentication settings.
- **OAuthClientId / OAuthClientSecret**: OAuth2 credentials.
- **ApiEndpoint**: MCMP API endpoint for UCS data import.

### Built With

- **Go** 1.26 - Primary programming language.
- **Cisco UCS XML API** - Integration interface for hardware data.
- **Viper** - Configuration management.
- **mcmp-eai-common** - Shared library for EAI lifecycle, logging, and networking.

## Documentation

### Requirements

- [Go](https://go.dev/) >= v1.26
- Network access to UCS Manager / CIMC XML API (HTTPS).
- Connectivity to MCMP API and Keycloak.

### Installation

#### 1. Clone the Repository

```bash
git clone https://github.com/it-at-m/mcmp.git
cd mcmp/mcmp-eai-ucs
```

#### 2. Install Dependencies

```bash
go mod tidy
```

#### 3. Build the Application

```bash
go build
```

### Configuration

#### 1. Create Configuration File

```bash
cp mcmp-eai-ucs.toml.example mcmp-eai-ucs.toml
```

#### 2. Edit Configuration

Configure your `UCSM`, `CIMC`, and `MCMP` sections in `mcmp-eai-ucs.toml`.

### Execution

#### Standard Operation

```bash
./mcmp-eai-ucs
```

#### Debug Mode

Set `Level = "DEBUG"` in the `[LOGGING]` section of the configuration file.

## Architecture

```text
mcmp-eai-ucs/
├── pkg/
│   ├── client/
│   │   ├── source/    # EAI DataSource implementation
│   │   └── ucs/       # Cisco UCS XML API client
│   └── processor/     # Logic for mapping UCS data to MCMP models
├── main.go            # Entry point
```

## Contributing

Contributions are welcome. Please ensure your code follows Go best practices and includes appropriate documentation.

## License

Distributed under the MIT License. See `LICENSE` file for more information.

## Contact

it@M - opensource@muenchen.de

Project Link: [https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-ucs](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-ucs)

<p align="right">(<a href="#top">back to top</a>)</p>
