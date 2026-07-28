package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/signal"
	"runtime/debug"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/it-at-m/mcmp/mcmp-callback-server/pkg/config"
	cfg "github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/db"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// MCMPClient defines the interface for MCMP Client operations.
// This interface abstracts database operations related to job management,
// enabling easier testing and dependency injection.
//
// Methods:
//   - FindJobByID: Retrieves a job record from the database by its unique identifier
//   - UpdateJob: Persists changes to an existing job record
//   - Close: Releases database connections and cleanup resources
type MCMPClient interface {
	// FindJobByID retrieves a job from the database by its unique identifier.
	//
	// Parameters:
	//   - id: The unique job identifier (primary key)
	//
	// Returns:
	//   - *mcmp.Job: The job object if found
	//   - error: An error if the job doesn't exist or database access fails
	FindJobByID(id int64) (*db.Job, error)

	// UpdateJob persists changes to an existing job record in the database.
	//
	// Parameters:
	//   - job: The job object with updated fields to be saved
	//
	// Returns:
	//   - error: An error if the update operation fails
	UpdateJob(job *db.Job) error

	// FindJobIncidentByID retrieves a job incident from the database by its unique identifier.
	//
	// Parameters:
	//   - id: The unique job incident identifier (primary key)
	//
	// Returns:
	//   - *mcmp.JobIncident: The job incident object if found
	//   - error: An error if the incident doesn't exist or database access fails
	FindJobIncidentByID(id int64) (*db.JobIncident, error)

	// UpdateJobIncident persists changes to an existing job incident record in the database.
	//
	// Parameters:
	//   - incident: The job incident object with updated fields to be saved
	//
	// Returns:
	//   - error: An error if the update operation fails
	UpdateJobIncident(incident *db.JobIncident) error

	// Close releases all database connections and performs cleanup operations.
	// This method should be called when the client is no longer needed.
	//
	// Returns:
	//   - error: An error if cleanup fails
	Close() error
}

// Server encapsulates all dependencies and configuration for the callback server.
// It provides HTTP handlers for processing callbacks from external systems
// and manages the lifecycle.go of database connections.
//
// Fields:
//   - mcmpClient: Client interface for database operations on job records
//   - logger: Structured logger for application logging
//   - config: Application configuration loaded from config file
type Server struct {
	mcmpClient MCMPClient
	logger     *logging.StructuredLogger
	config     *config.Config
}

// NewServer creates a new Server instance with all required dependencies initialized.
// This constructor establishes database connections and validates configuration.
//
// Parameters:
//   - cfg: Configuration object containing database credentials, server settings,
//     and operational parameters loaded from the application config file
//
// Returns:
//   - *Server: A fully initialized server instance ready to handle requests
//   - error: An error if database connection fails or configuration is invalid
//
// Example:
//
//	cfg := config.ReadConfig[config.Config]("mcmp-callback-server")
//	srv, err := NewServer(cfg)
//	if err != nil {
//	    log.Fatalf("Failed to create server: %v", err)
//	}
//	defer srv.Close()
func NewServer(cfg *config.Config) (*Server, error) {
	// Initialize structured logger
	logConfig := logging.LogConfig{
		Level:      cfg.LOGGING.Level,
		Output:     cfg.LOGGING.Output,
		Filename:   cfg.LOGGING.Filename,
		MaxSize:    cfg.LOGGING.MaxSize,
		MaxBackups: cfg.LOGGING.MaxBackups,
		MaxAge:     cfg.LOGGING.MaxAge,
		Compress:   cfg.LOGGING.Compress,
	}

	// Set default values if not configured
	if logConfig.Level == "" {
		logConfig.Level = "INFO"
	}
	if logConfig.Output == "" {
		logConfig.Output = "console"
	}
	if logConfig.Format == "" {
		logConfig.Format = "plain"
	}
	if logConfig.MaxSize == 0 {
		logConfig.MaxSize = 100
	}
	if logConfig.MaxBackups == 0 {
		logConfig.MaxBackups = 3
	}
	if logConfig.MaxAge == 0 {
		logConfig.MaxAge = 28
	}

	// Note: GENERAL.Debug only affects GORM logging and other debug features,
	// not the application log level which is controlled by LOGGING.Level

	logger, err := logging.NewStructuredLogger(logConfig)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize logger: %w", err)
	}

	log.SetOutput(logger.GetWriter())
	log.SetPrefix("[STDLIB] ")

	// Initialize MCMP database client with same log destination as application logger
	client, err := db.New(cfg.DATABASE.Username, cfg.DATABASE.Password, cfg.DATABASE.DSN, "", cfg.GENERAL.Debug, logger.GetWriter())
	if err != nil {
		return nil, fmt.Errorf("failed to initialize MCMP client: %w", err)
	}

	logger.Info("Server initialized successfully",
		"general_debug", cfg.GENERAL.Debug,
		"log_level", logConfig.Level,
		"log_output", logConfig.Output,
		"log_format", logConfig.Format,
		"std_logger_configured", true)

	return &Server{
		mcmpClient: client,
		logger:     logger,
		config:     cfg,
	}, nil
}

// Close gracefully releases all server resources and database connections.
// This method should be called during application shutdown to ensure
// proper cleanup and prevent resource leaks.
//
// Returns:
//   - error: An error if closing the database connection fails, nil otherwise
//
// Example:
//
//	defer srv.Close()
func (s *Server) Close() error {
	if s.mcmpClient != nil {
		return s.mcmpClient.Close()
	}
	return nil
}

