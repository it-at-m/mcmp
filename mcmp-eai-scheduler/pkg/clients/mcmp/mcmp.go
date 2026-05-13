package mcmp

import (
	"fmt"
	"log"
	"net/url"
	"os"
	"time"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const (
	maxRetries = 3
	baseDelay  = 100 * time.Millisecond
)

var berlinLocation *time.Location

type Client struct {
	db         *gorm.DB
	passphrase string
}

func init() {
	loc, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		panic(fmt.Sprintf("failed to load time location Europe/Berlin: %v", err))
	}
	berlinLocation = loc
}

func New(username, password, dsn, passphrase string, debug bool) (*Client, error) {
	escapedUsername := url.QueryEscape(username)
	//	escapedPassword = url.QueryEscape(password)
	escapedPassword := password
	gormLogLevel := logger.Warn
	if debug {
		gormLogLevel = logger.Info
	}
	gormLogger := logger.New(
		log.New(os.Stdout, "\n", log.LstdFlags),
		logger.Config{
			SlowThreshold:             1000 * time.Millisecond,
			LogLevel:                  gormLogLevel,
			IgnoreRecordNotFoundError: true,
			Colorful:                  true,
		},
	)
	DSN := fmt.Sprintf("%s user=%s password=%s", dsn, escapedUsername, escapedPassword)
	db, err := gorm.Open(postgres.Open(DSN), &gorm.Config{Logger: gormLogger})
	if err != nil {
		log.Fatalf("Open Postgres error: %v", err)
	}

	return &Client{
		db:         db,
		passphrase: passphrase,
	}, nil
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

func (c *Client) GetWorkflowJobsToProcess() ([]*Job, error) {
	var jobs []*Job

	err := c.db.Where("awx_template_type = ? AND awx_job_id > ?", AwxTemplateTypeWorkflow, 1565596).
		Preload("Snow").
		Preload("Awx").
		Preload("User").
		Preload("Server").
		Preload("Appservice").
		Preload("Appservice.ChangeGroup").
		Order("id ASC").
		Find(&jobs).Error
	if err != nil {
		return nil, fmt.Errorf("error loading workflow job entries: %w", err)
	}

	return jobs, nil
}

func (c *Client) GetJobsToProcess() ([]*Job, error) {
	var jobs []*Job

	err := c.db.Where("awx_template_type = ? AND awx_job_id > ?", AwxTemplateTypeTemplate, 1565596).
		Preload("Snow").
		Preload("Awx").
		Preload("User").
		Preload("Server").
		Preload("Appservice").
		Preload("Appservice.ChangeGroup").
		Order("id ASC").
		Find(&jobs).Error
	if err != nil {
		return nil, fmt.Errorf("error loading workflow job entries: %w", err)
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

// Close closes the database connection
func (c *Client) Close() error {
	sqlDB, err := c.db.DB()
	if err != nil {
		return fmt.Errorf("error retrieving SQL DB: %w", err)
	}
	return sqlDB.Close()
}

// GetDB returns the underlying GORM DB instance for advanced operations
func (c *Client) GetDB() *gorm.DB {
	return c.db
}

func (c *Client) UpdateJob(job *Job) error {
	if c.db == nil {
		return fmt.Errorf("no DB client available")
	}

	// Save the fields to be updated (dereference for copy)
	originalJob := *job

	for attempt := 1; attempt <= maxRetries; attempt++ {
		// Set UpdatedAt before each attempt
		job.UpdatedAt = time.Now().In(berlinLocation)

		// Try to save the job
		err := c.db.Model(job).Omit("Snow", "Awx", "User", "Server", "Appservice").Updates(job).Error

		if err == nil {
			// Successfully saved
			if attempt > 1 {
				log.Printf("Job with ID %d successfully saved after %d attempts\n", job.ID, attempt)
			}
			return nil
		}

		/// Error during save
		log.Printf("Attempt %d/%d failed while saving job with ID %d: %v\n", attempt, maxRetries, job.ID, err)

		// If this was the last attempt, return the error
		if attempt == maxRetries {
			return fmt.Errorf("error saving job with ID %d after %d attempts: %w", job.ID, maxRetries, err)
		}

		// Reload the job from the database to get the latest state (including Version)
		var freshJob Job
		err = c.db.First(&freshJob, job.ID).Error
		if err != nil {
			return fmt.Errorf("error reloading job with ID %d: %w", job.ID, err)
		}

		freshJob.ServerID = originalJob.ServerID
		freshJob.Status = originalJob.Status
		freshJob.ChangeStartDate = originalJob.ChangeStartDate
		freshJob.ChangeEndDate = originalJob.ChangeEndDate
		freshJob.ChangeStatus = originalJob.ChangeStatus
		freshJob.ChangeNumber = originalJob.ChangeNumber
		freshJob.ChangeSysId = originalJob.ChangeSysId
		freshJob.ChangeLink = originalJob.ChangeLink
		freshJob.ChangeError = originalJob.ChangeError
		freshJob.AwxVariables = originalJob.AwxVariables
		freshJob.AwxStatus = originalJob.AwxStatus
		freshJob.AwxJobId = originalJob.AwxJobId
		freshJob.AwxJobLink = originalJob.AwxJobLink
		freshJob.AwxError = originalJob.AwxError
		freshJob.QuickDiscovery = originalJob.QuickDiscovery
		freshJob.QuickDiscoveryStatus = originalJob.QuickDiscoveryStatus
		freshJob.QuickDiscoveryError = originalJob.QuickDiscoveryError
		freshJob.QuickDiscoveryCiSysid = originalJob.QuickDiscoveryCiSysid
		freshJob.QuickDiscoveryCiName = originalJob.QuickDiscoveryCiName
		freshJob.TaggingStatus = originalJob.TaggingStatus
		freshJob.TaggingError = originalJob.TaggingError
		freshJob.Title = originalJob.Title
		freshJob.Description = originalJob.Description
		freshJob.IP = originalJob.IP
		freshJob.Hostname = originalJob.Hostname
		freshJob.JobEndDate = originalJob.JobEndDate
		freshJob.AwxStartDate = originalJob.AwxStartDate
		freshJob.AwxEndDate = originalJob.AwxEndDate
		freshJob.QuickDiscoveryErrorCounter = originalJob.QuickDiscoveryErrorCounter
		freshJob.NonPostgresEmailStatus = originalJob.NonPostgresEmailStatus
		freshJob.AwxJobName = originalJob.AwxJobName
		freshJob.AwxJobStatus = originalJob.AwxJobStatus
		freshJob.AwxJobFailed = originalJob.AwxJobFailed
		freshJob.AwxJobOrg = originalJob.AwxJobOrg
		freshJob.AwxTemplateLink = originalJob.AwxTemplateLink
		freshJob.AwxTemplateName = originalJob.AwxTemplateName
		freshJob.AwxJobReturnCompleted = originalJob.AwxJobReturnCompleted
		freshJob.AwxJobReturnMessage = originalJob.AwxJobReturnMessage
		freshJob.AwxJobReturnData = originalJob.AwxJobReturnData
		freshJob.AwxJobErrorMessage = originalJob.AwxJobErrorMessage

		// Use the updated job for the next attempt
		*job = freshJob

		// Exponential backoff: wait longer for each attempt
		delay := baseDelay * time.Duration(1<<uint(attempt-1))
		log.Printf("Waiting %v before retry...\n", delay)
		time.Sleep(delay)
	}

	return fmt.Errorf("error saving job with ID %d: maximum number of attempts reached", job.ID)
}

func (c *Client) UpdateServer(server *Server) error {
	if c.db == nil {
		return fmt.Errorf("no DB client available")
	}

	snowServerSysID := server.SnowServerSysID
	snowServerSysClass := server.SnowServerSysClass
	snowInstanceSysID := server.SnowInstanceSysID
	snowInstanceSysClass := server.SnowInstanceSysClass

	for attempt := 1; attempt <= maxRetries; attempt++ {
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

		if err == nil {
			if attempt > 1 {
				log.Printf("Server with ID %d successfully saved after %d attempts\n", server.ID, attempt)
			}
			return nil
		}
		log.Printf("Attempt %d/%d failed while saving server with ID %d: %v\n", attempt, maxRetries, server.ID, err)

		if attempt == maxRetries {
			return fmt.Errorf("error saving server with ID %d after %d attempts: %w", server.ID, maxRetries, err)
		}
		var freshServer Server
		err = c.db.First(&freshServer, server.ID).Error
		if err != nil {
			return fmt.Errorf("error reloading server with ID %d: %w", server.ID, err)
		}

		freshServer.SnowServerSysID = snowServerSysID
		freshServer.SnowServerSysClass = snowServerSysClass
		freshServer.SnowInstanceSysID = snowInstanceSysID
		freshServer.SnowInstanceSysClass = snowInstanceSysClass
		*server = freshServer
		delay := baseDelay * time.Duration(1<<uint(attempt-1))
		log.Printf("Waiting %v before retry...\n", delay)
		time.Sleep(delay)
	}

	return fmt.Errorf("error saving server with ID %d: maximum number of attempts reached", server.ID)
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

	for attempt := 1; attempt <= maxRetries; attempt++ {
		node.UpdatedAt = time.Now().In(berlinLocation)

		// Omit ID and CreatedAt from updates to prevent accidental changes
		err := c.db.Model(node).Omit("ID", "CreatedAt").Updates(node).Error

		if err == nil {
			if attempt > 1 {
				log.Printf("JobNode with ID %d successfully updated after %d attempts\n", node.ID, attempt)
			}
			return nil
		}

		log.Printf("Attempt %d/%d failed while updating job node with ID %d: %v\n", attempt, maxRetries, node.ID, err)

		if attempt == maxRetries {
			return fmt.Errorf("error updating job node with ID %d after %d attempts: %w", node.ID, maxRetries, err)
		}

		// Reload the node to ensure we have the correct state for the next attempt
		var freshNode JobNode
		if err := c.db.First(&freshNode, node.ID).Error; err != nil {
			return fmt.Errorf("error reloading job node with ID %d: %w", node.ID, err)
		}

		// Re-apply values from the original node that should be preserved during the retry
		// (Assuming ID and other base fields are already correct in the object)
		delay := baseDelay * time.Duration(1<<uint(attempt-1))
		log.Printf("Waiting %v before retry...\n", delay)
		time.Sleep(delay)
	}

	return fmt.Errorf("error updating job node with ID %d: maximum number of attempts reached", node.ID)
}
