package db

import (
	"errors"
	"fmt"
	"io"
	"log"
	"net/url"
	"os"
	"strings"
	"time"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const (
	defaultSlowQueryThreshold = 1000 * time.Millisecond
	defaultMaxRetries         = 3
	defaultInitialBackoff     = 100 * time.Millisecond
	defaultMaxBackoff         = 5 * time.Second
	defaultBaseDelay          = 100 * time.Millisecond
)

var berlinLocation *time.Location

// RetryConfig defines the retry behavior for transient database errors
type RetryConfig struct {
	MaxRetries     int
	InitialBackoff time.Duration
	MaxBackoff     time.Duration
}

// Client represents a database client for MCMP operations
type Client struct {
	db          *gorm.DB
	retryConfig RetryConfig
	passphrase  string
}

func init() {
	loc, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		panic(fmt.Sprintf("failed to load time location Europe/Berlin: %v", err))
	}
	berlinLocation = loc
}

// New creates a new MCMP client with database connection
// Parameters:
//   - username: database username
//   - password: database password
//   - dsn: data source name for database connection
//   - passphrase: encryption key for PGP operations
//   - debug: enable debug logging
//   - logWriter: io.Writer for database logs (optional, uses os.Stderr if nil and debug is true)
func New(username, password, dsn, passphrase string, debug bool, logWriter io.Writer) (*Client, error) {
	if strings.TrimSpace(username) == "" {
		return nil, errors.New("username cannot be empty")
	}
	if strings.TrimSpace(dsn) == "" {
		return nil, errors.New("dsn cannot be empty")
	}
	escapedUsername := url.QueryEscape(username)
	gormLogLevel := logger.Warn
	if debug {
		gormLogLevel = logger.Info
	}

	// Use provided logWriter or disable GORM logging if not needed
	var gormLogger logger.Interface
	if debug && logWriter != nil {
		gormLogger = logger.New(
			log.New(logWriter, "[GORM] ", log.LstdFlags),
			logger.Config{
				SlowThreshold:             defaultSlowQueryThreshold,
				LogLevel:                  gormLogLevel,
				IgnoreRecordNotFoundError: true,
				Colorful:                  false, // Disable colors for structured logging
			},
		)
	} else if debug {
		// Fallback to stderr if no writer provided but debug is enabled
		gormLogger = logger.New(
			log.New(os.Stderr, "[GORM] ", log.LstdFlags),
			logger.Config{
				SlowThreshold:             defaultSlowQueryThreshold,
				LogLevel:                  gormLogLevel,
				IgnoreRecordNotFoundError: true,
				Colorful:                  false,
			},
		)
	} else {
		// Disable GORM logging completely if debug is off
		gormLogger = logger.New(
			log.New(io.Discard, "", 0),
			logger.Config{
				LogLevel: logger.Silent,
			},
		)
	}
	DSN := fmt.Sprintf("%s user=%s password=%s", dsn, escapedUsername, password)
	database, err := gorm.Open(postgres.Open(DSN), &gorm.Config{
		Logger:      gormLogger,
		PrepareStmt: false,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to open Postgres connection: %w", err)
	}

	sqlDB, err := database.DB()
	if err != nil {
		return nil, fmt.Errorf("failed to get SQL DB instance: %w", err)
	}
	if err := sqlDB.Ping(); err != nil {
		_ = sqlDB.Close()
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}
	return &Client{
		db:         database,
		passphrase: passphrase,
		retryConfig: RetryConfig{
			MaxRetries:     defaultMaxRetries,
			InitialBackoff: defaultInitialBackoff,
			MaxBackoff:     defaultMaxBackoff,
		},
	}, nil
}

// isTransientError checks if an error is a transient database error that should be retried
func isTransientError(err error) bool {
	if err == nil {
		return false
	}

	errorMsg := strings.ToLower(err.Error())

	// Transiente PostgreSQL-Fehler
	transientErrors := []string{
		"connection refused",
		"connection reset",
		"broken pipe",
		"timeout",
		"too many connections",
		"deadlock detected",
		"could not serialize access",
		"terminating connection due to administrator command",
		"the database system is starting up",
		"the database system is shutting down",
		"cannot execute",
		"server closed the connection unexpectedly",
		"no connection to the server",
		"could not receive data from server",
	}

	for _, transientErr := range transientErrors {
		if strings.Contains(errorMsg, transientErr) {
			return true
		}
	}

	return false
}

// retryOperation executes a database operation with exponential backoff retry logic
func (c *Client) retryOperation(operation func() error) error {
	var lastErr error
	backoff := c.retryConfig.InitialBackoff

	for attempt := 0; attempt <= c.retryConfig.MaxRetries; attempt++ {
		err := operation()
		if err == nil {
			return nil
		}

		lastErr = err

		// Kein Retry bei nicht-transienten Fehlern
		if !isTransientError(err) {
			return err
		}

		// Letzter Versuch - kein weiteres Warten
		if attempt == c.retryConfig.MaxRetries {
			break
		}

		// Exponentielles Backoff wird von der aufrufenden Anwendung geloggt
		time.Sleep(backoff)

		// Backoff verdoppeln, aber MaxBackoff nicht überschreiten
		backoff *= 2
		if backoff > c.retryConfig.MaxBackoff {
			backoff = c.retryConfig.MaxBackoff
		}
	}

	return fmt.Errorf("operation failed after %d retries: %w", c.retryConfig.MaxRetries, lastErr)
}

// FindJobByID retrieves a Job record from the database by its ID.
// Returns a pointer to the Job record and an error if the operation fails.
// Automatically retries on transient database errors with exponential backoff.
func (c *Client) FindJobByID(id int64) (*Job, error) {
	var job Job
	err := c.retryOperation(func() error {
		err := c.db.Where("id = ?", id).First(&job).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return err // Kein Retry für RecordNotFound
		}
		return err
	})

	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, fmt.Errorf("job with ID %d not found", id)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load job entry with ID %d: %w", id, err)
	}
	return &job, nil
}

