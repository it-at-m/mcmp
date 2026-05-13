package mcmp

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
)

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
}

// New creates a new MCMP client with database connection
// Parameters:
//   - username: database username
//   - password: database password
//   - dsn: data source name for database connection
//   - debug: enable debug logging
//   - logWriter: io.Writer for database logs (optional, uses os.Stdout if nil)
func New(username, password, dsn string, debug bool, logWriter io.Writer) (*Client, error) {
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
	db, err := gorm.Open(postgres.Open(DSN), &gorm.Config{
		Logger:      gormLogger,
		PrepareStmt: false,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to open Postgres connection: %w", err)
	}

	sqlDB, err := db.DB()
	if err != nil {
		return nil, fmt.Errorf("failed to get SQL DB instance: %w", err)
	}
	if err := sqlDB.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}
	return &Client{
		db: db,
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

// UpdateJob updates an existing job record in the database with the provided job object and sets the current timestamp.
// Returns an error if the database client is uninitialized or the update operation fails.
func (c *Client) UpdateJob(job *Job) error {
	if job == nil {
		return errors.New("job cannot be nil")
	}
	return c.retryOperation(func() error {
		job.UpdatedAt = time.Now()
		return c.db.Model(job).Save(job).Error
	})
}