const (
	// appName is the application identifier used for configuration file naming
	// and logging identification
	appName = "mcmp-callback-server"

	// pathQuickDiscovery defines the URL path prefix for QuickDiscovery callbacks.
	// Expected format: /callback/quickdiscovery/{job_id}
	pathQuickDiscovery = "/callback/quickdiscovery/"

	// pathChange defines the URL path prefix for Change callbacks.
	// Expected format: /callback/change/{job_id}
	pathChange = "/callback/change/"

	// pathIncident defines the URL path prefix for Incident callbacks from ServiceNow.
	// Expected format: /callback/incident/{job_incident_id}
	pathIncident = "/callback/incident/"

	// maxRequestBodySize limits the maximum size of incoming request bodies
	// to prevent memory exhaustion attacks (1 MB = 1,048,576 bytes)
	maxRequestBodySize = 1 << 20 // 1 MB

	// shutdownTimeout defines the maximum time to wait for graceful shutdown
	// before forcefully terminating active connections
	shutdownTimeout = 30 * time.Second

	// approvalStatusApproved is the expected value for approved changes
	approvalStatusApproved = "approved"

	// approvalStatusRejected represents the status of an approval that has been denied or rejected.
	approvalStatusRejected = "rejected"

	// approvalStatusNotRequested represents the status of an approval that has not been requested (was canceled before the approval phase).
	approvalStatusNotRequested = "not requested"

	// Error message constants for consistent error reporting
	errMsgMethodNotAllowed          = "method not allowed"
	errMsgInvalidJSON               = "invalid JSON format"
	errMsgJobNotFound               = "job not found: %v"
	errMsgFailedToUpdateJob         = "failed to update job: %v"
	errMsgInternalServerError       = "internal server error"
	errMsgChangeNotRequired         = "job does not require change approval"
	errMsgQuickDiscoveryNotRequired = "job does not require QuickDiscovery"
	errMsgInvalidChangeStatus       = "invalid ChangeStatus: expected '%s', got '%s'"
	errMsgInvalidQDStatus           = "invalid QuickDiscoveryStatus: expected '%s', got '%s'"
	errMsgInvalidJobStatus          = "invalid JobStatus: expected '%s', got '%s'"
	errMsgIncidentNotFound          = "job incident not found: %v"
	errMsgFailedToUpdateIncident    = "failed to update job incident: %v"
	errMsgInvalidIncidentStatus     = "invalid IncidentStatus: expected '%s', got '%s'"

	// Response status constants
	responseStatusSuccess = "success"

	// Default error messages (German as per business requirements)
	msgChangeRejected = "Der Change wurde abgelehnt!"
	msgChangeCanceled = "Der Change wurde abgebrochen!"
)

// ChangeCallbackRequest represents the structure of incoming POST requests
// from the Change management system. This structure captures approval status,
// error information, and detailed approval history.
//
// Fields:
//   - Success: Indicates whether the Change operation completed successfully
//   - ErrorMessage: Contains error details if Success is false
//   - Result: Nested structure containing approval details and history
//   - Result.Approval: Current approval status (e.g., "approved", "rejected")
//   - Result.ApprovalSet: Identifier for the approval set or workflow
//   - Result.ApprovalHistory: Chronological list of approval state changes
//
// Example JSON:
//
//	{
//	  "success": true,
//	  "error_message": "",
//	  "result": {
//	    "approval": "approved",
//	    "approval_set": "SET001",
//	    "approval_history": [
//	      {"sys_created_on": "2025-10-12T10:00:00Z", "value": "requested"},
//	      {"sys_created_on": "2025-10-12T11:30:00Z", "value": "approved"}
//	    ]
//	  }
//	}
type ChangeCallbackRequest struct {
	Success      bool   `json:"success"`
	ErrorMessage string `json:"error_message"`
	Result       struct {
		Approval        string `json:"approval"`
		ApprovalSet     string `json:"approval_set"`
		ApprovalHistory []struct {
			SysCreatedOn string `json:"sys_created_on"`
			Value        string `json:"value"`
		} `json:"approval_history"`
	} `json:"result"`
}

// QuickDiscoveryCallbackRequest represents the structure of incoming POST requests
// from the QuickDiscovery system. This structure captures discovery results including
// configuration item details created or updated in the CMDB.
//
// Fields:
//   - Success: Indicates whether the QuickDiscovery operation completed successfully
//   - ErrorMessage: Contains error details if Success is false
//   - Result: Nested structure containing discovered configuration item details
//   - Result.CiSysid: System identifier (SYSID) of the configuration item in CMDB
//   - Result.CiName: Human-readable name of the configuration item
//
// Example JSON:
//
//	{
//	  "success": true,
//	  "error_message": "",
//	  "result": {
//	    "ci_sysid": "abc123def456",
//	    "ci_name": "server01.example.com"
//	  }
//	}
type QuickDiscoveryCallbackRequest struct {
	Success      bool   `json:"success"`
	ErrorMessage string `json:"error_message"`
	Result       struct {
		CiSysid string `json:"ci_sysid"`
		CiName  string `json:"ci_name"`
	} `json:"result"`
}

// IncidentCallbackRequest represents the structure of incoming POST requests
// from the ServiceNow Incident management system. This structure captures the
// final resolution state of an incident after it has been processed.
//
// Fields:
//   - Success: Indicates whether the incident was resolved successfully
//   - ErrorMessage: Contains error details if Success is false
//   - Result: Nested structure containing the resolution details
//   - Result.ResolvedBy: SysID of the user who resolved the incident
//   - Result.CloseCode: Label/value pair describing how the incident was closed
//   - Result.ResolvedAt: Timestamp when the incident was resolved
//   - Result.State: Label/value pair describing the final incident state
//   - Result.CloseNotes: Free-text notes added when closing the incident
//
// Example JSON (resolved):
//
//	{
//	  "success": true,
//	  "error_message": "",
//	  "result": {
//	    "resolved_by": "00000000000000000000000000000000",
//	    "close_code": {
//	      "label": "Gelöst (vor Ort, dauerhaft)",
//	      "value": "Solved (Permanently)"
//	    },
//	    "resolved_at": "2026-05-13T07:02:13.000+0000Z",
//	    "state": {
//	      "label": "Gelöst",
//	      "value": "6"
//	    },
//	    "close_notes": "Gelöst"
//	  }
//	}
type IncidentCallbackRequest struct {
	Success      bool   `json:"success"`
	ErrorMessage string `json:"error_message"`
	Result       struct {
		ResolvedBy string `json:"resolved_by"`
		CloseCode  struct {
			Label string `json:"label"`
			Value string `json:"value"`
		} `json:"close_code"`
		ResolvedAt string `json:"resolved_at"`
		State      struct {
			Label string `json:"label"`
			Value string `json:"value"`
		} `json:"state"`
		CloseNotes string `json:"close_notes"`
	} `json:"result"`
}

// ErrorResponse represents the standardized structure for all error responses
// returned by the API. This ensures consistency across all endpoints and makes
// error handling predictable for client.
//
// Fields:
//   - Status: Always set to "error" to distinguish from success responses
//   - Message: Human-readable error description
//   - Code: HTTP status code for reference (optional, included for clarity)
//
// Example JSON:
//
//	{
//	  "status": "error",
//	  "message": "Job not found: record does not exist",
//	  "code": 404
//	}
type ErrorResponse struct {
	Status  string `json:"status"`
	Message string `json:"message"`
	Code    int    `json:"code"`
}