// Close closes the underlying database connection and releases any resources held by the client. Returns an error if any occurs.
func (c *Client) Close() error {
	sqlDB, err := c.db.DB()
	if err != nil {
		return fmt.Errorf("failed to get SQL DB instance: %w", err)
	}
	return sqlDB.Close()
}

// GetDB returns the underlying gorm.DB instance associated with the Client.
func (c *Client) GetDB() *gorm.DB {
	return c.db
}

// UpdateJob updates an existing job record. It implements a reload-and-merge strategy
// to handle concurrent updates from different processes safely.
func (c *Client) UpdateJob(job *Job) error {
	if job == nil {
		return errors.New("job cannot be nil")
	}

	return c.retryOperation(func() error {
		// 1. Reload latest state from DB to get current Version/status
		var freshJob Job
		if err := c.db.First(&freshJob, job.ID).Error; err != nil {
			return fmt.Errorf("failed to reload job %d: %w", job.ID, err)
		}

		// 2. Prepare the update timestamp
		job.UpdatedAt = time.Now().In(berlinLocation)

		// 3. Perform the update using the job struct's values.
		// We use Model(&freshJob) to ensure GORM uses the latest known state (like Version)
		// but apply the values from our 'job' argument.
		// Omit ensures we don't touch complex associations that might be stale.
		err := c.db.Model(&freshJob).
			Omit("ID", "Snow", "Awx", "User", "Server", "Appservice", "CreatedAt").
			Updates(job).Error
		if err != nil {
			return err
		}

		// 4. Update the local object so the caller has the new state (e.g. UpdatedAt)
		*job = freshJob
		return nil
	})
}

