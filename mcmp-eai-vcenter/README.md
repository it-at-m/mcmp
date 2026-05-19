<div id="top"></div>

## About The Project

**mcmp-eai-vcenter** is an Enterprise Application Integration (EAI) service that synchronizes virtual machine and infrastructure data from VMware vCenter environments with the Munich Cloud Management Platform (MCMP). The application automatically retrieves VM information, performance metrics, and infrastructure details from multiple vCenter servers and stores them in a centralized database for monitoring and management purposes.

### Key Features

- **Multi-vCenter Integration**: Connects to multiple vCenter servers simultaneously
- **VM Data Synchronization**: Retrieves comprehensive virtual machine information including configurations, performance metrics, and resource usage
- **Infrastructure Monitoring**: Collects host system status, network configurations, and storage details
- **Performance Metrics Collection**: Gathers CPU, memory, disk, and network performance data with configurable intervals
- **Database Storage**: Stores VM metadata and metrics in PostgreSQL/MySQL database with normalized schema
- **Real-time Monitoring**: Supports real-time performance data collection with 20-second intervals
- **Comprehensive Logging**: Configurable debug logging for troubleshooting and monitoring
- **Password Encryption**: Secure storage of vCenter credentials with encrypted passwords

### Data Collection Features

The application collects comprehensive data from vCenter environments:

- **Virtual Machines**: Configuration, power state, resource allocation, guest OS information
- **Performance Metrics**: CPU usage, memory consumption, disk I/O, network statistics
- **Host Systems**: Hardware information, status, maintenance mode, connection state
- **Network Configuration**: Port groups, VLANs, network adapters
- **Storage Information**: Virtual disks, datastores, mount points
- **Snapshots**: VM snapshot information and management
- **Tags and Metadata**: vCenter tags and custom attributes

### Built With

This project leverages modern Go technologies and VMware integration libraries:

- **Go** 1.24.3 - Primary programming language
- **GORM** - Object-Relational Mapping and database operations
- **govmomi** - VMware vSphere API bindings for Go
- **Viper** - Configuration management and TOML parsing
- **PostgreSQL/MySQL** - Database storage for VM metadata and metrics

## Documentation

### Requirements