// main is the entry point of the MCMP callback server application.
// It handles command line argument parsing and validation, then delegates
// to startServer for actual server initialization and execution.
//
// Command Line Usage:
//
//	mcmp-callback-server
//
// The application expects exactly zero arguments. Any additional arguments
// will cause the program to display usage information and exit with status 1.
//
// Exit Codes:
//   - 0: Normal termination
//   - 1: Invalid command line arguments
//   - Fatal: Server initialization or runtime errors
func main() {
	// Define custom usage function that displays program usage information
	flag.Usage = func() {
		_, err := fmt.Fprintf(flag.CommandLine.Output(), "usage: %s\n", os.Args[0])
		if err != nil {
			return
		}
		flag.PrintDefaults()
	}

	// Parse command line flags (none are currently defined)
	flag.Parse()

	// Handle different numbers of command line arguments
	switch len(os.Args) {
	case 1:
		// No additional arguments - proceed with normal execution
		startServer()
	default:
		// Any additional arguments are considered an error
		_, err := fmt.Fprintln(os.Stderr, "error: wrong number of arguments")
		if err != nil {
			return
		}
		flag.Usage()
		os.Exit(1)
	}
}

// startServer initializes and starts the HTTP server with graceful shutdown support.
// The server runs behind an Apache reverse proxy for HTTPS termination and
// listens on the configured port for HTTP traffic.
//
// Workflow:
//  1. Loads application configuration from config file
//  2. Initializes server with database connections
//  3. Registers HTTP route handlers for callback endpoints
//  4. Starts HTTP server in a goroutine
//  5. Listens for OS interrupt signals (SIGINT, SIGTERM)
//  6. On signal receipt, initiates graceful shutdown with timeout
//  7. Closes database connections and cleans up resources
//
// The server implements the following endpoints:
//   - POST /callback/change/{job_id}: Handles Change approval callbacks
//   - POST /callback/quickdiscovery/{job_id}: Handles QuickDiscovery result callbacks
//
// Timeouts:
//   - Read: 15 seconds
//   - Write: 15 seconds
//   - Idle: 60 seconds
//   - Graceful shutdown: 30 seconds
//
// The function blocks until an interrupt signal is received, then performs
// graceful shutdown before returning.
func startServer() {
	// Read configuration
	c, err := cfg.LoadConfig[config.Config](appName)
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Create server instance with all dependencies
	srv, err := NewServer(c)
	if err != nil {
		log.Fatalf("Failed to create server: %v", err)
	}
	// Ensure cleanup on any exit path
	defer func() {
		if err := srv.Close(); err != nil {
			srv.errorLog("Error closing server resources", "error", err)
		} else {
			srv.infoLog("Database connection closed")
		}
	}()

	mux := http.NewServeMux()

	// Register specific callback endpoints with server methods wrapped in middleware
	// Middleware order: withLogging(withRecovery(...))
	// - Inner: withRecovery catches panics and prevents server crashes
	// - Outer: withLogging logs all requests including those that panicked
	mux.HandleFunc(pathChange, srv.withLogging(srv.withRecovery(srv.handleChangeCallback)))
	mux.HandleFunc(pathQuickDiscovery, srv.withLogging(srv.withRecovery(srv.handleQuickDiscoveryCallback)))
	mux.HandleFunc(pathIncident, srv.withLogging(srv.withRecovery(srv.handleIncidentCallback)))

	// Server configuration with timeouts
	server := &http.Server{
		Addr:         fmt.Sprintf(":%d", c.GENERAL.Port),
		Handler:      mux,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Channel to listen for OS signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

	// Channel to capture server startup errors
	serverErrChan := make(chan error, 1)

	// Start server in a goroutine
	go func() {
		srv.infoLog("Starting server", "port", c.GENERAL.Port)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErrChan <- err
		}
	}()

	// Wait for either interrupt signal or server error
	select {
	case sig := <-sigChan:
		srv.infoLog("Received signal, initiating graceful shutdown", "signal", sig)
	case err := <-serverErrChan:
		srv.errorLog("Server failed to start", "error", err)
		log.Fatalf("Server failed to start: %v", err)
	}

	// Create context with timeout for shutdown
	ctx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()

	// Attempt graceful shutdown
	if err := server.Shutdown(ctx); err != nil {
		srv.warnLog("Server forced to shutdown", "error", err)
	} else {
		srv.infoLog("Server stopped gracefully")
	}

	srv.infoLog("Application shutdown complete")
}