// FindJobIncidentByID retrieves a JobIncident record from the database by its ID.
// Returns a pointer to the JobIncident record and an error if the operation fails.
// Automatically retries on transient database errors with exponential backoff.
func (c *Client) FindJobIncidentByID(id int64) (*JobIncident, error) {
	var incident JobIncident
	err := c.retryOperation(func() error {
		err := c.db.Where("id = ?", id).First(&incident).Error
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		}
		return err
	})

	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, fmt.Errorf("job incident with ID %d not found", id)
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load job incident entry with ID %d: %w", id, err)
	}
	return &incident, nil
}

// UpdateJobIncident updates an existing job incident record in the database with the provided
// job incident object and sets the current timestamp.
// Returns an error if the database client is uninitialized or the update operation fails.
func (c *Client) UpdateJobIncident(incident *JobIncident) error {
	if incident == nil {
		return errors.New("incident cannot be nil")
	}
	return c.retryOperation(func() error {
		incident.UpdatedAt = time.Now()
		return c.db.Model(incident).Save(incident).Error
	})
}

// SaveJobIncident creates a new job incident record in the database.
func (c *Client) SaveJobIncident(incident *JobIncident) error {
	if incident == nil {
		return errors.New("incident cannot be nil")
	}
	return c.retryOperation(func() error {
		return c.db.Create(incident).Error
	})
}

// GetAllConfigAwx loads all config_awx entries and decrypts the passwords
func (c *Client) GetAllConfigAwx() ([]ConfigAwx, error) {
	var results []ConfigAwx

	query := `
		SELECT 
			id,
			api_description,
			api_username,
			api_password_encrypted,
			api_endpoint,
			enabled,
			pgp_sym_decrypt(api_password_encrypted, ?) as api_password
		FROM cmp.config_awx
		ORDER BY id
	`

	rows, err := c.db.Raw(query, c.passphrase).Rows()
	if err != nil {
		return nil, fmt.Errorf("error loading config_awx entries: %w", err)
	}
	defer rows.Close()

	for rows.Next() {
		var result ConfigAwx
		var apiPasswordDecrypted *string

		err := rows.Scan(
			&result.ID,
			&result.ApiDescription,
			&result.ApiUsername,
			&result.ApiPassword,
			&result.ApiEndpoint,
			&result.Enabled,
			&apiPasswordDecrypted,
		)
		if err != nil {
			return nil, fmt.Errorf("error scanning config_awx row: %w", err)
		}

		if apiPasswordDecrypted != nil {
			result.ApiPassword = *apiPasswordDecrypted
		}

		results = append(results, result)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating over config_awx rows: %w", err)
	}

	return results, nil
}

// GetAllConfigSnow loads all config_snow entries and decrypts the client secrets
func (c *Client) GetAllConfigSnow() ([]ConfigSnow, error) {
	var results []ConfigSnow

	query := `
		SELECT
			id,
			api_description,
			api_client_id,
			api_client_auth_url,
			api_endpoint,
			enabled,
			proxy,
			use_proxy,
			pgp_sym_decrypt(api_client_secret_encrypted, ?) as api_client_secret
		FROM cmp.config_snow
		ORDER BY id
	`

	rows, err := c.db.Raw(query, c.passphrase).Rows()
	if err != nil {
		return nil, fmt.Errorf("error loading config_snow entries: %w", err)
	}
	defer rows.Close()

	for rows.Next() {
		var result ConfigSnow
		var apiClientSecretDecrypted *string

		err := rows.Scan(
			&result.ID,
			&result.ApiDescription,
			&result.ApiClientID,
			&result.ApiClientAuthUrl,
			&result.ApiEndpoint,
			&result.Enabled,
			&result.Proxy,
			&result.UseProxy,
			&apiClientSecretDecrypted,
		)
		if err != nil {
			return nil, fmt.Errorf("error scanning config_snow row: %w", err)
		}

		if apiClientSecretDecrypted != nil {
			result.ApiClientSecret = *apiClientSecretDecrypted
		}

		results = append(results, result)
	}

	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterating over config_snow rows: %w", err)
	}

	return results, nil
}