- [Go](https://go.dev/) >= v1.24.3
- PostgreSQL or MySQL database
- Network connectivity to vCenter servers
- Valid credentials for vCenter authentication
- vCenter Server 6.7 or later

### Installation

#### 1. Clone the Repository
```bash
git clone github.com/it-at-m/mcmp.git
cd webanwendung/mcmp-eai-vcenter
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-vcenter` (or `mcmp-eai-vcenter.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-vcenter.toml.example mcmp-eai-vcenter.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-vcenter.toml
```

Configure the following sections:

##### General Settings
```toml
[GENERAL]
Passphrase = 'enter a passphrase here with which all passwords will be encrypted'
Debug = false
```

##### Database Configuration
```toml
[DATABASE]
DSN = 'host=127.0.0.1 port=5432 dbname=cmp sslmode=disable TimeZone=UTC'
Username = 'example'
EncryptedPassword = 'a889402cee571f4cf218e01359df5e496b2bb736993e64e531d53b07dc0ae2d38359eba159700d10da6ac892'
```

##### vCenter Systems Configuration
```toml
[[VCENTER]]
Enabled = true
Fqdn = 'vcenter01.example.com'
Username = 'service-account@vsphere.local'
EncryptedPassword = 'encrypted_password_here'

[[VCENTER]]
Enabled = true
Fqdn = 'vcenter02.example.com'
Username = 'monitoring@vsphere.local'
EncryptedPassword = 'encrypted_password_here'
```

### Database Setup

#### 1. Initialize Database Tables
```bash
./mcmp-eai-vcenter init
```

This command creates the following tables:
- **cloud**: Stores vCenter environment information
- **server**: Stores virtual machine information and configurations
- **disk**: Stores virtual disk information
- **nic**: Stores network adapter information
- **mount_point**: Stores filesystem mount points
- **snapshot**: Stores VM snapshot information
- **port_group**: Stores network port group information
- **network**: Stores network configuration data
- **ipam**: Stores IP address management information

#### 2. Encrypt vCenter Password
```bash
./mcmp-eai-vcenter crypt-password
```

Use this command to generate encrypted passwords for the configuration file.

### Execution

#### Standard Operation
```bash
./mcmp-eai-vcenter
```

This command executes the complete vCenter synchronization workflow:
1. Loads configuration from `mcmp-eai-vcenter.toml`
2. Connects to each enabled vCenter server
3. Retrieves VM configurations and performance metrics
4. Collects host system information and status
5. Processes network and storage configurations
6. Stores all data in the database with versioning support

#### Debug Mode
Enable debug logging by setting `Debug = true` in the configuration file to see detailed processing information including API calls and data transformations.

### Database Schema

The application uses a comprehensive database schema to store vCenter data:

#### Cloud Table
Stores vCenter server information:
```sql
CREATE TABLE cloud (
    id SERIAL PRIMARY KEY,
    fqdn VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100),
    server_gui VARCHAR(100)
);
```

#### Server Table
Stores comprehensive VM information:
```sql
CREATE TABLE server (
    id SERIAL PRIMARY KEY,
    cloud_id INTEGER NOT NULL REFERENCES cloud(id) ON DELETE CASCADE,
    uuid VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    power_state VARCHAR(20) NOT NULL,
    memory_mb INTEGER NOT NULL,
    num_cpu INTEGER NOT NULL,
    overall_status VARCHAR(6) DEFAULT 'gray',
    -- ... additional fields for guest OS, tools, performance metrics
    UNIQUE(cloud_id, uuid)
);
```

#### Performance Metrics
The application collects detailed performance metrics:
- **CPU Metrics**: Usage percentage, MHz consumption, ready time, co-stop time
- **Memory Metrics**: Usage, active memory, balloon driver usage, swap rates
- **Disk Metrics**: Usage, latency, read/write performance
- **Network Metrics**: Packet rates, bandwidth utilization

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── app/             # Application utilities and configuration
├── cipher/          # Password encryption/decryption
├── clients/
│   └── vcenter/     # vCenter API client and metrics processing
├── config/          # Configuration management
├── db/              # Database connection and operations
└── logging/         # Logging utilities
```

### Key Components

- **vCenter Client**: Handles vSphere API communication using govmomi library
- **Metrics Processor**: Collects and processes performance data with statistical analysis
- **Database Layer**: Manages PostgreSQL/MySQL connections with GORM ORM
- **Configuration Manager**: Handles TOML configuration parsing and password decryption
- **Performance Engine**: Real-time performance data collection with 20-second intervals

## Performance Metrics Collection

The application supports comprehensive performance monitoring:

### Supported Metrics
- **CPU**: Usage percentage, MHz consumption, ready time, co-stop time, wait time, demand
- **Memory**: Usage percentage, active memory, balloon driver, swap in/out rates, consumed memory
- **Disk**: Usage average, maximum latency, read/write latency
- **Network**: Packet transmission rates, bandwidth utilization

### Data Aggregation
Performance data is collected and aggregated using statistical methods:
- Minimum, Maximum, Average values
- Quartile calculations (Q1, Median, Q3)
- Percentage conversion for normalized reporting
- Time-based aggregation (30-minute intervals)

## Data Processing Example

The application processes vCenter data and stores it in a normalized database format:

### Input: vCenter API Response
```go
// VM configuration from vCenter
vm := mo.VirtualMachine{
    Name: "web-server-01",
    Config: &types.VirtualMachineConfigInfo{
        Uuid: "50012345-1234-1234-1234-123456789012",
        Hardware: types.VirtualHardware{
            NumCPU:   4,
            MemoryMB: 8192,
        },
    },
    Runtime: &types.VirtualMachineRuntimeInfo{
        PowerState: types.VirtualMachinePowerStatePoweredOn,
    },
}
```

### Output: Database Records
The application transforms this data into structured database records with comprehensive metadata, versioning, and performance metrics for efficient querying and monitoring.

## Performance Considerations

- **Concurrent Processing**: Optimized for multiple vCenter connections
- **Batch Operations**: Database operations are performed in batches for efficiency
- **Transaction Management**: All database operations use transactions for consistency
- **Connection Pooling**: Efficient database and vCenter connection management
- **Memory Management**: Proper cleanup of vCenter sessions and view objects
- **API Rate Limiting**: Respectful API usage to avoid overwhelming vCenter servers

## Contributing

Contributions are welcome and help improve the vCenter integration capabilities. To contribute:

1. **Report Issues**: Open an issue with detailed description and steps to reproduce
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

Project Link: [https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-vcenter](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-vcenter)

<p align="right">(<a href="#top">back to top</a>)</p>