// handleChangeCallback processes POST callbacks from the Change management system.
// This handler receives approval status updates for Change requests and updates
// the corresponding job records in the database.
//
// HTTP Method: POST only
//
// URL Format: /callback/change/{job_id}
//   - job_id: Numeric identifier of the job (int64)
//
// Request Body: JSON formatted ChangeCallbackRequest
//
// Parameters:
//   - w: HTTP response writer for sending responses to the client
//   - r: HTTP request containing the callback data
//
// Request Validation:
//   - Method must be POST
//   - URL must contain valid numeric job ID
//   - Request body must be valid JSON matching ChangeCallbackRequest structure
//   - Body size limited to 1 MB to prevent memory exhaustion
//
// Business Logic:
//  1. Extracts job ID from URL path
//  2. Parses JSON request body
//  3. Retrieves job record from database
//  4. Validates job is in 'waiting_for_approval' state
//  5. Updates job status based on approval result:
//     - success=true + approval="approved" → ChangeStatus='approved'
//     - Otherwise → ChangeStatus='rejected' with error message
//  6. Persists updated job to database
//  7. Returns JSON response with new status
//
// Response Codes:
//   - 200 OK: Successfully processed callback
//   - 400 Bad Request: Invalid URL, ID format, JSON, or job state
//   - 404 Not Found: Job ID doesn't exist
//   - 405 Method Not Allowed: Non-POST request
//   - 500 Internal Server Error: Database update failed
//
// Response Body (Success):
//
//	{
//	  "status": "success",
//	  "job_id": 123,
//	  "new_status": "approved"
//	}
func (s *Server) handleChangeCallback(w http.ResponseWriter, r *http.Request) {
	// Only accept POST requests
	if r.Method != http.MethodPost {
		sendErrorResponse(w, http.StatusMethodNotAllowed, errMsgMethodNotAllowed)
		return
	}

	// Parse job ID from URL path
	id, err := parseJobIDFromPath(r.URL.Path, pathChange)
	if err != nil {
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	s.debugLog("Received Change callback", "job_id", id)

	// Parse JSON request body
	var callbackData ChangeCallbackRequest
	if err := readAndParseJSON(r, &callbackData); err != nil {
		s.errorLog("Failed to parse JSON request", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	// Find job by ID
	job, err := s.mcmpClient.FindJobByID(id)
	if err != nil {
		s.errorLog("Error finding job", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusNotFound, fmt.Sprintf(errMsgJobNotFound, err))
		return
	}

	// Validate job configuration and status
	if err := validateChangeJob(job); err != nil {
		s.errorLog("Job validation failed", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	// Update job based on an approval result
	if callbackData.Success && strings.EqualFold(callbackData.Result.Approval, approvalStatusApproved) {
		// approved
		job.ChangeStatus = db.ChangeStatusApproved
		job.Status = db.JobStatusApproved
		s.debugLog("Setting ChangeStatus to approved",
			"job_id", id,
			"status", db.ChangeStatusApproved,
			"approval", callbackData.Result.Approval)
	} else if !callbackData.Success && strings.EqualFold(callbackData.Result.Approval, approvalStatusRejected) {
		// rejected
		job.ChangeStatus = db.ChangeStatusRejected
		job.Status = db.JobStatusRejected
		if callbackData.ErrorMessage != "" {
			job.ChangeError = &callbackData.ErrorMessage
		}
		job.Title = replaceErrorPlaceholder(job.ActionErrorTitle, msgChangeRejected)
		job.Description = replaceErrorPlaceholder(job.ActionErrorDescription, msgChangeRejected)
		s.debugLog("Setting ChangeStatus to rejected",
			"job_id", id,
			"status", db.ChangeStatusRejected,
			"error_message", callbackData.ErrorMessage)
	} else if !callbackData.Success && (strings.EqualFold(callbackData.Result.Approval, approvalStatusApproved) || strings.EqualFold(callbackData.Result.Approval, approvalStatusNotRequested)) {
		// canceled
		job.ChangeStatus = db.ChangeStatusCanceled
		job.Status = db.JobStatusCanceled
		if callbackData.ErrorMessage != "" {
			job.ChangeError = &callbackData.ErrorMessage
		}
		job.Title = replaceErrorPlaceholder(job.ActionErrorTitle, msgChangeCanceled)
		job.Description = replaceErrorPlaceholder(job.ActionErrorDescription, msgChangeCanceled)
		s.debugLog("Setting ChangeStatus to canceled",
			"job_id", id,
			"status", db.ChangeStatusCanceled,
			"error_message", callbackData.ErrorMessage)
	} else {
		s.errorLog("Invalid approval result", "job_id", id, "success", callbackData.Success, "error_message", callbackData.ErrorMessage, "approval", callbackData.Result.Approval)
		sendErrorResponse(w, http.StatusBadRequest, errMsgInvalidJSON)
		return
	}

	// Save an updated job
	if err := s.mcmpClient.UpdateJob(job); err != nil {
		s.errorLog("Error updating job", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusInternalServerError, fmt.Sprintf(errMsgFailedToUpdateJob, err))
		return
	}

	// Send success response
	response := map[string]interface{}{
		"status":     responseStatusSuccess,
		"job_id":     id,
		"new_status": string(job.ChangeStatus),
	}
	if err := sendJSONResponse(w, http.StatusOK, response); err != nil {
		s.errorLog("Failed to send response", "error", err, "job_id", id)
		return
	}

	s.infoLog("Successfully processed Change callback",
		"job_id", id,
		"new_status", job.ChangeStatus,
		"approval", callbackData.Result.Approval)
}

// handleQuickDiscoveryCallback processes POST callbacks from the QuickDiscovery system.
// This handler receives discovery results including CMDB configuration item details
// and updates the corresponding job records in the database.
//
// HTTP Method: POST only
//
// URL Format: /callback/quickdiscovery/{job_id}
//   - job_id: Numeric identifier of the job (int64)
//
// Request Body: JSON formatted QuickDiscoveryCallbackRequest
//
// Parameters:
//   - w: HTTP response writer for sending responses to the client
//   - r: HTTP request containing the callback data
//
// Request Validation:
//   - Method must be POST
//   - URL must contain valid numeric job ID
//   - Request body must be valid JSON matching QuickDiscoveryCallbackRequest structure
//   - Body size limited to 1 MB to prevent memory exhaustion
//
// Business Logic:
//  1. Extracts job ID from URL path
//  2. Parses JSON request body
//  3. Retrieves job record from database
//  4. Validates job is in 'waiting' state
//  5. Updates job based on a discovery result:
//     - success=true → QuickDiscoveryStatus='successful' + CI details
//     - success=false → QuickDiscoveryStatus='failed' + error message
//  6. Persists an updated job to database
//  7. Returns JSON response with new status
//
// Response Codes:
//   - 200 OK: Successfully processed callback
//   - 400 Bad Request: Invalid URL, ID format, JSON, or job state
//   - 404 Not Found: Job ID doesn't exist
//   - 405 Method Not Allowed: Non-POST request
//   - 500 Internal Server Error: Database update failed
//
// Response Body (Success):
//
//	{
//	  "status": "success",
//	  "job_id": 123,
//	  "new_status": "successful"
//	}
func (s *Server) handleQuickDiscoveryCallback(w http.ResponseWriter, r *http.Request) {
	// Only accept POST requests
	if r.Method != http.MethodPost {
		sendErrorResponse(w, http.StatusMethodNotAllowed, errMsgMethodNotAllowed)
		return
	}

	// Parse job ID from URL path
	id, err := parseJobIDFromPath(r.URL.Path, pathQuickDiscovery)
	if err != nil {
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	s.debugLog("Received QuickDiscovery callback", "job_id", id)

	// Parse JSON request body
	var callbackData QuickDiscoveryCallbackRequest
	if err := readAndParseJSON(r, &callbackData); err != nil {
		s.debugLog("Failed to parse JSON request", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	s.debugLog("Parsed callback data",
		"job_id", id,
		"success", callbackData.Success,
		"ci_sysid", callbackData.Result.CiSysid,
		"ci_name", callbackData.Result.CiName)

	// Find job by ID
	job, err := s.mcmpClient.FindJobByID(id)
	if err != nil {
		s.errorLog("Error finding job", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusNotFound, fmt.Sprintf(errMsgJobNotFound, err))
		return
	}

	// Validate job configuration and status
	if err := validateQuickDiscoveryJob(job); err != nil {
		s.debugLog("Job validation failed", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	// Update job based on success flag
	if callbackData.Success {
		job.Status = db.JobStatusQuickdiscoveryCompleted
		job.QuickDiscoveryStatus = db.QuickdiscoveryStatusSuccessful
		job.QuickDiscoveryCiSysid = &callbackData.Result.CiSysid
		job.QuickDiscoveryCiName = &callbackData.Result.CiName
		s.debugLog("Setting QuickDiscoveryStatus to successful",
			"job_id", id,
			"status", db.QuickdiscoveryStatusSuccessful,
			"ci_name", callbackData.Result.CiName,
			"ci_sysid", callbackData.Result.CiSysid)
	} else {
		job.Status = db.JobStatusQuickdiscoveryFailed
		job.QuickDiscoveryStatus = db.QuickdiscoveryStatusFailed
		if callbackData.ErrorMessage != "" {
			job.Title = replaceErrorPlaceholder(job.ActionErrorTitle, callbackData.ErrorMessage)
			job.Description = replaceErrorPlaceholder(job.ActionErrorDescription, callbackData.ErrorMessage)
			job.QuickDiscoveryError = &callbackData.ErrorMessage
		} else {
			errorMsg := "Quick Discovery failed!"
			job.Title = replaceErrorPlaceholder(job.ActionErrorTitle, errorMsg)
			job.Description = replaceErrorPlaceholder(job.ActionErrorDescription, errorMsg)
			job.QuickDiscoveryError = &errorMsg
		}
		s.debugLog("Setting QuickDiscoveryStatus to failed",
			"job_id", id,
			"status", db.QuickdiscoveryStatusFailed,
			"error_message", callbackData.ErrorMessage)
	}

	// Save updated job
	if err := s.mcmpClient.UpdateJob(job); err != nil {
		s.errorLog("Error updating job", "error", err, "job_id", id)
		sendErrorResponse(w, http.StatusInternalServerError, fmt.Sprintf(errMsgFailedToUpdateJob, err))
		return
	}

	// Send success response
	response := map[string]interface{}{
		"status":     responseStatusSuccess,
		"job_id":     id,
		"new_status": string(job.QuickDiscoveryStatus),
	}
	if err := sendJSONResponse(w, http.StatusOK, response); err != nil {
		s.errorLog("Failed to send response", "error", err, "job_id", id)
		return
	}

	s.infoLog("Successfully processed QuickDiscovery callback",
		"job_id", id,
		"new_status", job.QuickDiscoveryStatus,
		"success", callbackData.Success)
}

// handleIncidentCallback processes POST callbacks from the ServiceNow Incident
// management system. This handler receives the final state of an incident after
// it has been resolved (or failed to be resolved) and updates the corresponding
// job_incident record in the database.
//
// HTTP Method: POST only
//
// URL Format: /callback/incident/{job_incident_id}
//   - job_incident_id: Numeric identifier of the job_incident record (int64)
//
// Request Body: JSON formatted IncidentCallbackRequest
//
// Response Codes:
//   - 200 OK: Successfully processed callback
//   - 400 Bad Request: Invalid URL, ID format, JSON, or incident state
//   - 404 Not Found: Job incident ID doesn't exist
//   - 405 Method Not Allowed: Non-POST request
//   - 500 Internal Server Error: Database update failed
//
// Response Body (Success):
//
//	{
//	  "status": "success",
//	  "incident_id": 123,
//	  "new_status": "resolved"
//	}
func (s *Server) handleIncidentCallback(w http.ResponseWriter, r *http.Request) {
	// Only accept POST requests
	if r.Method != http.MethodPost {
		sendErrorResponse(w, http.StatusMethodNotAllowed, errMsgMethodNotAllowed)
		return
	}

	// Parse incident ID from URL path
	id, err := parseJobIDFromPath(r.URL.Path, pathIncident)
	if err != nil {
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	s.debugLog("Received Incident callback", "incident_id", id)

	// Parse JSON request body
	var callbackData IncidentCallbackRequest
	if err := readAndParseJSON(r, &callbackData); err != nil {
		s.errorLog("Failed to parse JSON request", "error", err, "incident_id", id)
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	// Find incident by ID
	incident, err := s.mcmpClient.FindJobIncidentByID(id)
	if err != nil {
		s.errorLog("Error finding job incident", "error", err, "incident_id", id)
		sendErrorResponse(w, http.StatusNotFound, fmt.Sprintf(errMsgIncidentNotFound, err))
		return
	}

	// Validate incident state
	if err := validateIncident(incident); err != nil {
		s.errorLog("Job incident validation failed", "error", err, "incident_id", id)
		sendErrorResponse(w, http.StatusBadRequest, err.Error())
		return
	}

	// Update incident fields from callback data
	incident.Success = new(callbackData.Success)

	if callbackData.ErrorMessage != "" {
		incident.ErrorMessage = new(callbackData.ErrorMessage)
	}
	if callbackData.Result.CloseCode.Label != "" {
		incident.CloseCodeLabel = new(callbackData.Result.CloseCode.Label)
	}
	if callbackData.Result.CloseCode.Value != "" {
		incident.CloseCodeValue = new(callbackData.Result.CloseCode.Value)
	}
	if callbackData.Result.State.Label != "" {
		incident.StateLabel = new(callbackData.Result.State.Label)
	}
	if callbackData.Result.State.Value != "" {
		incident.StateValue = new(callbackData.Result.State.Value)
	}
	if callbackData.Result.CloseNotes != "" {
		incident.CloseNotes = new(callbackData.Result.CloseNotes)
	}

	// Parse resolved_at timestamp if provided
	if callbackData.Result.ResolvedAt != "" {
		if parsed, parseErr := parseIncidentTimestamp(callbackData.Result.ResolvedAt); parseErr == nil {
			incident.ResolvedAt = &parsed
		} else {
			s.warnLog("Failed to parse resolved_at timestamp", "incident_id", id, "resolved_at", callbackData.Result.ResolvedAt, "error", parseErr)
		}
	}

	// Determine new incident status based on success flag
	if callbackData.Success {
		incident.Status = db.IncidentStatusResolved
		s.debugLog("Setting IncidentStatus to resolved", "incident_id", id, "status", db.IncidentStatusResolved)
	} else {
		incident.Status = db.IncidentStatusFailed
		s.debugLog("Setting IncidentStatus to failed", "incident_id", id, "status", db.IncidentStatusFailed, "error_message", callbackData.ErrorMessage)
	}

	// Save updated incident
	if err := s.mcmpClient.UpdateJobIncident(incident); err != nil {
		s.errorLog("Error updating job incident", "error", err, "incident_id", id)
		sendErrorResponse(w, http.StatusInternalServerError, fmt.Sprintf(errMsgFailedToUpdateIncident, err))
		return
	}

	// Find the associated job to update its status
	job, err := s.mcmpClient.FindJobByID(incident.JobID)
	if err != nil {
		s.errorLog("Error finding job for incident", "error", err, "job_id", incident.JobID, "incident_id", id)
		// We don't fail the whole callback here, as the incident itself is already updated.
		// But we log it as an error.
	} else {
		// Update job status based on incident resolution
		if callbackData.Success {
			// Incident resolved successfully
			if job.ChangeStatus == db.ChangeStatusWaitingForIncidentResolution {
				job.ChangeStatus = db.ChangeStatusNew
				job.Status = db.JobStatusNew
			}
			if job.QuickDiscoveryStatus == db.QuickdiscoveryStatusWaitingForIncidentResolution {
				job.QuickDiscoveryStatus = db.QuickdiscoveryStatusNew
				job.Status = db.JobStatusAwxCompleted
			}
			if job.TaggingStatus == db.TaggingStatusWaitingForIncidentResolution {
				job.TaggingStatus = db.TaggingStatusNew
				job.Status = db.JobStatusQuickdiscoveryCompleted
			}
			if job.AwxStatus == db.AwxStatusWaitingForIncidentResolution {
				job.AwxStatus = db.AwxStatusIncidentSuccessful
				job.Status = db.JobStatusAwxCompleted
			}
		} else {
			// Incident resolution failed
			if job.ChangeStatus == db.ChangeStatusWaitingForIncidentResolution {
				job.ChangeStatus = db.ChangeStatusIncidentFailed
				job.Status = db.JobStatusError
			}
			if job.QuickDiscoveryStatus == db.QuickdiscoveryStatusWaitingForIncidentResolution {
				job.QuickDiscoveryStatus = db.QuickdiscoveryStatusIncidentFailed
				job.Status = db.JobStatusError
			}
			if job.TaggingStatus == db.TaggingStatusWaitingForIncidentResolution {
				job.TaggingStatus = db.TaggingStatusIncidentFailed
				job.Status = db.JobStatusError
			}
			if job.AwxStatus == db.AwxStatusWaitingForIncidentResolution {
				job.AwxStatus = db.AwxStatusIncidentFailed
				job.Status = db.JobStatusError
			}
		}

		if err := s.mcmpClient.UpdateJob(job); err != nil {
			s.errorLog("Error updating job status after incident callback", "error", err, "job_id", job.ID, "incident_id", id)
			// Again, don't fail the whole request, but log it.
		} else {
			s.infoLog("Successfully updated job status after incident callback", "job_id", job.ID, "new_job_status", job.Status)
		}
	}

	// Send success response
	response := map[string]interface{}{
		"status":      responseStatusSuccess,
		"incident_id": id,
		"new_status":  string(incident.Status),
	}
	if err := sendJSONResponse(w, http.StatusOK, response); err != nil {
		s.errorLog("Failed to send response", "error", err, "incident_id", id)
		return
	}

	s.infoLog("Successfully processed Incident callback", "incident_id", id, "new_status", incident.Status, "success", callbackData.Success)
}

// validateIncident validates that a job incident is in a state that allows
// receiving a resolution callback. Only incidents in status 'open' may be updated.
func validateIncident(incident *db.JobIncident) error {
	if incident.Status != db.IncidentStatusOpen {
		return fmt.Errorf(errMsgInvalidIncidentStatus, db.IncidentStatusOpen, incident.Status)
	}
	return nil
}

// parseJobIDFromPath extracts and validates the numeric job ID from a callback URL path.
// This helper function parses URLs following the pattern: {prefix}{id} or {prefix}{id}/
// and converts the ID to int64.
//
// Parameters:
//   - path: The complete URL path from the HTTP request (e.g., "/callback/change/123")
//   - prefix: The expected path prefix to strip (e.g., "/callback/change/")
//
// Returns:
//   - int64: The parsed job ID
//   - error: An error if the path format is invalid or ID cannot be parsed as int64
//
// Errors:
//   - "invalid callback URL - missing ID": Path doesn't contain an ID after the prefix
//   - "invalid ID format": ID string cannot be converted to int64
//
// Examples:
//
//	parseJobIDFromPath("/callback/change/123", "/callback/change/") → 123, nil
//	parseJobIDFromPath("/callback/change/123/", "/callback/change/") → 123, nil
//	parseJobIDFromPath("/callback/change/", "/callback/change/") → 0, error
//	parseJobIDFromPath("/callback/change/abc", "/callback/change/") → 0, error
func parseJobIDFromPath(path, prefix string) (int64, error) {
	idStr := extractIDFromPath(path, prefix)
	if idStr == "" {
		return 0, fmt.Errorf("invalid callback URL - missing ID")
	}

	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("invalid ID format")
	}

	return id, nil
}

// extractIDFromPath extracts the numeric job ID from a callback URL path.
// This helper function parses URLs following the pattern: {prefix}{id} or {prefix}{id}/
//
// Parameters:
//   - path: The complete URL path from the HTTP request (e.g., "/callback/change/123")
//   - prefix: The expected path prefix to strip (e.g., "/callback/change/")
//
// Returns:
//   - string: The extracted ID portion, or empty string if extraction fails
//
// Behavior:
//   - Returns empty string if path doesn't start with the specified prefix
//   - Strips the prefix from the path
//   - Removes trailing slash if present
//   - Returns empty string if no ID remains after prefix removal
//
// Examples:
//
//	extractIDFromPath("/callback/change/123", "/callback/change/") → "123"
//	extractIDFromPath("/callback/change/123/", "/callback/change/") → "123"
//	extractIDFromPath("/callback/change/", "/callback/change/") → ""
//	extractIDFromPath("/other/path/123", "/callback/change/") → ""
//	extractIDFromPath("/callback/quickdiscovery/456", "/callback/quickdiscovery/") → "456"
//
// Note: This function only extracts the string; ID validation and conversion
// to int64 should be performed using parseJobIDFromPath instead.
func extractIDFromPath(path, prefix string) string {
	if !strings.HasPrefix(path, prefix) {
		return ""
	}

	// Extract ID part after the prefix
	id := strings.TrimPrefix(path, prefix)
	id = strings.TrimSuffix(id, "/") // Remove trailing slash if present

	if id == "" {
		return ""
	}

	return id
}

// readAndParseJSON reads the request body and parses it as JSON into the provided structure.
// This helper function handles body size limiting, reading, and JSON unmarshaling with
// comprehensive error handling.
//
// Parameters:
//   - r: The HTTP request containing the JSON body
//   - v: Pointer to the structure to unmarshal the JSON into
//
// Returns:
//   - error: An error if reading fails, body is too large, or JSON is invalid
//
// Behavior:
//   - Limits request body size to maxRequestBodySize (1 MB) to prevent memory exhaustion
//   - Reads the entire body into memory
//   - Closes the request body (deferred)
//   - Unmarshals JSON into the provided structure
//
// Errors:
//   - "error reading request body": Failed to read the body (network error, size limit exceeded)
//   - "invalid JSON format": Body is not valid JSON or doesn't match the target structure
//
// Example:
//
//	var data ChangeCallbackRequest
//	if err := readAndParseJSON(r, &data); err != nil {
//	    http.Error(w, err.Error(), http.StatusBadRequest)
//	    return
//	}
func readAndParseJSON(r *http.Request, v interface{}) error {
	// Limit request body size to prevent memory exhaustion attacks
	r.Body = http.MaxBytesReader(nil, r.Body, maxRequestBodySize)

	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {
			log.Printf("Error closing request body: %v", err)
		}
	}(r.Body)

	body, err := io.ReadAll(r.Body)
	if err != nil {
		return fmt.Errorf("error reading request body")
	}

	if err := json.Unmarshal(body, v); err != nil {
		return fmt.Errorf("invalid JSON format [body = \"%s\"]", string(body))
	}

	return nil
}

// sendJSONResponse sends a JSON-formatted HTTP response with the specified status code.
// This helper function provides consistent JSON response formatting across all endpoints
// and handles serialization errors gracefully.
//
// Parameters:
//   - w: The HTTP response writer to send the response to
//   - status: The HTTP status code (e.g., http.StatusOK, http.StatusBadRequest)
//   - data: The data structure to serialize as JSON (will be marshaled)
//
// Returns:
//   - error: An error if JSON marshaling or writing fails, nil on success
//
// Behavior:
//   - Marshals the data structure to JSON
//   - Sets Content-Type header to "application/json"
//   - Writes the HTTP status code
//   - Writes the JSON response body
//   - Returns error if any step fails (caller should handle)
//
// Example:
//
//	response := map[string]interface{}{
//	    "status": "success",
//	    "job_id": 123,
//	}
//	if err := sendJSONResponse(w, http.StatusOK, response); err != nil {
//	    log.Printf("Failed to send response: %v", err)
//	}
func sendJSONResponse(w http.ResponseWriter, status int, data interface{}) error {
	responseBytes, err := json.Marshal(data)
	if err != nil {
		return fmt.Errorf("error encoding JSON response: %w", err)
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)

	// nosemgrep: go.lang.security.audit.xss.no-direct-write-to-responsewriter.no-direct-write-to-responsewriter -- Safe JSON response, no HTML rendering or user input
	if _, err := w.Write(responseBytes); err != nil {
		return fmt.Errorf("error writing response: %w", err)
	}

	return nil
}

// sendErrorResponse sends a standardized JSON error response with the specified
// HTTP status code and error message. This ensures all error responses have a
// consistent format across the API.
//
// Parameters:
//   - w: The HTTP response writer to send the error response to
//   - status: The HTTP status code (e.g., http.StatusBadRequest, http.StatusNotFound)
//   - message: The error message to include in the response
//
// Behavior:
//   - Creates an ErrorResponse structure with status="error"
//   - Includes the HTTP status code for client reference
//   - Sends the response as JSON using sendJSONResponse
//   - If JSON serialization fails, falls back to plain text error (500)
//
// Response Format:
//
//	{
//	  "status": "error",
//	  "message": "Invalid ID format",
//	  "code": 400
//	}
//
// Example Usage:
//
//	sendErrorResponse(w, http.StatusBadRequest, "Invalid ID format")
//	sendErrorResponse(w, http.StatusNotFound, fmt.Sprintf("Job not found: %v", err))
func sendErrorResponse(w http.ResponseWriter, status int, message string) {
	errorResp := ErrorResponse{
		Status:  "error",
		Message: message,
		Code:    status,
	}

	// Attempt to send JSON error response
	if err := sendJSONResponse(w, status, errorResp); err != nil {
		// Fallback to plain text if JSON encoding fails
		// This should rarely happen, but ensures we always send some response
		http.Error(w, errMsgInternalServerError, http.StatusInternalServerError)
	}
}

// validateChangeJob validates that a job is properly configured for Change callback processing.
// This function ensures the job has the ChangeRequired flag set and is in the correct state
// to receive a change approval callback.
//
// Parameters:
//   - job: The job to validate
//
// Returns:
//   - error: A descriptive error if validation fails, nil if the job is valid
//
// Validation Rules:
//   - Job must have ChangeRequired flag set to true
//   - Job's ChangeStatus must be 'waiting_for_approval'
//   - Job's Status must be 'waiting_for_approval'
func validateChangeJob(job *db.Job) error {
	if !job.ChangeRequired {
		return fmt.Errorf(errMsgChangeNotRequired)
	}
	if job.ChangeStatus != db.ChangeStatusWaitingForApproval {
		return fmt.Errorf(errMsgInvalidChangeStatus,
			db.ChangeStatusWaitingForApproval,
			job.ChangeStatus)
	}
	if job.Status != db.JobStatusWaitingForApproval {
		return fmt.Errorf(errMsgInvalidJobStatus,
			db.JobStatusWaitingForApproval,
			job.Status)
	}
	return nil
}

// validateQuickDiscoveryJob validates that a job is properly configured for QuickDiscovery
// callback processing. This function ensures the job has the QuickDiscovery flag set and
// is in the correct state to receive a discovery result callback.
//
// Parameters:
//   - job: The job to validate
//
// Returns:
//   - error: A descriptive error if validation fails, nil if the job is valid
//
// Validation Rules:
//   - Job must have QuickDiscovery flag set to true
//   - Job's QuickDiscoveryStatus must be 'waiting'
//   - Job's Status must be 'waiting_for_quickdiscovery'
func validateQuickDiscoveryJob(job *db.Job) error {
	if !job.QuickDiscovery && !job.ServerInstallation {
		return fmt.Errorf(errMsgQuickDiscoveryNotRequired)
	}
	if job.QuickDiscoveryStatus != db.QuickdiscoveryStatusWaiting {
		return fmt.Errorf(errMsgInvalidQDStatus,
			db.QuickdiscoveryStatusWaiting,
			job.QuickDiscoveryStatus)
	}
	if job.Status != db.JobStatusWaitingForQuickdiscovery {
		return fmt.Errorf(errMsgInvalidJobStatus,
			db.JobStatusWaitingForQuickdiscovery,
			job.Status)
	}
	return nil
}

// debugLog provides conditional debug logging functionality using structured logging.
// It only outputs log messages when debug level is enabled.
func (s *Server) debugLog(msg string, args ...any) {
	s.logger.Debug(msg, args...)
}

// infoLog provides info-level structured logging.
func (s *Server) infoLog(msg string, args ...any) {
	s.logger.Info(msg, args...)
}

// errorLog provides error-level structured logging.
func (s *Server) errorLog(msg string, args ...any) {
	s.logger.Error(msg, args...)
}

// warnLog provides warning-level structured logging.
func (s *Server) warnLog(msg string, args ...any) {
	s.logger.Warn(msg, args...)
}

// responseWriter is a wrapper around http.ResponseWriter that captures
// the HTTP status code for logging purposes.
type responseWriter struct {
	http.ResponseWriter
	statusCode int
	written    bool
}

// WriteHeader captures the status code and delegates to the underlying ResponseWriter.
func (rw *responseWriter) WriteHeader(code int) {
	if !rw.written {
		rw.statusCode = code
		rw.written = true
		rw.ResponseWriter.WriteHeader(code)
	}
}

// Write captures the write operation and ensures status code is set.
func (rw *responseWriter) Write(b []byte) (int, error) {
	if !rw.written {
		rw.WriteHeader(http.StatusOK)
	}
	return rw.ResponseWriter.Write(b)
}

// withLogging is a middleware that logs HTTP requests including method, path,
// remote address, status code, and processing duration using structured logging.
//
// This middleware wraps HTTP handlers to provide comprehensive request logging,
// which is essential for monitoring, debugging, and security auditing.
//
// Parameters:
//   - next: The HTTP handler to wrap with logging functionality
//
// Returns:
//   - http.HandlerFunc: A new handler that logs requests and delegates to the wrapped handler
//
// Logged Information:
//   - HTTP Method (GET, POST, etc.)
//   - Request Path
//   - Remote IP Address
//   - HTTP Status Code
//   - Processing Duration in milliseconds
func (s *Server) withLogging(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()

		// Wrap the ResponseWriter to capture status code
		wrapped := &responseWriter{
			ResponseWriter: w,
			statusCode:     http.StatusOK,
			written:        false,
		}

		// Call the next handler
		next(wrapped, r)

		// Log request details using structured logging
		duration := time.Since(start)
		s.infoLog("HTTP request processed",
			"method", r.Method,
			"path", r.URL.Path,
			"remote_addr", r.RemoteAddr,
			"status_code", wrapped.statusCode,
			"status_text", http.StatusText(wrapped.statusCode),
			"duration_ms", duration.Milliseconds(),
		)
	}
}

// withRecovery is a middleware that recovers from panics in HTTP handlers,
// preventing the entire server from crashing due to unhandled panics.
//
// This middleware provides a safety net for unexpected errors, ensuring
// server stability and logging detailed panic information for debugging.
//
// Parameters:
//   - next: The HTTP handler to wrap with panic recovery
//
// Returns:
//   - http.HandlerFunc: A new handler that recovers from panics and returns a 500 error
//
// Behavior:
//   - Catches any panic that occurs in the wrapped handler
//   - Logs the panic message and stack trace using structured logging
//   - Returns HTTP 500 Internal Server Error to the client
//   - Prevents server crash and allows other requests to continue
func (s *Server) withRecovery(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if err := recover(); err != nil {
				// Log the panic with stack trace using structured logging
				s.errorLog("Panic recovered in HTTP handler",
					"method", r.Method,
					"path", r.URL.Path,
					"error", err,
					"stack_trace", string(debug.Stack()),
				)

				// Return 500 Internal Server Error
				http.Error(w, errMsgInternalServerError, http.StatusInternalServerError)
			}
		}()

		next(w, r)
	}
}

// replaceErrorPlaceholder replaces the ${error} placeholder in a template string with the actual error message.
// If the template is nil or empty, returns an empty string pointer.
func replaceErrorPlaceholder(template *string, errorMessage string) *string {
	// Fallback if no template is set
	templateValue := ""
	if template != nil && *template != "" {
		templateValue = *template
	}
	result := templateValue

	// Replace ${error} with errorMessage
	result = strings.ReplaceAll(result, "${error}", errorMessage)

	return &result
}

// parseIncidentTimestamp parses an incident timestamp coming from ServiceNow.
// ServiceNow sends timestamps such as "2026-05-13T07:02:13.000+0000Z" which is
// not strictly RFC3339 (it contains both a numeric offset and a trailing Z).
// We accept several common formats to be robust.
func parseIncidentTimestamp(value string) (time.Time, error) {
	layouts := []string{
		"2006-01-02T15:04:05.000-0700Z",
		"2006-01-02T15:04:05.000Z0700",
		"2006-01-02T15:04:05.000-0700",
		"2006-01-02T15:04:05.000Z07:00",
		"2006-01-02T15:04:05Z07:00",
		time.RFC3339Nano,
		time.RFC3339,
	}
	// First try a normalized form: drop a trailing 'Z' if a numeric offset is present.
	normalized := value
	if len(normalized) > 6 {
		tail := normalized[len(normalized)-6:]
		// matches +0000Z or -0000Z (offset followed by Z)
		if (tail[0] == '+' || tail[0] == '-') && tail[5] == 'Z' {
			normalized = normalized[:len(normalized)-1]
		}
	}
	for _, layout := range layouts {
		if t, err := time.Parse(layout, normalized); err == nil {
			return t, nil
		}
		if t, err := time.Parse(layout, value); err == nil {
			return t, nil
		}
	}
	return time.Time{}, fmt.Errorf("unrecognized timestamp format: %q", value)
}