// GetJobsByStatus loads all job entries with the specified statuses
func (c *Client) GetJobsByStatus() ([]*Job, error) {
	var jobs []*Job

	// Define the desired statuses
	statuses := []JobStatus{
		JobStatusSuccessful,
		JobStatusFailed,
		JobStatusError,
		JobStatusCanceled,
		JobStatusRejected,
	}

	err := c.db.Where("status not in ?", statuses).
		Preload("Snow").
		Preload("Awx").
		Preload("User").
		Preload("Server").
		Preload("Appservice").
		Preload("Appservice.ChangeGroup").
		Order("is_low_priority ASC, id ASC").
		Find(&jobs).Error
	if err != nil {
		return nil, fmt.Errorf("error loading job entries: %w", err)
	}

	return jobs, nil
}

func (c *Client) FindServerByInstanceUUID(uuid string) ([]Server, error) {
	var servers []Server
	err := c.db.Where("instance_uuid = ?", uuid).Order("created_at desc").Find(&servers).Error
	if err != nil {
		return nil, fmt.Errorf("error searching for a server with instance UUID %s: %w", uuid, err)
	}
	return servers, nil
}

func (c *Client) UpdateServer(server *Server) error {
	if c.db == nil {
		return fmt.Errorf("no DB client available")
	}

	snowServerSysID := server.SnowServerSysID
	snowServerSysClass := server.SnowServerSysClass
	snowInstanceSysID := server.SnowInstanceSysID
	snowInstanceSysClass := server.SnowInstanceSysClass

	return c.retryOperation(func() error {
		server.UpdatedAt = time.Now().In(berlinLocation)
		server.Version += 1

		err := c.db.Model(server).
			Select("snow_server_sys_id", "snow_server_sys_class", "snow_instance_sys_id", "snow_instance_sys_class", "updated_at", "version").
			Updates(map[string]interface{}{
				"snow_server_sys_id":      snowServerSysID,
				"snow_server_sys_class":   snowServerSysClass,
				"snow_instance_sys_id":    snowInstanceSysID,
				"snow_instance_sys_class": snowInstanceSysClass,
				"updated_at":              server.UpdatedAt,
				"version":                 server.Version,
			}).Error
		if err != nil {
			var freshServer Server
			if reloadErr := c.db.First(&freshServer, server.ID).Error; reloadErr == nil {
				freshServer.SnowServerSysID = snowServerSysID
				freshServer.SnowServerSysClass = snowServerSysClass
				freshServer.SnowInstanceSysID = snowInstanceSysID
				freshServer.SnowInstanceSysClass = snowInstanceSysClass
				*server = freshServer
			}
			return err
		}

		return nil
	})
}

func (c *Client) SaveServerAssignment(server *ServerAssignment) error {
	if c.db != nil {
		err := c.db.Create(server).Error
		if err != nil {
			return fmt.Errorf("error saving server assignment: %w", err)
		}
	} else {
		log.Printf("No DB client\n")
	}
	return nil
}

// SaveJobNode creates a new JobNode entry in the database
func (c *Client) SaveJobNode(node *JobNode) error {
	if c.db == nil {
		return fmt.Errorf("no DB client available")
	}

	err := c.db.Create(node).Error
	if err != nil {
		return fmt.Errorf("error creating job node: %w", err)
	}
	return nil
}

// UpdateJobNode updates an existing JobNode entry with retry logic
func (c *Client) UpdateJobNode(node *JobNode) error {
	if c.db == nil {
		return fmt.Errorf("no DB client available")
	}

	return c.retryOperation(func() error {
		node.UpdatedAt = time.Now().In(berlinLocation)

		// Omit ID and CreatedAt from updates to prevent accidental changes
		err := c.db.Model(node).Omit("ID", "CreatedAt").Updates(node).Error
		if err != nil {
			// Reload the node to ensure we have the correct state for the next attempt
			var freshNode JobNode
			if reloadErr := c.db.First(&freshNode, node.ID).Error; reloadErr == nil {
				// Re-apply original updates if needed, though here we update the whole struct
				*node = freshNode
			}
			return err
		}

		return nil
	})
}
