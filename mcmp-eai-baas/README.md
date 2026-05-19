<div id="top"></div>

## About The Project

**mcmp-eai-baas** is an Enterprise Application Integration (EAI) service that synchronizes backup data from various Backup-as-a-Service (BaaS) systems with the Munich Cloud Management Platform (MCMP). The application automatically retrieves backup information from multiple BaaS providers, processes the data, and stores it in a centralized database for monitoring and management purposes.

### Key Features

- **Multi-BaaS Integration**: Connects to multiple BaaS systems simultaneously
- **Backup Data Synchronization**: Retrieves and processes backup catalogs from BaaS providers
- **Database Storage**: Stores backup metadata in PostgreSQL/MySQL database
- **Incremental Updates**: Efficiently compares and updates only changed backup records
- **Concurrent Processing**: Processes multiple virtual machines concurrently with configurable limits
- **Comprehensive Logging**: Configurable debug logging for troubleshooting and monitoring
- **Password Encryption**: Secure storage of database credentials with encrypted passwords

### Data Synchronization Flow

The application follows this data synchronization workflow:

1. **Configuration Loading**: Reads TOML configuration file with BaaS connections and database settings
2. **BaaS Data Retrieval**: Fetches backup catalogs from configured BaaS systems
3. **Data Processing**: Transforms backup data into standardized format
4. **Database Operations**: Performs efficient batch insert/update operations
5. **Change Detection**: Compares existing records with new data to minimize database operations

### Backup Types Supported

The application processes multiple backup types from BaaS systems:

- **VM**: Virtual Machine backups
- **Agent**: Agent-based backups
- **Database Types**: DA, DB, DH, DM, DP, DS, DY
- **File Systems**: NFS, CIFS

### Built With

This project leverages modern Go technologies and enterprise integration patterns:

- **Go** 1.24.2 - Primary programming language
- **GORM** - Object-Relational Mapping and database operations
- **Viper** - Configuration management and TOML parsing
- **PostgreSQL/MySQL** - Database storage for backup metadata

## Documentation

### Requirements

- [Go](https://go.dev/) >= v1.24.2
- PostgreSQL or MySQL database
- Network connectivity to BaaS API endpoints
- Valid SSL certificates for BaaS connections

### Installation

#### 1. Clone the Repository
```bash
git clone https://github.com/it-at-m/mcmp.git
cd mcmp/mcmp-eai-baas
```

#### 2. Install Dependencies
```bash
go mod tidy
```

#### 3. Build the Application
```bash
go build
```

This creates an executable named `mcmp-eai-baas` (or `mcmp-eai-baas.exe` on Windows).

### Configuration

#### 1. Create Configuration File
```bash
cp mcmp-eai-baas.toml.example mcmp-eai-baas.toml
```

#### 2. Edit Configuration
```bash
vim mcmp-eai-baas.toml
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
DSN = 'unix(/var/lib/mysql/mysql.sock)/ucs?charset=utf8mb4&parseTime=True&loc=Local'
Username = 'example'
EncryptedPassword = 'a889402cee571f4cf218e01359df5e496b2bb736993e64e531d53b07dc0ae2d38359eba159700d10da6ac892'
```

##### BaaS Systems Configuration
```toml
[[BAAS]]
Enabled = true
Fqdn = 'dev-baas.example.com'
Cloud = 'vcenterc.example.com'

[[BAAS]]
Enabled = true 
Fqdn = 'test-baas.example.com'
Cloud = 'vcenterk.example.com'
```

### Database Setup

#### 1. Initialize Database Tables
```bash
./mcmp-eai-baas init
```

This command creates the following tables:
- **cloud**: Stores cloud environment information
- **server**: Stores virtual machine/server information
- **backup**: Stores backup metadata and history

#### 2. Encrypt Database Password
```bash
./mcmp-eai-baas crypt-password
```

Use this command to generate encrypted passwords for the configuration file.

### Execution

#### Standard Operation
```bash
./mcmp-eai-baas
```

This command executes the complete backup synchronization workflow:
1. Loads configuration from `mcmp-eai-baas.toml`
2. Connects to each enabled BaaS system
3. Retrieves backup catalogs for all configured virtual machines
4. Processes and stores backup data in the database
5. Performs incremental updates to minimize database operations

#### Debug Mode
Enable debug logging by setting `Debug = true` in the configuration file to see detailed processing information.

### Database Schema

The application uses three main database tables:

#### Cloud Table
```sql
CREATE TABLE cloud (
    id SERIAL PRIMARY KEY,
    fqdn VARCHAR(100) NOT NULL UNIQUE
);
```

#### Server Table
```sql
CREATE TABLE server (
    id SERIAL PRIMARY KEY,
    cloud_id INTEGER NOT NULL REFERENCES cloud(id) ON DELETE CASCADE,
    uuid VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    UNIQUE(cloud_id, uuid)
);
```

#### Backup Table
```sql
CREATE TABLE backup (
    id SERIAL PRIMARY KEY,
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    server_id INTEGER NOT NULL REFERENCES server(id) ON DELETE CASCADE,
    backup_type backup_type_enum NOT NULL,
    backup_server VARCHAR(100) NOT NULL,
    client_server VARCHAR(100) NOT NULL,
    save_set_name VARCHAR(100) NOT NULL,
    save_time_string VARCHAR(50) NOT NULL,
    save_time TIMESTAMP NOT NULL,
    ssid VARCHAR(50) NOT NULL,
    clone_id VARCHAR(50) NOT NULL,
    pool VARCHAR(100) NOT NULL
);
```

## Architecture

The application is structured with clear separation of concerns:

```
pkg/
├── app/             # Application utilities and configuration
├── cipher/          # Password encryption/decryption
├── clients/
│   └── baas/        # BaaS API client
├── config/          # Configuration management
├── db/              # Database connection and operations
└── logging/         # Logging utilities
```

### Key Components

- **BaaS Client**: Handles HTTPS communication with BaaS API endpoints
- **Database Layer**: Manages PostgreSQL/MySQL connections and operations
- **Configuration Manager**: Handles TOML configuration parsing and password decryption
- **Worker System**: Concurrent processing of virtual machines with configurable limits
- **Change Detection**: Efficient comparison of backup records to minimize database operations

## Data Processing Example

The application processes backup data from BaaS systems and stores it in a normalized database format:

### Input: BaaS API Response
```json
{
  "status": 0,
  "message": "Success",
  "backups": {
    "count": 2,
    "catalog": {
      "vm": [
        {
          "backupserver": "backup01.example.com",
          "clientserver": "vm001.example.com",
          "savesetname": "vm001_full",
          "savetime": "2025-07-08 02:00:00",
          "ssid": "123456",
          "cloneid": "789012",
          "pool": "pool01"
        }
      ]
    }
  }
}
```

### Output: Database Records
The application transforms this data into structured database records with versioning, timestamps, and efficient indexing for fast queries and updates.

## Performance Considerations

- **Concurrent Processing**: Limited to 1 worker by default to avoid overwhelming BaaS systems
- **Batch Operations**: Database operations are performed in batches for efficiency
- **Transaction Management**: All database operations use transactions for consistency
- **Change Detection**: Only modified records are updated, reducing database load
- **Connection Pooling**: Efficient database connection management

## Contributing

Contributions are welcome and help improve the integration capabilities. To contribute:

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

Project Link: [https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-baas](https://github.com/it-at-m/mcmp/tree/main/mcmp-eai-baas)

<p align="right">(<a href="#top">back to top</a>)</p>
```