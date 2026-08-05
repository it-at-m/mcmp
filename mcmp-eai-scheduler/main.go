package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"log"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/euerla/goawx/client"
	"github.com/google/uuid"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/app"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/db"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/lock"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-scheduler/pkg/clients/foreman"
	"github.com/it-at-m/mcmp/mcmp-eai-scheduler/pkg/clients/mail"
	"github.com/it-at-m/mcmp/mcmp-eai-scheduler/pkg/clients/siem"
	"github.com/it-at-m/mcmp/mcmp-eai-scheduler/pkg/clients/snow"
)

// Global debug flag that controls verbose logging throughout the application
var (
	logger                    *logging.StructuredLogger
	berlinLocation            *time.Location
	changeRegex               = regexp.MustCompile(`(?i)\${CHANGE}`)
	ansiRegex                 = regexp.MustCompile("\x1b\\[[0-9;]*[a-zA-Z]")
	ErrWrongNumberOfArguments = errors.New("wrong number of arguments")
)

// Application name constant used for configuration file naming and identification
const (
	appname            = "mcmp-eai-scheduler"
	ChangeTypeNormal   = "normal"
	ChangeTypeStandard = "standard"
)

// Configuration structs that define the structure of the TOML configuration file
// Viper uses These structs to unmarshal the configuration into Go structs

// General contains general application settings
type General struct {
	Debug                      bool // Enables debug logging when set to true
	CallbackUrlChange          string
	CallbackUrlQuickDiscovery  string
	DefaultServiceNowUserSysId string
	MaxChangeRequestsPerMinute int
}

type Database struct {
	DSN        string
	Username   string
	Password   string
	Passphrase string
}

type ForemanConfig struct {
	Username    string
	Password    string
	ApiEndpoint string
}

type SmtpConfig struct {
	Server        string
	Port          int
	Username      string
	Password      string
	ToNonOSS      string
	ToNonPostgres string
	CC            string
	Subject       string
}

// Config is the root configuration structure that combines all configuration sections
// This structure mirrors the TOML configuration file format
type Config struct {
	GENERAL  General // General application settings
	FOREMAN  ForemanConfig
	DATABASE Database
	SMTP     SmtpConfig
	SIEM     siem.SiemConfig
	LOGGING  logging.LogConfig
}

func (cfg *Config) validateConfig() error {
	if cfg.GENERAL.CallbackUrlChange == "" {
		return fmt.Errorf("callback URL for Change Ticket is not configured")
	}
	if cfg.GENERAL.CallbackUrlQuickDiscovery == "" {
		return fmt.Errorf("callback URL for Quick Discovery is not configured")
	}
	if cfg.GENERAL.DefaultServiceNowUserSysId == "" {
		return fmt.Errorf("default ServiceNow user sys_id is not configured")
	}
	return nil
}

func init() {
	loc, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		panic(fmt.Sprintf("failed to load time location Europe/Berlin: %v", err))
	}
	berlinLocation = loc
}

// the main is the entry point of the application
// It handles command line argument parsing and delegates to the run function
// The application expects no command line arguments and will show usage if any are provided
func main() {
	app.Bootstrap(func(ctx context.Context) error {
		// Parse command line flags
		flag.Usage = func() {
			exePath, err := os.Executable()
			if err != nil {
				_, _ = fmt.Fprintf(os.Stderr, "failed to get executable path: %v\n", err)
			}
			_, _ = fmt.Fprintf(flag.CommandLine.Output(), "Usage: %s\n", filepath.Base(exePath))
			flag.PrintDefaults()
		}
		flag.Parse()

		if len(os.Args) != 1 {
			return ErrWrongNumberOfArguments
		}

		// Execute main application logic
		return run()
	})
}

// Run executes the main application logic
// This function orchestrates the entire data synchronization process:
// 1. Loads configuration from the TOML file
// 2. Creates ServiceNow client and processes application services
// 3. Retrieves and exports ServiceNow data
// 4. Authenticates with Keycloak to get an access token
// 5. Sends processed data to MCMP API
func run() error {
	release, err := lock.Acquire(appname)
	if err != nil {
		return fmt.Errorf("failed to acquire lock: %w", err)
	}
	defer release()

	// Load configuration from TOML file using the generic ReadConfig function
	cfg, err := config.LoadConfig[Config](appname)
	if err != nil {
		return fmt.Errorf("failed to load config: %w", err)
	}

	// Initialize Logger using the centralized setup from common
	logger, err = logging.SetupGlobalLogger(cfg.LOGGING)
	if err != nil {
		return fmt.Errorf("failed to initialize logger: %w", err)
	}

	if err := cfg.validateConfig(); err != nil {
		return err
	}

	siemLogger := siem.NewSiemLogger(cfg.SIEM)
	if cfg.SIEM.Enabled {
		logger.DebugPrintf("SIEM Logger initialized. File: %s, Syslog: %s:%d", cfg.SIEM.File.Filename, cfg.SIEM.Syslog.Host, cfg.SIEM.Syslog.Port)
	}

	foremanClient, err := createForemanClient(cfg)
	if err != nil {
		return fmt.Errorf("failed to create Foreman client: %w", err)
	}

	mcmpClient, err := db.New(cfg.DATABASE.Username, cfg.DATABASE.Password, cfg.DATABASE.DSN, cfg.DATABASE.Passphrase, cfg.GENERAL.Debug, logger.GetWriter())
	if err != nil {
		return fmt.Errorf("failed to create MCMP client: %w", err)
	}
	defer func(mcmpClient *db.Client) {
		err := mcmpClient.Close()
		if err != nil {
			logger.Error("Failed to close MCMP client", "error", err)
		}
	}(mcmpClient)

	configAwxList, err := mcmpClient.GetAllConfigAwx()
	if err != nil {
		return fmt.Errorf("failed to get all config awx: %w", err)
	}
	awxClients := make(map[int64]*awx.AWX)
	for _, configAwx := range configAwxList {
		if configAwx.Enabled {
			awxClient, err := awx.NewAWX(configAwx.ApiEndpoint, configAwx.ApiUsername, configAwx.ApiPassword, nil)
			if err != nil {
				logger.DebugPrintf("Failed to create AWX client for awx ID %d: %v", configAwx.ID, err)
				continue
			}
			awxClients[configAwx.ID] = awxClient
			logger.DebugPrintf("Created AWX client for client ID: %d", configAwx.ID)
		}
	}

	configSnowList, err := mcmpClient.GetAllConfigSnow()
	if err != nil {
		return fmt.Errorf("failed to get all config snow: %w", err)
	}
	snowClients := make(map[int64]*snow.Client)
	for _, configSnow := range configSnowList {
		if configSnow.Enabled {
			snowConfig := snow.ClientConfig{
				Debug:           cfg.GENERAL.Debug,
				AuthServerURL:   configSnow.ApiClientAuthUrl,
				ClientID:        configSnow.ApiClientID,
				ClientSecret:    configSnow.ApiClientSecret,
				ApiEndpoint:     configSnow.ApiEndpoint,
				ProxyURL:        configSnow.Proxy,
				EnableTLSVerify: true,
				RequestTimeout:  30 * time.Second,
				Scopes:          []string{},
			}
			snowClient, err := snow.NewClient(snowConfig)
			if err != nil {
				logger.DebugPrintf("Failed to create ServiceNow client for snow ID %d: %v", configSnow.ID, err)
				continue
			}
			snowClients[configSnow.ID] = snowClient
			logger.DebugPrintf("Created ServiceNow client for client ID: %d", configSnow.ID)
		}
	}
	return processJobs(cfg, mcmpClient, awxClients, snowClients, foremanClient, siemLogger)
}

func processJobs(cfg *Config, mcmpClient *db.Client, awxClients map[int64]*awx.AWX, snowClients map[int64]*snow.Client, foremanClient *foreman.Client, siemLogger *siem.SiemLogger) error {
	callbackUrlChange := ensureTrailingSlash(cfg.GENERAL.CallbackUrlChange)
	callbackUrlQuickDiscovery := ensureTrailingSlash(cfg.GENERAL.CallbackUrlQuickDiscovery)

	// Counter for created change tickets
	createdChangeTickets := 0
	maxChangeTickets := cfg.GENERAL.MaxChangeRequestsPerMinute
	if maxChangeTickets <= 0 {
		// If not configured or invalid, use a safe default
		maxChangeTickets = 10
		logger.DebugPrintf("MaxChangeRequestsPerMinute not configured or invalid, using default: %d", maxChangeTickets)
	}

	jobs, err := mcmpClient.GetJobsByStatus()
	if err != nil {
		return fmt.Errorf("failed to get jobs: %w", err)
	}

	// Before the job loop: Map to track running AWX jobs per server
	runningAwxJobsByServer := make(map[int64]bool)

	// First, identify all running AWX jobs per server
	for _, job := range jobs {
		if job.ServerID != nil && job.Status == db.JobStatusAwxRunning {
			runningAwxJobsByServer[*job.ServerID] = true
		}
	}

	for _, job := range jobs {
		titleVal := "<nil>"
		if job.Title != nil {
			titleVal = *job.Title
		}
		logger.Info("--------------------------------------------------------------------------------")
		logger.Info("PROCESSING JOB", "id", job.ID, "status", job.Status, "title", titleVal)
		logger.Info("  Details", "title", titleVal, "change", job.ChangeStatus, "awx", job.AwxStatus, "quickDiscovery", job.QuickDiscoveryStatus, "tagging", job.TaggingStatus, "email", job.NonPostgresEmailStatus)

		if job.Status == db.JobStatusWaitingForApproval {
			logger.Info("  -> Job is waiting for approval. Skipping.", "id", job.ID)
			continue
		}
		if job.ServerInstallation {
			_, err := getSnowClientForJob(snowClients, job)
			if err != nil {
				logger.Warn("  -> Error getting ServiceNow client for job", "id", job.ID, "error", err)
				handleMissingConfig(mcmpClient, job)
				continue
			}
		}

		if job.Status == db.JobStatusNew || job.Status == db.JobStatusWaitingForServiceNowEnablement || job.Status == db.JobStatusWaitingForServiceNowConfiguration {
			if job.ChangeRequired == true {
				// Check if we have reached the limit for this program run
				if createdChangeTickets >= maxChangeTickets {
					logger.Info("  -> Status 'New' & Change Required: Skipping Job - Max change tickets limit reached", "id", job.ID, "limit", maxChangeTickets)
					continue
				}

				logger.Info("  -> Status 'New' & Change Required: Creating ServiceNow Change Ticket for Job", "id", job.ID)
				createSnowChangeTicket(callbackUrlChange, mcmpClient, snowClients, job, cfg.GENERAL.DefaultServiceNowUserSysId)
				// Only increment counter if ticket was actually created
				// Check if the status changed to waiting_for_approval
				if job.ChangeStatus == db.ChangeStatusWaitingForApproval {
					createdChangeTickets++
					logger.Info("  -> Successfully created change ticket", "current", createdChangeTickets, "limit", maxChangeTickets, "id", job.ID)
				}
				continue
			} else {
				now := time.Now().In(berlinLocation)
				if job.ChangeStartDate == nil || (job.ChangeStartDate != nil && now.After(*job.ChangeStartDate)) {
					logger.Info("  -> Status 'New' & Change Not Required: Auto-approving Job", "id", job.ID)
					job.ChangeStatus = db.ChangeStatusSkipped
					job.Status = db.JobStatusApproved
					err = mcmpClient.UpdateJob(job)
					if err != nil {
						logger.Error("Failed to update job", "id", job.ID, "error", err)
					}
				} else {
					logger.Info("  -> Status 'New' & Change Not Required: Waiting for ChangeStartDate for Job", "id", job.ID)
					continue
				}
			}
		}
		if job.Status == db.JobStatusApproved || job.Status == db.JobStatusWaitingForAwxEnablement || job.Status == db.JobStatusWaitingForAwxConfiguration {
			logger.Info("  -> Status 'Approved': Initiating AWX Job for Job", "id", job.ID)

			// Check if an AWX job is already running for this server
			if job.ServerID != nil && runningAwxJobsByServer[*job.ServerID] {
				logger.Info("  --> Skipping AWX job creation - another AWX job is already running for server", "jobId", job.ID, "serverId", *job.ServerID)
				continue
			}

			logVmStatusChange(siemLogger, job, cfg.GENERAL.DefaultServiceNowUserSysId)

			err = createAwxJob(mcmpClient, awxClients, job)
			if err != nil {
				logger.Error("Failed to create AWX job for job ID", "id", job.ID, "error", err)
				job.AwxError = new(err.Error())
				job.Title = replaceVariables(job.ActionErrorTitle, err.Error())
				job.Description = replaceVariables(job.ActionErrorDescription, err.Error())
				err = mcmpClient.UpdateJob(job)
				if err != nil {
					logger.Error("Failed to update job", "id", job.ID, "error", err)
				}
			} else {
				if job.ServerInstallation {
					// Send email to ITA and OpenSource
					sendNonPostgresEmail(cfg, mcmpClient, job)
				}
				// AWX job successfully started - mark server as "busy"
				if job.ServerID != nil {
					runningAwxJobsByServer[*job.ServerID] = true
				}
			}
			continue
		}
		if job.Status == db.JobStatusAwxRunning {
			logger.Info("  -> Status 'AWX Running': Checking progress for Job", "id", job.ID)
			isRunning, err := getAwxJobStatus(mcmpClient, awxClients, job)
			if err != nil {
				logger.Error("Failed to get AWX Status for job ID", "id", job.ID, "error", err)
				continue
			}
			if isRunning {
				continue
			}
		}
		if job.Status == db.JobStatusAwxCompleted {
			logger.Info("  -> Status 'AWX Completed': Checking for Quick Discovery requirement for Job", "id", job.ID)
			if job.AwxStatus != db.AwxStatusCanceled && (job.ServerInstallation || job.QuickDiscovery) {
				err = startQuickDiscovery(callbackUrlQuickDiscovery, mcmpClient, snowClients, job)
				if err != nil {
					logger.Error("Failed to start Quick Discovery for job ID", "id", job.ID, "error", err)
				}
				continue
			} else {
				job.QuickDiscoveryStatus = db.QuickdiscoveryStatusSkipped
				job.Status = db.JobStatusQuickdiscoveryCompleted
				err = mcmpClient.UpdateJob(job)
				if err != nil {
					logger.Error("Failed to update job", "id", job.ID, "error", err)
				}
			}
		}

		if job.Status == db.JobStatusWaitingForQuickdiscovery {
			logger.Info("  -> Status 'Waiting for Quick Discovery': Job is pending external callback", "id", job.ID)
			continue
		}

		if job.Status == db.JobStatusQuickdiscoveryFailed {
			logger.Info("  -> Status 'Quick Discovery Failed': Checking retry limits for Job", "id", job.ID)
			if job.QuickDiscoveryErrorCounter < 10 {
				job.QuickDiscoveryErrorCounter += 1
				job.QuickDiscoveryStatus = db.QuickdiscoveryStatusNew
				job.Status = db.JobStatusWaitingForQuickdiscovery
				err = mcmpClient.UpdateJob(job)
				if err != nil {
					logger.Error("Failed to update job", "id", job.ID, "error", err)
				} else {
					err = startQuickDiscovery(callbackUrlQuickDiscovery, mcmpClient, snowClients, job)
					if err != nil {
						logger.Error("Failed to start Quick Discovery for job ID", "id", job.ID, "error", err)
					}
				}
				continue
			} else {
				job.Status = db.JobStatusQuickdiscoveryCompleted
				job.Title = replaceVariables(job.ActionErrorTitle, "Quick Discovery failed")
				job.Description = replaceVariables(job.ActionErrorDescription, "Quick Discovery failed")
				err = mcmpClient.UpdateJob(job)
				if err != nil {
					logger.Error("Failed to update job", "id", job.ID, "error", err)
				}
			}
		}

		if job.Status == db.JobStatusQuickdiscoveryCompleted {
			logger.Info("  -> Status 'Quick Discovery Completed': Proceeding to Tagging for Job", "id", job.ID)
			if job.ServerInstallation {
				err = tagCI(mcmpClient, snowClients, foremanClient, job)
				if err != nil {
					logger.Error("Failed to Tag CI for job ID", "id", job.ID, "error", err)
				}
			} else {
				job.TaggingStatus = db.TaggingStatusSkipped
				job.Status = db.JobStatusTaggingCompleted
				err = mcmpClient.UpdateJob(job)
				if err != nil {
					logger.Error("Failed to update job", "id", job.ID, "error", err)
				}
			}
		}

		if job.Status == db.JobStatusTaggingFailed {
			logger.Info("  -> Status 'Tagging Failed': Marking Job as completed (with errors)", "id", job.ID)
			job.Status = db.JobStatusTaggingCompleted
			job.Title = replaceVariables(job.ActionErrorTitle, "Tagging failed")
			job.Description = replaceVariables(job.ActionErrorDescription, "Tagging failed")
			err = mcmpClient.UpdateJob(job)
			if err != nil {
				logger.Error("Failed to update job", "id", job.ID, "error", err)
			}
		}

		if job.Status == db.JobStatusTaggingCompleted {
			log.Printf("  -> Status 'Tagging Completed': Finalizing Job %d", job.ID)
			errMsg := ""
			errFlag := false
			if job.AwxStatus == db.AwxStatusError || job.AwxStatus == db.AwxStatusFailed || job.AwxStatus == db.AwxStatusCanceled {
				awxErr := ""
				if job.AwxError != nil {
					awxErr = *job.AwxError
				}
				errMsg = fmt.Sprintf("AWX-Status: %s\nAWX-Error: %s\n", job.AwxStatus, awxErr)
				errFlag = true
			}
			if job.QuickDiscoveryStatus == db.QuickdiscoveryStatusError || job.QuickDiscoveryStatus == db.QuickdiscoveryStatusCanceled || job.QuickDiscoveryStatus == db.QuickdiscoveryStatusFailed {
				qdErr := ""
				if job.QuickDiscoveryError != nil {
					qdErr = *job.QuickDiscoveryError
				}
				errMsg = fmt.Sprintf("QuickDiscovery-Status: %s\nQuickDiscovery-Error: %s\n", job.QuickDiscoveryStatus, qdErr)
				errFlag = true
			}
			if job.TaggingStatus == db.TaggingStatusError || job.TaggingStatus == db.TaggingStatusCanceled || job.TaggingStatus == db.TaggingStatusFailed {
				tagErr := ""
				if job.TaggingError != nil {
					tagErr = *job.TaggingError
				}
				errMsg = fmt.Sprintf("Tagging-Status: %s\nTagging-Error: %s\n", job.TaggingStatus, tagErr)
				errFlag = true
			}

			job.JobEndDate = new(time.Now().In(berlinLocation))
			if errFlag {
				job.Status = db.JobStatusFailed
				job.Title = replaceVariables(job.ActionErrorTitle, errMsg)
				job.Description = replaceVariables(job.ActionErrorDescription, errMsg)
				job.Notification = true
				if job.ChangeRequired {
					err = closeSnowChangeTicket(snow.Unsuccessful, *job.Description, snowClients, job)
					if err != nil {
						logger.Warn("Failed to close ServiceNow Change Ticket for job ID", "id", job.ID, "error", err)
					}
				}
			} else {
				job.Status = db.JobStatusSuccessful
				job.Title = job.ActionSuccessTitle
				job.Description = job.ActionSuccessDescription
				if job.ChangeRequired {
					err = closeSnowChangeTicket(snow.Successful, *job.Description, snowClients, job)
					if err != nil {
						logger.Warn("Failed to close ServiceNow Change Ticket for job ID", "id", job.ID, "error", err)
					}
				}
			}

			err = mcmpClient.UpdateJob(job)
			if err != nil {
				logger.Error("Failed to update job at end of processing", "id", job.ID, "error", err)
			}
		}

		logger.Info("END Processing Job", "id", job.ID, "newStatus", job.Status)

	}
	return nil
}

func getAwxJobStatus(mcmpClient *db.Client, awxClients map[int64]*awx.AWX, job *db.Job) (bool, error) {
	if job.AwxEstimatedRuntime != nil && *job.AwxEstimatedRuntime > 1 && job.AwxStartDate != nil {
		estimatedEnd := job.AwxStartDate.Add(time.Duration(*job.AwxEstimatedRuntime) * time.Minute).In(berlinLocation)
		now := time.Now().In(berlinLocation)
		if now.Before(estimatedEnd) {
			if job.AwxJobId != nil {
				logger.Info("  -> AWX Job is estimated to run. Skipping status check.", "awxId", *job.AwxJobId, "mcmpId", job.ID, "until", estimatedEnd.Format(time.RFC3339))
			} else {
				logger.Info("  -> AWX Job (unknown ID) is estimated to run. Skipping status check.", "mcmpId", job.ID, "until", estimatedEnd.Format(time.RFC3339))
			}
			return true, nil
		}
	}

	awxClient, ok := awxClients[job.Awx.ID]
	if !ok {
		return false, fmt.Errorf("AWX Client is not enabled or not configured. Job ID %d", job.ID)
	}

	err := fetchAndPopulateAwxJobData(context.Background(), awxClient, job)
	if err != nil {
		return true, err
	}

	// Determine if still running based on status
	if job.AwxJobStatus != nil {
		s := *job.AwxJobStatus
		if s != "successful" && s != "failed" && s != "error" && s != "canceled" {
			logger.Info("  -> AWX Job is still running.", "awxId", *job.AwxJobId, "status", s)
			return true, nil
		}
	}

	if job.AwxTemplateType == db.AwxTemplateTypeTemplate {
		var jobSuccess bool
		var errorMessage string
		if job.AwxJobReturnCompleted == nil {
			// Fallback to jobResult.Status if no artifact info is available
			if job.AwxJobStatus != nil && *job.AwxJobStatus == "successful" {
				jobSuccess = true
			} else if job.AwxJobStatus != nil && (*job.AwxJobStatus == "canceled" || *job.AwxJobStatus == "error" || *job.AwxJobStatus == "failed") {
				jobSuccess = false
				errorMessage = fmt.Sprintf("AWX-Status: %s", *job.AwxJobStatus)
			}
		} else {
			// Use artifact information
			failed := job.AwxJobFailed != nil && *job.AwxJobFailed
			jobCompleted := *job.AwxJobReturnCompleted
			if failed && jobCompleted {
				jobSuccess = true
			} else if failed || jobCompleted == false {
				jobSuccess = false
				if jobCompleted == false && job.AwxJobReturnMessage != nil {
					errorMessage = *job.AwxJobReturnMessage
				} else {
					errorMessage = "AWX job failed"
				}
			}
		}

		if jobSuccess {
			job.Status = db.JobStatusAwxCompleted
			job.AwxStatus = db.AwxStatusSuccessful
			err = mcmpClient.UpdateJob(job)
			if err != nil {
				return false, err
			}
			return false, nil
		}

		logger.Info("  -> AWX Job failed.", "error", errorMessage)
		job.Status = db.JobStatusAwxCompleted
		job.AwxStatus = db.AwxStatusFailed
		job.AwxError = new(errorMessage)
		err = mcmpClient.UpdateJob(job)
		if err != nil {
			return false, err
		}
		return false, nil
	} else if job.AwxTemplateType == db.AwxTemplateTypeWorkflow {
		err := syncWorkflowNodes(context.Background(), awxClient, mcmpClient, job)
		if err != nil {
			return true, err
		}

		switch *job.AwxJobStatus {
		case "successful":
			job.Status = db.JobStatusAwxCompleted
			job.AwxStatus = db.AwxStatusSuccessful

		case "failed":
			logger.Info("  -> AWX Workflow Job failed.", "status", *job.AwxJobStatus)
			job.Status = db.JobStatusAwxCompleted
			job.AwxStatus = db.AwxStatusFailed
			job.AwxError = new(fmt.Sprintf("AWX-Status: %s", *job.AwxJobStatus))

			err = mcmpClient.UpdateJob(job)
			if err != nil {
				return false, err
			}
			return false, nil
		case "canceled":
			logger.Info("  -> AWX Workflow Job canceled.")
			job.Status = db.JobStatusAwxCompleted
			job.AwxStatus = db.AwxStatusCanceled
		case "error":
			logger.Info("  -> AWX Workflow Job failed.", "status", *job.AwxJobStatus)
			job.Status = db.JobStatusAwxCompleted
			job.AwxStatus = db.AwxStatusError

		}
		err = mcmpClient.UpdateJob(job)
		if err != nil {
			return true, err
		}
		return false, nil
	}
	return false, nil
}

// checkJobArtifacts checks job artifacts for completed status, msg, and data
// Returns: (completed *bool, message *string, data *string)
func checkJobArtifacts(artifacts interface{}) (*bool, *string, *string) {
	if artifacts == nil {
		return nil, nil, nil
	}

	// Convert artifacts to map for easier access
	artifactsMap, ok := artifacts.(map[string]interface{})
	if !ok {
		return nil, nil, nil
	}

	if len(artifactsMap) == 0 {
		return nil, nil, nil
	}

	// Check for job_result in artifacts
	jobResult, exists := artifactsMap["job_result"]
	if !exists {
		return nil, nil, nil
	}

	jobResultMap, ok := jobResult.(map[string]interface{})
	if !ok {
		return nil, nil, nil
	}

	var completedPtr *bool
	var messagePtr *string
	var dataPtr *string

	// Check completed status
	if completed, exists := jobResultMap["completed"]; exists {
		if completedBool, ok := completed.(bool); ok {
			completedPtr = &completedBool
		}
	}

	// Extract message if present
	if msg, exists := jobResultMap["msg"]; exists {
		if msgStr, ok := msg.(string); ok {
			messagePtr = &msgStr
		}
	}

	// Extract data if present
	if dataValue, exists := jobResultMap["data"]; exists {
		if dataMap, ok := dataValue.(map[string]interface{}); ok && len(dataMap) > 0 {
			if jsonData, err := json.Marshal(dataMap); err == nil {
				dataPtr = new(string(jsonData))
			}
		}
	}

	return completedPtr, messagePtr, dataPtr
}

func createAwxJob(mcmpClient *db.Client, awxClients map[int64]*awx.AWX, job *db.Job) error {
	logger.DebugPrintf("START - create awx job\n")
	if !job.AwxJobEnabled {
		logger.Info("Job is not a awx job. Skipping creation of awx job.", "id", job.ID)
		job.AwxStatus = db.AwxStatusSkipped
		job.Status = db.JobStatusAwxCompleted
		err := mcmpClient.UpdateJob(job)
		if err != nil {
			logger.Error("Failed to update job", "id", job.ID, "error", err)
		}
		return nil
	}
	if job.AwxID == nil || *job.AwxID == 0 {
		logger.Info("Awx ID is not configured for job. Skipping creation of awx job.", "id", job.ID)
		if job.Status != db.JobStatusWaitingForAwxConfiguration {
			job.Status = db.JobStatusWaitingForAwxConfiguration
			err := mcmpClient.UpdateJob(job)
			if err != nil {
				logger.Error("Failed to update job", "id", job.ID, "error", err)
			}
		}
		return nil
	}
	awxClient, ok := awxClients[job.Awx.ID]
	if !ok {
		logger.Info("AWX Client is not enabled for job. Skipping creation of awx job.", "id", job.ID)
		if job.Status != db.JobStatusWaitingForAwxEnablement {
			job.Status = db.JobStatusWaitingForAwxEnablement
			err := mcmpClient.UpdateJob(job)
			if err != nil {
				logger.Error("Failed to update job", "id", job.ID, "error", err)
			}
		}
		return nil
	}
	logger.DebugPrintf("create awx job %+v\n", job)
	jobTemplateID := *job.AwxTemplateID
	data := make(map[string]interface{})
	params := make(map[string]string)
	mcmpVars := map[string]interface{}{
		"job_id": job.ID,
	}
	additionalExtraVars := map[string]interface{}{
		"mcmp": mcmpVars,
	}

	if job.User != nil && job.User.Username != "" {
		mcmpVars["requesting_user"] = job.User.Username
	}

	if job.Server != nil {
		serverVars := make(map[string]string)
		mcmpVars["server"] = serverVars
		serverVars["fqdn"] = *job.Server.FQDN
	}

	if job.ChangeNumber != nil && *job.ChangeNumber != "" && job.ChangeLink != nil && *job.ChangeLink != "" {
		snowTickets := []map[string]interface{}{
			{
				"kind": "change",
				"id":   *job.ChangeNumber,
				"url":  *job.ChangeLink,
			},
		}
		mcmpVars["ticket_refs"] = map[string]interface{}{
			"snow": snowTickets,
		}
	}
	if job.AwxInventoryID != nil && *job.AwxInventoryID != 0 {
		data["inventory_id"] = *job.AwxInventoryID
	}
	if job.AwxCredentials != nil && *job.AwxCredentials != "" {
		values, err := parseStringToIntSlice(*job.AwxCredentials)
		if err == nil {
			data["credentials"] = values
		} else {
			logger.Warn("failed to parse credentials", "jobId", job.ID, "credentials", *job.AwxCredentials, "error", err)
		}
	}
	if job.AwxJobType != nil && *job.AwxJobType != "" {
		data["job_type"] = *job.AwxJobType
	}
	if job.AwxLimit != nil && *job.AwxLimit != "" {
		data["limit"] = *job.AwxLimit
	}
	if job.AwxJobTags != nil && *job.AwxJobTags != "" {
		data["job_tags"] = *job.AwxJobTags
	}
	if job.AwxSkipTags != nil && *job.AwxSkipTags != "" {
		data["skip_tags"] = *job.AwxSkipTags
	}
	if job.AwxExtraVars != nil && *job.AwxExtraVars != "" {
		extraVars := *job.AwxExtraVars
		// Replaces all placeholders ${CHANGE} in AwxExtraVars with the ServiceNow ticket number.
		if job.ChangeNumber != nil && *job.ChangeNumber != "" {
			extraVars = changeRegex.ReplaceAllString(extraVars, *job.ChangeNumber)
		}
		mergedVars, err := mergeExtraVars(extraVars, additionalExtraVars)
		if err != nil {
			return err
		}

		data["extra_vars"] = mergedVars
	} else {
		// Convert map to JSON string
		jsonBytes, err := json.Marshal(additionalExtraVars)
		if err != nil {
			return fmt.Errorf("fehler beim Konvertieren der extra_vars zu JSON: %v", err)
		}
		data["extra_vars"] = string(jsonBytes)
	}
	if job.AwxSCMBranch != nil && *job.AwxSCMBranch != "" {
		data["scm_branch"] = *job.AwxSCMBranch
	}
	if job.AwxVerbosity != nil && *job.AwxVerbosity != 0 {
		data["verbosity"] = *job.AwxVerbosity
	}
	if job.AwxTimeout != nil && *job.AwxTimeout != 0 {
		data["timeout"] = *job.AwxTimeout
	}
	if job.AwxForks != nil && *job.AwxForks != 0 {
		data["forks"] = *job.AwxForks
	}
	if job.AwxJobSliceCount != nil && *job.AwxJobSliceCount != 0 {
		data["job_slice_count"] = *job.AwxJobSliceCount
	}
	if job.AwxExecutionEnvironment != nil && *job.AwxExecutionEnvironment != 0 {
		data["execution_environment"] = *job.AwxExecutionEnvironment
	}
	if job.AwxInstanceGroups != nil && *job.AwxInstanceGroups != "" {
		values, err := parseStringToIntSlice(*job.AwxInstanceGroups)
		if err == nil {
			data["instance_groups"] = values
		} else {
			logger.Warn("failed to parse instance groups", "jobId", job.ID, "groups", *job.AwxInstanceGroups, "error", err)
		}
	}
	if job.AwxLabels != nil && *job.AwxLabels != "" {
		values, err := parseStringToIntSlice(*job.AwxLabels)
		if err == nil {
			data["labels"] = values
		} else {
			logger.Warn("failed to parse labels", "jobId", job.ID, "labels", *job.AwxLabels, "error", err)
		}
	}

	job.AwxStartDate = new(time.Now().In(berlinLocation))
	if job.AwxTemplateType == db.AwxTemplateTypeTemplate {
		logger.Info("  -> Launching AWX Job Template", "templateId", jobTemplateID, "mcmpId", job.ID)
		// Start Job Template
		jobResult, err := awxClient.JobTemplateService.Launch(int(jobTemplateID), data, params)
		if err != nil {
			return fmt.Errorf("failed to launch job template: %v", err)
		}

		// Save job information
		job.AwxJobId = new(int64(jobResult.ID))
		awxJobLink, err := getAwxJobURL(job.Awx.ApiEndpoint, jobResult.ID)
		if err != nil {
			logger.Warn("failed to create awx job url", "error", err)
		} else {
			job.AwxJobLink = awxJobLink
		}

		job.AwxVariables = prepareAndSortMap(data)
		job.AwxStatus = db.AwxStatusRunning
		job.Status = db.JobStatusAwxRunning
		logger.Info("  -> AWX Job launched successfully", "awxId", jobResult.ID)
	} else if job.AwxTemplateType == db.AwxTemplateTypeWorkflow {
		logger.Info("  -> Launching AWX Workflow Job Template", "templateId", jobTemplateID, "mcmpId", job.ID)
		// Start Workflow Job Template
		jobResult, err := awxClient.WorkflowJobTemplateService.Launch(int(jobTemplateID), data, params)
		if err != nil {
			return fmt.Errorf("failed to launch job template: %v", err)
		}
		// Save job information
		job.AwxJobId = new(int64(jobResult.ID))
		awxJobLink, err := getAwxWorkflowJobURL(job.Awx.ApiEndpoint, jobResult.ID)
		if err != nil {
			logger.Warn("failed to create awx workflow job url", "error", err)
		} else {
			job.AwxJobLink = awxJobLink
		}

		job.AwxVariables = prepareAndSortMap(data)
		job.AwxStatus = db.AwxStatusRunning
		job.Status = db.JobStatusAwxRunning
		logger.Info("  -> AWX Workflow Job launched successfully", "awxId", jobResult.ID)
	}

	err := mcmpClient.UpdateJob(job)
	if err != nil {
		return err
	}
	return nil
}

func extractBaseURL(fullURL string) (string, error) {
	// Parse URL
	parsedURL, err := url.Parse(fullURL)
	if err != nil {
		return "", fmt.Errorf("error parsing the URL: %w", err)
	}

	// Assemble Base-URL (Scheme + Host)
	baseURL := fmt.Sprintf("%s://%s", parsedURL.Scheme, parsedURL.Host)

	return baseURL, nil
}

func getAwxTemplateURL(awxUrl string, jobId int) (*string, error) {
	// Validate input parameter
	if awxUrl == "" {
		return nil, fmt.Errorf("apiEndpoint parameter cannot be empty")
	}
	awxBaseUrl, err := extractBaseURL(awxUrl)
	if err != nil {
		return nil, fmt.Errorf("no AWX URL for job ID %d found", jobId)
	}
	return new(fmt.Sprintf("%s/#/templates/job_template/%d", awxBaseUrl, jobId)), nil
}

func getAwxWorkflowTemplateURL(awxUrl string, jobId int) (*string, error) {
	// Validate input parameter
	if awxUrl == "" {
		return nil, fmt.Errorf("apiEndpoint parameter cannot be empty")
	}
	awxBaseUrl, err := extractBaseURL(awxUrl)
	if err != nil {
		return nil, fmt.Errorf("no AWX URL for job ID %d found", jobId)
	}
	return new(fmt.Sprintf("%s/#/templates/workflow_job_template/%d", awxBaseUrl, jobId)), nil
}

func getAwxJobURL(awxUrl string, jobId int) (*string, error) {
	// Validate input parameter
	if awxUrl == "" {
		return nil, fmt.Errorf("apiEndpoint parameter cannot be empty")
	}
	awxBaseUrl, err := extractBaseURL(awxUrl)
	if err != nil {
		return nil, fmt.Errorf("no AWX URL for job ID %d found", jobId)
	}
	return new(fmt.Sprintf("%s/#/jobs/playbook/%d/output", awxBaseUrl, jobId)), nil
}

func getAwxWorkflowJobURL(awxUrl string, jobId int) (*string, error) {
	// Validate input parameter
	if awxUrl == "" {
		return nil, fmt.Errorf("apiEndpoint parameter cannot be empty")
	}
	awxBaseUrl, err := extractBaseURL(awxUrl)
	if err != nil {
		return nil, fmt.Errorf("no AWX URL for job ID %d found", jobId)
	}
	return new(fmt.Sprintf("%s/#/jobs/workflow/%d/output", awxBaseUrl, jobId)), nil
}

func closeSnowChangeTicket(changeCloseCode snow.ChangeCloseCode, changeCloseNote string, snowClients map[int64]*snow.Client, job *db.Job) error {
	logger.DebugPrintf("close snow change %+v\n", job)
	if job.ChangeRequired {
		snowClient, ok := snowClients[job.Snow.ID]
		if !ok {
			return fmt.Errorf("ServiceNow Client is not enabled or not configured. Job ID %d", job.ID)
		}
		if job.ServerInstallation {
			err := addCiToChange(snowClients, job)
			if err != nil {
				logger.Warn("Failed to add CI to change ticket.", "jobId", job.ID, "error", err)
			}
		}
		closeChangeRequest := snow.ChangeCloseRequest{
			CloseCode:       changeCloseCode.String(),
			CloseNotes:      changeCloseNote,
			ActualStartDate: formatChangeDate(job.AwxStartDate),
			ActualEndDate:   formatChangeDate(new(time.Now().In(berlinLocation))),
		}
		_, err := snowClient.CloseChangeTicket(*job.ChangeSysId, closeChangeRequest)
		if err != nil {
			return err
		}
	}
	return nil
}

func addCiToChange(snowClients map[int64]*snow.Client, job *db.Job) error {
	logger.DebugPrintf("add ci to change %+v\n", job)

	val := "nil"
	if job.QuickDiscoveryCiSysid != nil {
		val = *job.QuickDiscoveryCiSysid
	}
	logger.DebugPrintf("DEBUG: ChangeRequired=%v, ServerInstallation=%v, QuickDiscoveryCiSysid=%s", job.ChangeRequired, job.ServerInstallation, val)

	if job.ChangeRequired && job.ServerInstallation && job.QuickDiscoveryCiSysid != nil && *job.QuickDiscoveryCiSysid != "" {
		snowClient, ok := snowClients[job.Snow.ID]
		if !ok {
			return fmt.Errorf("ServiceNow Client is not enabled or not configured. Job ID %d", job.ID)
		}
		addCi := snow.ChangeAddCiRequest{
			CI: *job.QuickDiscoveryCiSysid,
		}
		err := snowClient.AddCiToChangeTicket(*job.ChangeSysId, addCi)
		if err != nil {
			return err
		}
	}
	return nil
}

// resolveFQDNToIP resolves an FQDN (Fully Qualified Domain Name) to an IP address
// The function uses DNS resolution to determine the IP address
//
// Parameters:
//   - fqdn: The fully qualified domain name (e.g., "server.example.com")
//
// Returns:
//   - string: The resolved IP address as a string
//   - error: Error if resolution fails or no IP was found
func resolveFQDNToIP(fqdn string) (string, error) {
	if fqdn == "" {
		return "", fmt.Errorf("FQDN must not be empty")
	}

	// Perform DNS resolution
	ips, err := net.LookupIP(fqdn)
	if err != nil {
		return "", fmt.Errorf("DNS resolution for '%s' failed: %v", fqdn, err)
	}

	// Check if at least one IP address was found
	if len(ips) == 0 {
		return "", fmt.Errorf("no IP address found for '%s'", fqdn)
	}

	// Return the first IP address found
	return ips[0].String(), nil
}

// tagCI processes the tagging functionality by validating inputs, tagging CIs, updating job statuses, and handling errors.
func tagCI(mcmpClient *db.Client, snowClients map[int64]*snow.Client, foremanClient *foreman.Client, job *db.Job) error {
	var snowClient *snow.Client
	logger.DebugPrintf("**************************************************")
	logger.DebugPrintf("* JobID %d : START - tagging", job.ID)
	logger.DebugPrintf("**************************************************")

	// Validation
	errorMsg := ""
	if mcmpClient == nil {
		return fmt.Errorf("MCMP-Client ist not set.\n")
	}
	if snowClients == nil {
		errorMsg += "snowClients is not set.\n"
	}
	if foremanClient == nil {
		errorMsg += "foremanClient is not set.\n"
	}
	if job.SnowID == nil || *job.SnowID == 0 {
		errorMsg = "Snow ID is not set.\n"
	} else {
		if snowClients != nil {
			var ok bool
			snowClient, ok = snowClients[*job.SnowID]
			if !ok {
				errorMsg += fmt.Sprintf("ServiceNow Client ID %d is not enabled or not configured.\n", job.SnowID)
			}
		}
	}
	if job.QuickDiscoveryCiSysid == nil || *job.QuickDiscoveryCiSysid == "" {
		errorMsg += "Quick Discovery CI sys_id is not set.\n"
	}
	if job.Appservice == nil || job.Appservice.Number == "" {
		errorMsg += "AppService Number is not set.\n"
	}
	if job.Hostname == nil || *job.Hostname == "" {
		errorMsg += "Hostname is not set.\n"
	}
	if !job.ServerInstallation {
		errorMsg += "Server Installation is not set.\n"
	}
	if job.TaggingStatus != db.TaggingStatusNew {
		errorMsg += fmt.Sprintf("Tagging Status is not set to 'new'. Current Tagging Status = '%s'\n", job.TaggingStatus)
	}

	if snowClient == nil || errorMsg != "" {
		logger.Info("  -> Tagging validation failed", "error", errorMsg)
		job.TaggingError = &errorMsg
		job.Status = db.JobStatusTaggingFailed
		job.TaggingStatus = db.TaggingStatusFailed
		err := mcmpClient.UpdateJob(job)
		if err != nil {
			logger.Error("Failed to update job", "id", job.ID, "error", err)
		}
		return nil
	}

	job.TaggingError = nil

	// Error storage
	var collectedErrors []string
	serverCiError := false
	vmwareCiError := false

	// 1) Tag Quick Discovery CI
	err := tagQuickDiscoveryCI(snowClient, job)
	if err != nil {
		collectedErrors = append(collectedErrors, err.Error())
		serverCiError = true
	}

	// 2) Tag VMWare Instance CI
	err = tagVmwareInstanceCI(mcmpClient, snowClient, foremanClient, job)
	if err != nil {
		collectedErrors = append(collectedErrors, err.Error())
		vmwareCiError = true
	}

	if len(collectedErrors) > 0 {
		fullErrorMsg := strings.Join(collectedErrors, "\n")
		logger.Info("  -> Tagging errors occurred", "errors", fullErrorMsg)
		job.TaggingError = &fullErrorMsg
	} else {
		job.TaggingError = nil
	}

	// Status Update based on errors
	// Only if both serverCiError and vmwareCiError are incorrect should an error be set.
	if serverCiError && vmwareCiError {
		job.Status = db.JobStatusTaggingFailed
		job.TaggingStatus = db.TaggingStatusFailed
	} else {
		job.Status = db.JobStatusTaggingCompleted
		job.TaggingStatus = db.TaggingStatusSuccessful
	}

	err = mcmpClient.UpdateJob(job)
	if err != nil {
		logger.Error("Failed to update job", "id", job.ID, "error", err)
	}

	logger.DebugPrintf("**************************************************")
	logger.DebugPrintf("* JobID %d : END - tagging", job.ID)
	logger.DebugPrintf("**************************************************")
	return nil
}

func tagQuickDiscoveryCI(snowClient *snow.Client, job *db.Job) error {
	logger.DebugPrintf(" - Tag CI sys_id = '%s' to AppService Number '%s'\n", *job.QuickDiscoveryCiSysid, job.Appservice.Number)
	err := snowClient.PostTag(job.Appservice.Number, *job.QuickDiscoveryCiSysid)
	if err != nil {
		return fmt.Errorf(" Failed to tag CI sys_id '%s' to AppService Number '%s', Error: %w", *job.QuickDiscoveryCiSysid, job.Appservice.Number, err)
	}
	return nil
}

func tagVmwareInstanceCI(mcmpClient *db.Client, snowClient *snow.Client, foremanClient *foreman.Client, job *db.Job) error {
	logger.DebugPrintf(" - Find VMWare Instance CI for Hostname '%s'\n", *job.Hostname)
	logger.DebugPrintf(" -- Determine UUID for the host '%s' using the Foreman API\n", *job.Hostname)

	host, err := foremanClient.GetHost(*job.Hostname)
	if err != nil {
		return fmt.Errorf("warning: Failed to find Server with UUID in foreman. Error: %v", err)
	}

	if host.UUID == "" {
		return fmt.Errorf("warning: Failed to find Server with UUID %s in foreman", host.UUID)
	}

	logger.DebugPrintf(" -- Foreman UUID = %s\n", host.UUID)
	logger.DebugPrintf(" -- Search for instance UUID %s in the database\n", host.UUID)

	servers, err := mcmpClient.FindServerByInstanceUUID(host.UUID)
	if err != nil {
		return fmt.Errorf("warning: Failed to find Server with UUID %s in the database %v", host.UUID, err)
	}

	if len(servers) != 1 {
		return fmt.Errorf("%d servers were found in the database with the instance UUID. A unique assignment is not possible", len(servers))
	}

	// Server Setup & Updates
	server := servers[0]
	job.ServerID = &server.ID

	// We update the job here temporarily to secure the ServerID connection,
	// even if errors occur later
	if err := mcmpClient.UpdateJob(job); err != nil {
		logger.Error("Failed to update job with server ID", "id", job.ID, "error", err)
	}

	server.SnowServerSysID = job.QuickDiscoveryCiSysid
	server.SnowServerSysClass = new("cmdb_ci_server")
	if err := mcmpClient.UpdateServer(&server); err != nil {
		logger.Error("Failed to update server", "id", server.ID, "error", err)
	}

	serverAssignment := db.ServerAssignment{
		ServerID:     server.ID,
		AppserviceID: job.Appservice.ID,
	}
	if err := mcmpClient.SaveServerAssignment(&serverAssignment); err != nil {
		logger.Error("Failed to save server assignment", "error", err)
	}

	logger.DebugPrintf(" -- Found server with ID %d, name %s, uuid %s, instance uuid %s for host %s\n", server.ID, server.Name, server.UUID, *server.InstanceUUID, *job.Hostname)
	logger.DebugPrintf(" -- Determine sys_id for the VMware instance in Service Now using bios_uuid = %s\n", server.UUID)

	vmwareInstanceCIs, err := snowClient.FindVMwareInstance(server.UUID)
	if err != nil {
		return fmt.Errorf("warning: Failed to get detailed information for UUID %s from snow: %v", server.UUID, err)
	}

	if vmwareInstanceCIs == nil || len(vmwareInstanceCIs) != 1 {
		count := 0
		if vmwareInstanceCIs != nil {
			count = len(vmwareInstanceCIs)
		}
		return fmt.Errorf("%d vmware_instance CIs were found in the database with the UUID. A unique assignment is not possible", count)
	}

	vmwareInstanceCI := vmwareInstanceCIs[0]
	logger.DebugPrintf(" -- Found vmwareInstanceCI with name %s and sys_id %s and vm instance uuid %s\n", vmwareInstanceCI.Name, vmwareInstanceCI.SysId, vmwareInstanceCI.VmInstanceUUID)
	logger.DebugPrintf(" -- Tag CI sys_id = '%s' to AppService Number '%s'\n", vmwareInstanceCI.SysId, job.Appservice.Number)

	server.SnowInstanceSysID = new(vmwareInstanceCI.SysId)
	server.SnowInstanceSysClass = new("cmdb_ci_vmware_instance")
	if err := mcmpClient.UpdateServer(&server); err != nil {
		logger.Error("Failed to update server", "id", server.ID, "error", err)
	}

	err = snowClient.PostTag(job.Appservice.Number, vmwareInstanceCI.SysId)
	if err != nil {
		return fmt.Errorf("failed to tag CI sys_id '%s' to AppService Number '%s': %v", *job.QuickDiscoveryCiSysid, job.Appservice.Number, err)
	}

	return nil
}

func startQuickDiscovery(mcmpCallbackUrlQuickDiscovery string, mcmpClient *db.Client, snowClients map[int64]*snow.Client, job *db.Job) error {
	logger.DebugPrintf("START - quick discovery\n")

	// 1. Check if QuickDiscovery is necessary at all
	if !(job.QuickDiscovery || job.ServerInstallation) {
		logger.Info("Job: Quick Discovery or Server Installation is not set.", "id", job.ID)
		job.QuickDiscoveryStatus = db.QuickdiscoveryStatusSkipped
		job.Status = db.JobStatusQuickdiscoveryCompleted
		err := mcmpClient.UpdateJob(job)
		if err != nil {
			logger.Error("Failed to update job", "id", job.ID, "error", err)
		}
		return nil
	}

	// 2. Only process jobs in "New" status
	if job.QuickDiscoveryStatus != db.QuickdiscoveryStatusNew {
		return nil
	}

	// Ensure flag is set
	if !job.QuickDiscovery {
		job.QuickDiscovery = true
	}

	// 3. Hostname Validation
	if job.Hostname == nil || *job.Hostname == "" {
		logger.DebugPrintf("quick discovery hostname not set\n")
		job.QuickDiscoveryStatus = db.QuickdiscoveryStatusFailed
		job.Status = db.JobStatusQuickdiscoveryFailed
		job.QuickDiscoveryErrorCounter = 100

		errMsg := "hostname not set"
		if err := updateQuickDiscoveryError(mcmpClient, job, errMsg); err != nil {
			logger.Error("Failed to update job", "id", job.ID, "error", err)
		}
		return errors.New(errMsg)
	}

	logger.DebugPrintf("quick discovery hostname %s\n", *job.Hostname)

	// 4. DNS Resolution
	ip, err := resolveFQDNToIP(*job.Hostname)
	if err != nil {
		logger.DebugPrintf("DNS resolution error: %v\n", err)
		job.QuickDiscoveryStatus = db.QuickdiscoveryStatusFailed
		job.Status = db.JobStatusQuickdiscoveryFailed

		if updateErr := updateQuickDiscoveryError(mcmpClient, job, err.Error()); updateErr != nil {
			logger.Error("Failed to update job", "id", job.ID, "error", updateErr)
		}
		return err
	}

	logger.DebugPrintf("IP-Address: %s\n", ip)

	// 5. Get ServiceNow Client
	snowClient, ok := snowClients[job.Snow.ID]
	if !ok {
		return fmt.Errorf("ServiceNow Client is not enabled or not configured. Job ID %d", job.ID)
	}

	// 6. Perform Quick Discovery Request
	quickDiscoveryRequest := snow.QuickDiscoveryRequest{
		CallbackURL: fmt.Sprintf("%s%d", mcmpCallbackUrlQuickDiscovery, job.ID),
		DiscoveryIP: ip,
	}
	err = snowClient.QuickDiscovery(quickDiscoveryRequest)
	if err != nil {
		logger.Warn("Failed to start quick discovery for job ID", "id", job.ID, "error", err)
		if updateErr := updateQuickDiscoveryError(mcmpClient, job, err.Error()); updateErr != nil {
			logger.Error("Failed to update job", "id", job.ID, "error", updateErr)
		}
		return err
	}

	// 7. Successful update
	job.IP = &ip
	job.QuickDiscoveryStatus = db.QuickdiscoveryStatusWaiting
	job.Status = db.JobStatusWaitingForQuickdiscovery
	err = mcmpClient.UpdateJob(job)
	if err != nil {
		logger.Error("Failed to update job", "id", job.ID, "error", err)
		return err
	}

	logger.DebugPrintf("END - quick discovery\n")
	return nil
}

// updateQuickDiscoveryError updates the job with an error message
func updateQuickDiscoveryError(mcmpClient *db.Client, job *db.Job, errorMsg string) error {
	if job.QuickDiscoveryError == nil || *job.QuickDiscoveryError == "" {
		job.QuickDiscoveryError = &errorMsg
	} else {
		job.QuickDiscoveryError = new(*job.QuickDiscoveryError + "\n" + errorMsg)
	}
	return mcmpClient.UpdateJob(job)
}

func createSnowChangeTicket(mcmpCallbackUrlChange string, mcmpClient *db.Client, snowClients map[int64]*snow.Client, job *db.Job, defaultServiceNowUserSysId string) {
	logger.DebugPrintf("START - create snow change\n")

	// 1. Pre-Check: Is a change necessary at all?
	if !shouldCreateChangeTicket(job) {
		handleSkippedChange(mcmpClient, job)
		return
	}

	// 2. Validation: ServiceNow configuration and client
	snowClient, err := getSnowClientForJob(snowClients, job)
	if err != nil {
		logger.Warn("Skipping creation of snow change ticket", "error", err)
		handleMissingConfig(mcmpClient, job)
		return
	}

	logger.DebugPrintf("create snow change\n")

	// 3. Date Setup
	ensureChangeDates(job)

	// 4. Dispatcher: Call the correct function depending on Change Type
	var sysID, number string
	var changeErr error

	changeType := ""
	if job.ChangeType != nil {
		changeType = strings.ToLower(*job.ChangeType)
	}

	switch changeType {
	case ChangeTypeNormal:
		sysID, number, changeErr = performNormalChange(snowClient, mcmpCallbackUrlChange, job, defaultServiceNowUserSysId)
	case ChangeTypeStandard:
		sysID, number, changeErr = performStandardChange(snowClient, mcmpCallbackUrlChange, job, defaultServiceNowUserSysId)
	default:
		changeErr = fmt.Errorf("unknown or unsupported change type: '%s'", changeType)
	}

	// 5. Process result (Centralized error and success handling)
	if changeErr != nil {
		handleSnowChangeError(mcmpClient, job, changeErr)
		return
	}

	handleSnowChangeSuccess(mcmpClient, snowClient, job, sysID, number)
	logger.DebugPrintf("END - create snow change\n")
}

// shouldCreateChangeTicket determines if a change ticket should be created based on the job's properties and status.
func shouldCreateChangeTicket(job *db.Job) bool {
	return job.ChangeRequired &&
		(job.Status == db.JobStatusNew ||
			job.Status == db.JobStatusWaitingForServiceNowEnablement ||
			job.Status == db.JobStatusWaitingForServiceNowConfiguration)
}

// handleSkippedChange updates the job status to skipped and approved for non-change ticket jobs, then saves it to the database.
func handleSkippedChange(mcmpClient *db.Client, job *db.Job) {
	logger.Info("Job is not a change ticket. Skipping creation of snow change ticket.", "id", job.ID)
	job.ChangeStatus = db.ChangeStatusSkipped
	job.Status = db.JobStatusApproved
	if err := mcmpClient.UpdateJob(job); err != nil {
		logger.Error("Failed to update job", "id", job.ID, "error", err)
	}
}

// getSnowClientForJob retrieves the ServiceNow client for the specified job using the provided map of the client.
// It returns an error if the job's Snow ID is not set or if the corresponding client is not available.
func getSnowClientForJob(snowClients map[int64]*snow.Client, job *db.Job) (*snow.Client, error) {
	if job.SnowID == nil || *job.SnowID == 0 {
		return nil, fmt.Errorf("service now ID is not configured for job id %d", job.ID)
	}
	client, ok := snowClients[*job.SnowID]
	if !ok {
		return nil, fmt.Errorf("service now Client is not enabled for job id %d", job.ID)
	}
	return client, nil
}

// handleMissingConfig ensures the job's status is updated based on the presence and value of SnowID in the provided job.
// It updates the job to WaitingForServiceNowConfiguration if SnowID is nil or 0, else to WaitingForServiceNowEnablement.
func handleMissingConfig(mcmpClient *db.Client, job *db.Job) {
	if job.SnowID == nil || *job.SnowID == 0 {
		if job.Status != db.JobStatusWaitingForServiceNowConfiguration {
			job.Status = db.JobStatusWaitingForServiceNowConfiguration
			_ = mcmpClient.UpdateJob(job)
		}
	} else {
		if job.Status != db.JobStatusWaitingForServiceNowEnablement {
			job.Status = db.JobStatusWaitingForServiceNowEnablement
			_ = mcmpClient.UpdateJob(job)
		}
	}
}

// ensureChangeDates ensures that the job's ChangeStartDate and ChangeEndDate are set, assigning default values if nil.
func ensureChangeDates(job *db.Job) {
	now := time.Now().In(berlinLocation)
	if job.ChangeStartDate == nil {
		job.ChangeStartDate = &now
	}
	if job.ChangeEndDate == nil {
		job.ChangeEndDate = new(now.Add(1 * time.Second))
	}
}

// performNormalChange creates a normal change request in ServiceNow and returns the SysID, change number, and an error, if any.
func performNormalChange(client *snow.Client, callbackBaseUrl string, job *db.Job, defaultServiceNowUserSysId string) (string, string, error) {
	var requestedBy string
	var assignedTo string
	var assignmentGroup string

	if job.User != nil && job.User.SysID != "" {
		// User is set in the database
		requestedBy = job.User.SysID
		assignedTo = job.User.SysID
	} else {
		// User is null in the database -> use default sys_id from toml file
		requestedBy = defaultServiceNowUserSysId

		// Check if the Appservice has a ChangeGroup set
		if job.Appservice != nil && job.Appservice.ChangeGroup != nil && job.Appservice.ChangeGroup.SysID != "" {
			assignmentGroup = job.Appservice.ChangeGroup.SysID
		}
	}
	changeRequest := snow.NormalChangeRequest{
		CallbackUrl: fmt.Sprintf("%s%d", callbackBaseUrl, job.ID),
		Change: snow.NewNormalChangeObject{
			CIs:                getCISysIDs(job),
			ApplicationService: getApplicationAserviceSysID(job),
			ShortDescription:   "[MCMP] " + *replaceVariables(job.ActionTitle, ""),
			Description:        *replaceVariables(job.ActionDescription, ""),
			StartDate:          formatChangeDate(job.ChangeStartDate),
			EndDate:            formatChangeDate(job.ChangeEndDate),
			RequestedBy:        requestedBy,
			AssignedTo:         assignedTo,
			AssignmentGroup:    assignmentGroup,
			Justification:      *replaceVariables(job.ChangeJustification, ""),
			ImplementationPlan: *replaceVariables(job.ChangeImplementationPlan, ""),
			RiskImpactAnalysis: *replaceVariables(job.ChangeRiskImpactAnalysis, ""),
			BackoutPlan:        *replaceVariables(job.ChangeBackoutPlan, ""),
		},
	}
	if job.ChangeAction != nil {
		changeRequest.Variables = snow.ChangeVariables{
			Action: *job.ChangeAction,
		}
	}

	response, err := client.CreateNormalChangeTicket(changeRequest)
	if err != nil {
		return "", "", err
	}
	return response.SysID, response.Number, nil
}

// performStandardChange creates a standard change ticket in ServiceNow using the provided client, job details, and callback URL.
// It returns the ticket's SysID, Number, and any error encountered during the operation.
func performStandardChange(client *snow.Client, callbackBaseUrl string, job *db.Job, defaultServiceNowUserSysId string) (string, string, error) {
	// Standard Change strictly requires a template
	template := ""
	if job.ChangeTemplate != nil {
		template = *job.ChangeTemplate
	}

	var requestedBy string
	var assignedTo string
	var assignmentGroup string

	if job.User != nil && job.User.SysID != "" {
		// User is set in the database
		requestedBy = job.User.SysID
		assignedTo = job.User.SysID
	} else {
		// User is null in the database -> use default sys_id from toml file
		requestedBy = defaultServiceNowUserSysId

		// Check if the Appservice has a ChangeGroup set
		if job.Appservice != nil && job.Appservice.ChangeGroup != nil && job.Appservice.ChangeGroup.SysID != "" {
			assignmentGroup = job.Appservice.ChangeGroup.SysID
		}
		log.Printf("assignmentGroup: %s", assignmentGroup)
		log.Printf("requestedBy    : %s", requestedBy)
	}

	changeRequest := snow.StandardChangeRequest{
		CallbackUrl: fmt.Sprintf("%s%d", callbackBaseUrl, job.ID),
		Change: snow.NewStandardChangeObject{
			CIs:                    getCISysIDs(job),
			ApplicationService:     getApplicationAserviceSysID(job),
			ShortDescription:       "[MCMP] " + *replaceVariables(job.Title, ""),
			Description:            *replaceVariables(job.ActionDescription, ""),
			StartDate:              formatChangeDate(job.ChangeStartDate),
			EndDate:                formatChangeDate(job.ChangeEndDate),
			RequestedBy:            requestedBy,
			AssignedTo:             assignedTo,
			AssignmentGroup:        assignmentGroup,
			StandardChangeTemplate: template,
		},
	}

	response, err := client.CreateStandardChangeTicket(changeRequest)
	if err != nil {
		return "", "", err
	}
	return response.SysID, response.Number, nil
}

// handleSnowChangeError manages errors that occur while creating ServiceNow change tickets for a given job.
func handleSnowChangeError(mcmpClient *db.Client, job *db.Job, err error) {
	logger.Warn("Failed to create ServiceNow change ticket for job ID", "id", job.ID, "error", err)

	errorMsg := err.Error()
	job.ChangeStatus = db.ChangeStatusFailed
	job.Status = db.JobStatusFailed
	job.ChangeError = &errorMsg
	job.Title = replaceVariables(job.ActionErrorTitle, errorMsg)
	job.Description = replaceVariables(job.ActionErrorDescription, errorMsg)

	if updateErr := mcmpClient.UpdateJob(job); updateErr != nil {
		logger.Error("Failed to update job after snow error", "id", job.ID, "error", updateErr)
	}
}

// handleSnowChangeSuccess handles successful completion (fetch URL, Update DB)
func handleSnowChangeSuccess(mcmpClient *db.Client, snowClient *snow.Client, job *db.Job, sysID, number string) {
	changeUrl, err := snowClient.GetChangeURL(sysID)
	if err != nil {
		// Wenn URL abrufen fehlschlägt, ist es trotzdem ein Fehler im Prozess
		handleSnowChangeError(mcmpClient, job, fmt.Errorf("ticket created (%s) but failed to get URL: %w", number, err))
		return
	}

	logger.Info("  -> ServiceNow Change Ticket created successfully.", "number", number)

	job.ChangeStatus = db.ChangeStatusWaitingForApproval
	job.ChangeNumber = &number
	job.ChangeSysId = &sysID
	job.ChangeLink = &changeUrl
	job.Status = db.JobStatusWaitingForApproval

	if err := mcmpClient.UpdateJob(job); err != nil {
		logger.Error("Failed to update job after success", "id", job.ID, "error", err)
	}
}

// ensureTrailingSlash checks if a string ends with "/" and adds it if necessary
//
// Parameters:
//   - s: The string to check
//
// Returns:
//   - string: The string with guaranteed trailing "/"
func ensureTrailingSlash(s string) string {
	if !strings.HasSuffix(s, "/") {
		return s + "/"
	}
	return s
}

// getCISysIDs extracts the CI sys_ids from a job
// Returns a []string with the sys_ids if they are not empty
func getCISysIDs(job *db.Job) []string {
	sysIDs := make([]string, 0)

	// Check SnowInstanceSysID and add if not empty
	if job.Server != nil && job.Server.SnowInstanceSysID != nil && *job.Server.SnowInstanceSysID != "" {
		sysIDs = append(sysIDs, *job.Server.SnowInstanceSysID)
	}

	// Check SnowServerSysID and add if not empty
	if job.Server != nil && job.Server.SnowServerSysID != nil && *job.Server.SnowServerSysID != "" {
		sysIDs = append(sysIDs, *job.Server.SnowServerSysID)
	}

	return sysIDs
}

// getApplicationAserviceSysID retrieves the SysID of the Appservice from the provided job object if available.
func getApplicationAserviceSysID(job *db.Job) string {
	if job.Appservice != nil && job.Appservice.SysID != "" {
		return job.Appservice.SysID
	}
	return ""
}

// formatChangeDate formats a given time.Time pointer as an ISO8601 string with milliseconds and Z-UTC suffix.
func formatChangeDate(changeDate *time.Time) string {
	var t time.Time

	if changeDate == nil {
		t = time.Now().In(berlinLocation)
	} else {
		t = *changeDate
	}

	// Format as ISO8601 with milliseconds and Z-suffix (UTC)
	return t.Format("2006-01-02T15:04:05.000Z")
}

// replaceVariables replaces the placeholder ${error} in the given message string with the provided errorMessage value.
func replaceVariables(message *string, errorMessage string) *string {
	// Fallback, wenn keine Vorlage gesetzt ist
	tmpl := ""
	if message != nil && *message != "" {
		tmpl = *message
	}
	result := tmpl

	// Replace ${error} with errorMessage
	result = strings.ReplaceAll(result, "${error}", errorMessage)

	return &result
}

// parseStringToIntSlice extracts integers from a string and returns them as a slice of ints or an error if parsing fails.
func parseStringToIntSlice(input string) ([]int, error) {
	// Regex pattern for numbers (including negative ones)
	re := regexp.MustCompile(`-?\d+`)

	// Find all numbers in string
	matches := re.FindAllString(input, -1)

	var integers []int
	for _, match := range matches {
		num, err := strconv.Atoi(match)
		if err != nil {
			return nil, fmt.Errorf("cannot convert '%s' to integer: %v", match, err)
		}
		integers = append(integers, num)
	}

	return integers, nil
}

// mergeExtraVars merges a JSON string of variables with an additional map, overwriting conflicting keys with new values.
func mergeExtraVars(existingVars string, additionalVars map[string]interface{}) (string, error) {
	// Base-Map for merging
	mergedVars := make(map[string]interface{})

	// Parse existing variables if present
	if existingVars != "" {
		if err := json.Unmarshal([]byte(existingVars), &mergedVars); err != nil {
			return "", fmt.Errorf("error parsing existing extra_vars: %v", err)
		}
	}

	// Add additional variables to the base map
	// Additional variables overwrite existing ones in case of conflicts
	for key, value := range additionalVars {
		mergedVars[key] = value
	}

	// Convert merged map back to JSON
	result, err := json.Marshal(mergedVars)
	if err != nil {
		return "", fmt.Errorf("error converting to JSON: %v", err)
	}

	return string(result), nil
}

// prepareAndSortMap resolves nested JSON in "extra_vars" and returns a sorted JSON string of the map.
func prepareAndSortMap(data map[string]interface{}) *string {
	// Resolve nested JSON for better readability if "extra_vars" is a string
	if extraVarsStr, ok := data["extra_vars"].(string); ok {
		var extraVarsObj interface{}
		if err := json.Unmarshal([]byte(extraVarsStr), &extraVarsObj); err == nil {
			data["extra_vars"] = extraVarsObj
		}
	}
	return sortExtraVarsFromMap(data)
}

// sortExtraVarsFromMap takes a map and returns a sorted, indented JSON string.
func sortExtraVarsFromMap(data map[string]interface{}) *string {
	sortedJSON, err := marshalSortedJSON(data)
	if err != nil {
		logger.DebugPrintf("failed to marshal sorted data: %v", err)
		return new(fmt.Sprintf("failed to marshal data to JSON: %v", err))
	}

	return new(string(sortedJSON))
}

// sortExtraVars takes a JSON string, unmarshals it, and returns a sorted, indented JSON string.
// If unmarshaling or marshaling fails, it returns a pointer to the original string.
func sortExtraVars(extraVars string) *string {
	if extraVars == "" {
		return &extraVars
	}

	var extraVarsMap map[string]interface{}
	if err := json.Unmarshal([]byte(extraVars), &extraVarsMap); err != nil {
		logger.DebugPrintf("failed to unmarshal extra_vars for sorting: %v", err)
		return &extraVars
	}

	return sortExtraVarsFromMap(extraVarsMap)
}

// marshalSortedJSON converts a map into sorted, formatted JSON
// The keys are sorted alphabetically for consistent output
func marshalSortedJSON(data map[string]interface{}) ([]byte, error) {
	buffer := &strings.Builder{}
	encoder := json.NewEncoder(buffer)
	encoder.SetIndent("", "  ")
	encoder.SetEscapeHTML(false)

	// To sort keys, we must convert the map into a structure with sorted keys
	// Since json.Encoder does not sort map keys, we create the JSON manually
	return marshalSortedJSONRecursive(data, 0)
}

// marshalSortedJSONRecursive recursively creates sorted JSON with indentation
func marshalSortedJSONRecursive(v interface{}, indent int) ([]byte, error) {
	indentStr := strings.Repeat("  ", indent)
	nextIndentStr := strings.Repeat("  ", indent+1)

	switch val := v.(type) {
	case map[string]interface{}:
		if len(val) == 0 {
			return []byte("{}"), nil
		}

		// Sort keys
		keys := make([]string, 0, len(val))
		for k := range val {
			keys = append(keys, k)
		}
		// Alphabetical sort of keys
		for i := 0; i < len(keys); i++ {
			for j := i + 1; j < len(keys); j++ {
				if keys[i] > keys[j] {
					keys[i], keys[j] = keys[j], keys[i]
				}
			}
		}

		var builder strings.Builder
		builder.WriteString("{\n")
		for i, k := range keys {
			builder.WriteString(nextIndentStr)
			builder.WriteString(`"`)
			builder.WriteString(k)
			builder.WriteString(`": `)

			childJSON, err := marshalSortedJSONRecursive(val[k], indent+1)
			if err != nil {
				return nil, err
			}
			builder.Write(childJSON)

			if i < len(keys)-1 {
				builder.WriteString(",")
			}
			builder.WriteString("\n")
		}
		builder.WriteString(indentStr)
		builder.WriteString("}")
		return []byte(builder.String()), nil

	case []interface{}:
		if len(val) == 0 {
			return []byte("[]"), nil
		}

		var builder strings.Builder
		builder.WriteString("[\n")
		for i, item := range val {
			builder.WriteString(nextIndentStr)
			childJSON, err := marshalSortedJSONRecursive(item, indent+1)
			if err != nil {
				return nil, err
			}
			builder.Write(childJSON)

			if i < len(val)-1 {
				builder.WriteString(",")
			}
			builder.WriteString("\n")
		}
		builder.WriteString(indentStr)
		builder.WriteString("]")
		return []byte(builder.String()), nil

	default:
		// Use json.Marshal for primitive types
		return json.Marshal(val)
	}
}

// createForemanClient initializes and returns a configured Foreman API client or an error if the initialization fails.
// It securely configures the client with authentication, TLS verification, retry logic, and performance settings.
func createForemanClient(cfg *Config) (*foreman.Client, error) {
	// Initialize Foreman API client with comprehensive security and performance configuration
	// The client configuration includes authentication, TLS security, retry logic, and performance tuning
	foremanConfig := foreman.ClientConfig{
		Debug:           cfg.GENERAL.Debug,        // Enable debug logging for API communication
		Username:        cfg.FOREMAN.Username,     // Basic authentication username
		Password:        cfg.FOREMAN.Password,     // Basic authentication password
		ApiEndpoint:     cfg.FOREMAN.ApiEndpoint,  // Base URL for Foreman API endpoints
		EnableTLSVerify: true,                     // Enforce TLS certificate validation for security
		UserAgent:       "MCMP-EAI-Scheduler/1.0", // Client identification for server-side logging
	}

	// Create Foreman client instance with error handling for connection validation
	// The client encapsulates all HTTP communication logic and authentication handling
	foremanClient, err := foreman.NewClient(foremanConfig)
	if err != nil {
		return nil, err
	}
	return foremanClient, nil
}

// logVmStatusChange logs a virtual machine status change event using siemLogger and job details.
func logVmStatusChange(siemLogger *siem.SiemLogger, job *db.Job, defaultServiceNowUserSysId string) {
	if job.AwxError != nil {
		return
	}

	actionIdentifier := job.ActionIdentifier
	username := "MCMP_default_user_" + defaultServiceNowUserSysId
	if job.User != nil {
		username = job.User.Username
	}

	hostname := ""
	if job.Server != nil {
		if job.Server.FQDN != nil {
			hostname = *job.Server.FQDN
		} else {
			hostname = job.Server.Name
		}
	}
	if job.Hostname != nil {
		hostname = *job.Hostname
	}

	appService := ""
	appServiceNumber := ""
	if job.Appservice != nil {
		if job.Appservice.Name != nil {
			appService = *job.Appservice.Name
		}
		appServiceNumber = job.Appservice.Number
	}

	changeNumber := ""
	if job.ChangeNumber != nil {
		changeNumber = *job.ChangeNumber
	}

	jobNumber := strconv.FormatInt(job.ID, 10)

	msg := ""
	if job.Description != nil {
		msg = *job.Description
	}

	siemLogger.LogVmStatusChange(actionIdentifier, username, hostname, appService, appServiceNumber, changeNumber, jobNumber, msg)
}

func splitAndTrim(s string, sep string) []string {
	if s == "" {
		return []string{}
	}

	parts := strings.Split(s, sep)
	result := make([]string, 0, len(parts))

	for _, p := range parts {
		t := strings.TrimSpace(p)
		if t != "" {
			result = append(result, t)
		}
	}

	return result
}

func sendNonPostgresEmail(cfg *Config, mcmpClient *db.Client, job *db.Job) {
	if job.ServerInstallation && job.NonPostgres && job.NonPostgresEmailStatus == db.EmailStatusNew {
		logger.Info("  -> Initiating Non-Postgres Email notification for Job", "id", job.ID)

		to := splitAndTrim(cfg.SMTP.ToNonPostgres, ",")

		if job.NonOSS {
			toNonOss := splitAndTrim(cfg.SMTP.ToNonOSS, ",")
			to = append(to, toNonOss...)
		}
		cc := splitAndTrim(cfg.SMTP.CC, ",")

		justification := ""
		if job.NonPostgresJustification != nil {
			justification = *job.NonPostgresJustification
		}

		err := mail.SendEmail(cfg.SMTP.Server, cfg.SMTP.Port, cfg.SMTP.Username, cfg.SMTP.Password, to, cc, cfg.SMTP.Subject, justification, true)

		if err != nil {
			logger.Warn("Error sending email", "jobId", job.ID, "error", err)
			job.NonPostgresEmailStatus = db.EmailStatusFailed
		} else {
			logger.Info("  -> Non-Postgres Email sent successfully for Job", "id", job.ID)
			job.NonPostgresEmailStatus = db.EmailStatusSent
		}

		if err := mcmpClient.UpdateJob(job); err != nil {
			logger.Error("Failed to update job after email attempt", "id", job.ID, "error", err)
		}
	} else if job.NonPostgresEmailStatus == db.EmailStatusNew {
		logger.Info("  -> Non-Postgres Email not required for Job. Skipping.", "id", job.ID)
		job.NonPostgresEmailStatus = db.EmailStatusSkipped
		if err := mcmpClient.UpdateJob(job); err != nil {
			logger.Error("Failed to update job to skipped email status", "id", job.ID, "error", err)
		}
	}
}

// updateAwxJobDates updates the start and end dates of a job based on AWX results
func updateAwxJobDates(job *db.Job, started time.Time, finished time.Time, templateType string) {
	if !started.IsZero() {
		logger.DebugPrintf("     AwxTemplateType%s jobResult.Started : %s\n", templateType, started.Format(time.RFC3339Nano))
		job.AwxStartDate = new(started.In(berlinLocation))
	}

	if !finished.IsZero() {
		logger.DebugPrintf("     AwxTemplateType%s jobResult.Finished : %s\n", templateType, finished.Format(time.RFC3339Nano))
		job.AwxEndDate = new(finished.In(berlinLocation))
	} else {
		job.AwxEndDate = new(time.Now().In(berlinLocation))
	}
}

// retry is a helper function to retry a function call with exponential backoff.
func retry(ctx context.Context, maxRetries int, initialBackoff time.Duration, maxBackoff time.Duration, f func() error) error {
	var err error
	backoff := initialBackoff

	for i := 0; i < maxRetries; i++ {
		if i > 0 {
			fmt.Printf("Retrying after error: %v. Attempt %d/%d\n", err, i+1, maxRetries)
			select {
			case <-time.After(backoff):
			case <-ctx.Done():
				return ctx.Err()
			}
			backoff *= 2
			if backoff > maxBackoff {
				backoff = maxBackoff
			}
		}

		err = f()
		if err == nil {
			return nil // Success
		}
	}
	return fmt.Errorf("failed after %d attempts: %w", maxRetries, err)
}

// collectAllWorkflowNodes recursively collects all job details from a workflow job and its nested workflow jobs concurrently.
func collectAllWorkflowNodes(ctx context.Context, awxClient *awx.AWX, apiEndpoint string, workflowJobID int64, parentWorkflowJobID int64, depth int) ([]db.JobNode, error) {
	var nodes []*awx.WorkflowJobNode
	err := retry(ctx, 3, 1*time.Second, 10*time.Second, func() error {
		var listErr error
		nodes, _, listErr = awxClient.WorkflowJobNodeService.ListWorkflowJobNodes(map[string]string{
			"workflow_job": strconv.FormatInt(workflowJobID, 10),
		})
		return listErr
	})
	if err != nil {
		return nil, fmt.Errorf("failed to get workflow job nodes for workflow ID %d: %w", workflowJobID, err)
	}

	results := make([][]db.JobNode, len(nodes))
	var wg sync.WaitGroup
	var errOnce sync.Once
	var firstErr error

	for i := range nodes {
		i := i
		node := nodes[i]

		wg.Add(1)
		go func() {
			defer wg.Done()

			// Check context cancellation
			if ctx.Err() != nil {
				errOnce.Do(func() { firstErr = ctx.Err() })
				return
			}

			jobInfos, err := processNode(ctx, awxClient, apiEndpoint, node, parentWorkflowJobID, depth)
			if err != nil {
				logger.Warn("processNode Error", "error", err)
				errOnce.Do(func() { firstErr = err })
				return
			}
			results[i] = jobInfos
		}()
	}

	wg.Wait()

	if firstErr != nil {
		return nil, firstErr
	}

	var collectedJobs []db.JobNode
	for _, res := range results {
		collectedJobs = append(collectedJobs, res...)
	}

	return sortJobNodesHierarchically(collectedJobs, parentWorkflowJobID), nil
}

func sortJobNodesHierarchically(nodes []db.JobNode, rootParentID int64) []db.JobNode {
	byParent := make(map[int64][]db.JobNode)
	for _, node := range nodes {
		byParent[node.ParentJobID] = append(byParent[node.ParentJobID], node)
	}

	for parentID := range byParent {
		sort.Slice(byParent[parentID], func(i, j int) bool {
			nodeI := byParent[parentID][i]
			nodeJ := byParent[parentID][j]

			// If both IDs are nil (not executed), sort by NodeID ascending
			if nodeI.JobAwxID == nil && nodeJ.JobAwxID == nil {
				return nodeI.NodeID < nodeJ.NodeID
			}
			// If only nodeI is nil, it belongs at the end
			if nodeI.JobAwxID == nil {
				return false
			}
			// If only nodeJ is nil, nodeI comes first
			if nodeJ.JobAwxID == nil {
				return true
			}

			// If both IDs are present, sort by JobAwxID
			return *nodeI.JobAwxID < *nodeJ.JobAwxID
		})
	}

	var result []db.JobNode
	var walk func(int64)
	walk = func(pID int64) {
		for _, node := range byParent[pID] {
			result = append(result, node)
			// If this node was a workflow, it might be a parent for other nodes in the list
			if node.JobAwxID != nil {
				walk(*node.JobAwxID)
			}
		}
	}

	walk(rootParentID)

	return result
}

// processNode handles the extraction and formatting of a single workflow node.
func processNode(ctx context.Context, awxClient *awx.AWX, apiEndpoint string, node *awx.WorkflowJobNode, parentWorkflowJobID int64, depth int) ([]db.JobNode, error) {
	jobInfo := db.JobNode{
		NodeID:         int64(node.ID),
		NodeIdentifier: &node.Identifier,
		ParentJobID:    parentWorkflowJobID,
		ParentJobLink:  new(fmt.Sprintf("%s/#/jobs/workflow/%d", apiEndpoint, parentWorkflowJobID)),
		JobStatus:      new("not executed"),
		JobAwxID:       nil,
		JobStarted:     nil,
		JobFinished:    nil,
		JobDepth:       &depth,
		SuccessNodes:   node.SuccessNodes,
		FailureNodes:   node.FailureNodes,
		AlwaysNodes:    node.AlwaysNodes,
	}

	if node.SummaryFields != nil && node.SummaryFields.UnifiedJobTemplate != nil {
		id := node.SummaryFields.UnifiedJobTemplate.ID
		jobInfo.TemplateID = new(int64(id))
		jobInfo.TemplateName = new(node.SummaryFields.UnifiedJobTemplate.Name)
		jobInfo.TemplateType = new(node.SummaryFields.UnifiedJobTemplate.UnifiedJobType)
		templateType := ""
		if jobInfo.TemplateType != nil {
			templateType = *jobInfo.TemplateType
		}

		if templateType == "job" {
			link, err := getAwxTemplateURL(apiEndpoint, id)
			if err != nil {
				logger.Warn("failed to get AWX template URL", "id", id, "error", err)
			} else {
				jobInfo.TemplateLink = link
			}
		} else if templateType == "workflow_job" {
			link, err := getAwxWorkflowTemplateURL(apiEndpoint, id)
			if err != nil {
				logger.Warn("failed to get AWX template URL", "id", id, "error", err)
			} else {
				jobInfo.TemplateLink = link
			}
		}
	}

	nodeIdentifierOutput := ""
	if jobInfo.TemplateName != nil {
		nodeIdentifierOutput = *jobInfo.TemplateName
	}
	if isUUID(*jobInfo.NodeIdentifier) {
		if jobInfo.TemplateName == nil {
			nodeIdentifierOutput = *jobInfo.NodeIdentifier
		}
	} else {
		nodeIdentifierOutput = *jobInfo.NodeIdentifier
	}
	jobInfo.NodeAlias = &nodeIdentifierOutput

	/*
		// Set NodeAlias logic after TemplateName is populated
		nodeIdentifierOutput := ""
		if jobInfo.TemplateName != nil && *jobInfo.TemplateName != "" {
			nodeIdentifierOutput = *jobInfo.TemplateName
		} else if jobInfo.NodeIdentifier != nil {
			nodeIdentifierOutput = *jobInfo.NodeIdentifier
		}
		jobInfo.NodeAlias = &nodeIdentifierOutput
	*/

	templateType := ""
	if jobInfo.TemplateType != nil {
		templateType = *jobInfo.TemplateType
	}

	var collectedJobs []db.JobNode

	if isStandardJob(templateType) {
		if node.SummaryFields != nil && node.SummaryFields.Job != nil {
			jobID := int64(node.SummaryFields.Job.ID)

			jobDetails, link, err := fetchJobDetailsAndLink(ctx, awxClient, apiEndpoint, templateType, int(jobID))
			if err != nil {
				logger.DebugPrintf("Warning: failed to get details for %s ID %d (node ID %d): %v\n", templateType, jobID, node.ID, err)
				jobInfo.JobAwxID = &jobID
				jobInfo.JobStatus = new("UNKNOWN (details error)")
			} else {
				populateJobDetails(ctx, &jobInfo, jobDetails, link, awxClient, templateType)
			}
		}
		collectedJobs = append(collectedJobs, jobInfo)

	} else if templateType == "workflow_job" {
		if node.SummaryFields != nil && node.SummaryFields.Job != nil {
			jobID := node.SummaryFields.Job.ID
			var wfJobDetails *awx.WorkflowJob
			err := retry(ctx, 3, 1*time.Second, 10*time.Second, func() error {
				var getErr error
				wfJobDetails, getErr = awxClient.WorkflowJobService.GetWorkflowJob(jobID, nil)
				return getErr
			})

			if err != nil {
				logger.DebugPrintf("Warning: failed to get workflow job details for WF ID %d (node ID %d): %v\n", jobID, node.ID, err)
				jobInfo.JobAwxID = new(int64(jobID))
				if jobInfo.TemplateName != nil {
					jobInfo.JobName = jobInfo.TemplateName // Use template name as fallback
				}
				jobInfo.JobStatus = new("unknown")
				jobInfo.JobAwxLink = new(fmt.Sprintf("%s/#/jobs/workflow/%d", apiEndpoint, jobID))
			} else {
				jobInfo.JobAwxID = new(int64(wfJobDetails.ID))
				jobInfo.JobName = &wfJobDetails.Name
				jobInfo.JobType = &wfJobDetails.Type
				//jobInfo.JobType = new("workflow_job")
				jobInfo.JobStatus = &wfJobDetails.Status
				jobInfo.JobStarted = &wfJobDetails.Started
				jobInfo.JobFinished = &wfJobDetails.Finished
				jobInfo.JobFailed = &wfJobDetails.Failed

				if wfJobDetails.SummaryFields != nil && wfJobDetails.SummaryFields.Organization != nil {
					jobInfo.JobOrg = new(wfJobDetails.SummaryFields.Organization.Name)
				}
				jobInfo.JobErrorMessage = new(stripANSI(wfJobDetails.JobExplanation))
				jobInfo.JobAwxLink = new(fmt.Sprintf("%s/#/jobs/workflow/%d", apiEndpoint, wfJobDetails.ID))
			}
		} else {
			jobInfo.JobStatus = new("not executed")
			if jobInfo.TemplateName != nil {
				jobInfo.JobName = jobInfo.TemplateName
			}
		}
		collectedJobs = append(collectedJobs, jobInfo)

		// Recurse for children
		if node.SummaryFields != nil && node.SummaryFields.Job != nil {
			nestedWorkflowJobID := int64(node.SummaryFields.Job.ID)
			nestedJobs, err := collectAllWorkflowNodes(ctx, awxClient, apiEndpoint, nestedWorkflowJobID, *jobInfo.JobAwxID, depth+1)
			if err != nil {
				logger.DebugPrintf("Warning: error collecting nested workflow jobs for workflow ID %d (node ID %d): %v\n", nestedWorkflowJobID, node.ID, err)
			}
			collectedJobs = append(collectedJobs, nestedJobs...)
		}
	} else {
		collectedJobs = append(collectedJobs, jobInfo)
	}

	return collectedJobs, nil
}

// isStandardJob checks if the template type corresponds to a typical executeable AWX job.
func isStandardJob(templateType string) bool {
	return templateType == "job" ||
		templateType == "project_update" ||
		templateType == "inventory_update" ||
		templateType == "system_job" ||
		templateType == "workflow_approval"
}

// fetchJobDetailsAndLink retrieves the generic job details and constructs the corresponding AWX link.
func fetchJobDetailsAndLink(ctx context.Context, awxClient *awx.AWX, apiEndpoint string, templateType string, jobID int) (*awx.Job, *string, error) {
	var jobDetails *awx.Job
	var link string
	var err error

	err = retry(ctx, 3, 1*time.Second, 10*time.Second, func() error {
		var getErr error
		switch templateType {
		case "job":
			jobDetails, getErr = awxClient.JobService.GetJob(jobID, nil)
			link = fmt.Sprintf("%s/#/jobs/playbook/%d", apiEndpoint, jobID)
		case "project_update":
			jobDetails, getErr = awxClient.ProjectUpdatesService.ProjectUpdateGet(jobID)
			link = fmt.Sprintf("%s/#/jobs/project/%d", apiEndpoint, jobID)
		case "inventory_update":
			jobDetails, getErr = awxClient.InventoryUpdatesService.GetInventoryUpdate(jobID)
			link = fmt.Sprintf("%s/#/jobs/inventory/%d", apiEndpoint, jobID)
		case "system_job":
			jobDetails, getErr = awxClient.SystemJobsService.GetSystemJob(jobID)
			link = fmt.Sprintf("%s/#/jobs/management/%d", apiEndpoint, jobID)
		case "workflow_approval":
			jobDetails, getErr = awxClient.WorkflowApprovalsService.GetWorkflowApproval(jobID)
			link = fmt.Sprintf("%s/#/workflow_approvals/%d", apiEndpoint, jobID)
		default:
			return fmt.Errorf("unsupported template type: %s", templateType)
		}
		return getErr
	})
	if err != nil {
		// Check if the error is due to the unsupported type, which is not a network error.
		if strings.Contains(err.Error(), "unsupported template type") {
			return nil, nil, err
		}
		return nil, nil, fmt.Errorf("failed to fetch details for %s ID %d: %w", templateType, jobID, err)
	}
	return jobDetails, &link, nil
}

// populateJobDetails maps the API response to our DisplayJobInfo struct.
func populateJobDetails(ctx context.Context, jobInfo *db.JobNode, jobDetails *awx.Job, link *string, awxClient *awx.AWX, templateType string) {
	jobInfo.JobAwxID = new(int64(jobDetails.ID))
	jobInfo.JobName = &jobDetails.Name
	jobInfo.JobType = &jobDetails.Type
	jobInfo.JobStatus = &jobDetails.Status
	jobInfo.JobStarted = &jobDetails.Started
	jobInfo.JobFinished = &jobDetails.Finished
	jobInfo.JobAwxLink = link
	jobInfo.JobExtraVars = sortExtraVars(jobDetails.ExtraVars)

	if jobDetails.SummaryFields != nil && jobDetails.SummaryFields.Organization != nil {
		jobInfo.JobOrg = new(jobDetails.SummaryFields.Organization.Name)
	}
	jobInfo.JobErrorMessage = new(stripANSI(jobDetails.JobExplanation))

	// If the job failed, fetch detailed error events and append to explanation
	if (jobDetails.Status == "failed" || jobDetails.Status == "error") && templateType == "job" {
		detailedExplanation := getFailedJobEventsExplanation(ctx, awxClient, jobDetails.ID)
		if detailedExplanation != "" {
			if jobInfo.JobErrorMessage != nil && *jobInfo.JobErrorMessage != "" {
				jobInfo.JobErrorMessage = new(*jobInfo.JobErrorMessage + " | " + detailedExplanation)
			} else {
				jobInfo.JobErrorMessage = &detailedExplanation
			}
		}
	}

	jobInfo.JobFailed = new(jobDetails.Failed)

	completed, message, data := checkJobArtifacts(jobDetails.Artifacts)
	jobInfo.JobReturnCompleted = completed
	jobInfo.JobReturnMessage = message

	if data != nil {
		jobInfo.JobReturnData = sortExtraVars(*data)
	} else {
		jobInfo.JobReturnData = nil
	}

	if jobDetails.Artifacts != nil {
		jobInfo.JobArtifacts = sortExtraVarsFromMap(jobDetails.Artifacts)
	}
}

// getFailedJobEventsExplanation fetches and formats error events for a given job.
func getFailedJobEventsExplanation(ctx context.Context, awxClient *awx.AWX, jobID int) string {
	params := map[string]string{
		"event__in": "error,runner_on_async_failed,runner_on_failed,runner_on_error,runner_item_on_failed",
		"order_by":  "created", // Order by creation time to get chronological events
	}

	var events []awx.JobEvent
	err := retry(ctx, 3, 1*time.Second, 10*time.Second, func() error {
		var getErr error
		events, _, getErr = awxClient.JobService.GetJobEvents(jobID, params)
		return getErr
	})
	if err != nil {
		return fmt.Sprintf("Error fetching job events: %v", err)
	}

	if len(events) == 0 {
		return "No specific error events found."
	}

	var sb strings.Builder
	first := true

	for _, event := range events {
		if !first {
			sb.WriteString("\n")
		}
		first = false

		// Prioritize stdout for detailed error messages
		if event.Stdout != "" {
			cleanStdout := stripANSI(event.Stdout)
			cleanStdout = strings.ReplaceAll(cleanStdout, "\n", " ")
			sb.WriteString(fmt.Sprintf("[%s] Host: %s, Msg: %s", event.Event, event.HostName, cleanStdout))
		} else if event.EventDisplay != "" {
			cleanDisplay := stripANSI(event.EventDisplay)
			cleanDisplay = strings.ReplaceAll(cleanDisplay, "\n", " ")
			sb.WriteString(fmt.Sprintf("[%s] %s", event.Event, cleanDisplay))
		} else {
			sb.WriteString(fmt.Sprintf("[%s] (no details)", event.Event))
		}
	}
	return sb.String()
}

// isUUID checks if a string is a valid UUID.
func isUUID(s string) bool {
	_, err := uuid.Parse(s)
	return err == nil
}

// stripANSI removes ANSI escape codes (like colors) from a given string.
func stripANSI(str string) string {
	return ansiRegex.ReplaceAllString(str, "")
}

func fetchAndPopulateAwxJobData(ctx context.Context, awxClient *awx.AWX, job *db.Job) error {
	if job.AwxJobId == nil {
		return fmt.Errorf("job %d has no AwxJobId", job.ID)
	}

	var name, status string
	var failed bool
	var started, finished time.Time
	var artifacts interface{}
	var orgName, templateName *string
	var templateID int
	var jobExplanation string

	if job.AwxTemplateType == db.AwxTemplateTypeTemplate {
		res, err := awxClient.JobService.GetJob(int(*job.AwxJobId), nil)
		if err != nil {
			return err
		}
		name, status, failed, started, finished, artifacts = res.Name, res.Status, res.Failed, res.Started, res.Finished, res.Artifacts
		jobExplanation = res.JobExplanation

		if res.SummaryFields != nil {
			if res.SummaryFields.Organization != nil {
				orgName = &res.SummaryFields.Organization.Name
			}
			if res.SummaryFields.UnifiedJobTemplate != nil {
				templateName = &res.SummaryFields.UnifiedJobTemplate.Name
				templateID = res.SummaryFields.UnifiedJobTemplate.ID
			}
		}
		if artifacts != nil {
			job.AwxJobArtifacts = sortExtraVarsFromMap(res.Artifacts)
		}

		if status == "failed" || status == "error" {
			explanation := getFailedJobEventsExplanation(ctx, awxClient, res.ID)
			if explanation != "" {
				job.AwxJobErrorMessage = &explanation
			}
		}
	} else {
		res, err := awxClient.WorkflowJobService.GetWorkflowJob(int(*job.AwxJobId), nil)
		if err != nil {
			return err
		}
		name, status, failed, started, finished = res.Name, res.Status, res.Failed, res.Started, res.Finished
		jobExplanation = res.JobExplanation
		if res.SummaryFields != nil {
			if res.SummaryFields.Organization != nil {
				orgName = &res.SummaryFields.Organization.Name
			}
			if res.SummaryFields.UnifiedJobTemplate != nil {
				templateName = &res.SummaryFields.UnifiedJobTemplate.Name
				templateID = res.SummaryFields.UnifiedJobTemplate.ID
			}
		}
		if jobExplanation != "" {
			job.AwxJobErrorMessage = new(stripANSI(jobExplanation))
		}
	}

	job.AwxJobName = &name
	job.AwxJobStatus = &status
	job.AwxJobFailed = &failed
	job.AwxJobOrg = orgName
	job.AwxTemplateName = templateName
	if templateName != nil {
		link, err := getAwxTemplateURL(job.Awx.ApiEndpoint, templateID)
		if err == nil {
			job.AwxTemplateLink = link
		}
	}

	completed, msg, data := checkJobArtifacts(artifacts)
	job.AwxJobReturnCompleted = completed
	job.AwxJobReturnMessage = msg
	job.AwxJobReturnData = data

	updateAwxJobDates(job, started, finished, string(job.AwxTemplateType))
	return nil
}

func syncWorkflowNodes(ctx context.Context, awxClient *awx.AWX, mcmpClient *db.Client, job *db.Job) error {
	if job.AwxTemplateType != db.AwxTemplateTypeWorkflow || job.AwxJobId == nil {
		return nil
	}
	nodes, err := collectAllWorkflowNodes(ctx, awxClient, job.Awx.ApiEndpoint, *job.AwxJobId, *job.AwxJobId, 1)
	if err != nil {
		return err
	}

	determineRootCause(nodes)

	for _, node := range nodes {
		node.JobID = job.ID
		if err := mcmpClient.SaveJobNode(&node); err != nil {
			logger.Warn("Failed to save job node", "nodeId", node.NodeID, "jobId", job.ID, "error", err)
		}
	}
	return nil
}

// determineRootCause identifies the root cause of a failed workflow by evaluating
// if a failed node's error was caught by a failure path or had successful descendants.
func determineRootCause(nodes []db.JobNode) {
	nodeMap := make(map[int64]*db.JobNode)
	for i := range nodes {
		nodes[i].JobIsRootCause = false // Initialize for all
		nodeMap[nodes[i].NodeID] = &nodes[i]
	}

	var potentialRootCauses []*db.JobNode

	for i := range nodes {
		node := &nodes[i]
		if node.JobStatus != nil && (*node.JobStatus == "failed" || *node.JobStatus == "error") {
			// Check if this failure was "caught" or followed by a successful node
			if !hasSuccessfulPath(node, nodeMap) {
				potentialRootCauses = append(potentialRootCauses, node)
			}
		}
	}

	// In case of multiple potential root causes (e.g., parallel paths),
	// select the one that finished earliest.
	if len(potentialRootCauses) > 0 {
		var rootCause *db.JobNode
		for _, cause := range potentialRootCauses {
			if rootCause == nil {
				rootCause = cause
			} else if cause.JobFinished != nil && rootCause.JobFinished != nil && cause.JobFinished.Before(*rootCause.JobFinished) {
				rootCause = cause
			}
		}
		if rootCause != nil {
			rootCause.JobIsRootCause = true
		}
	}
}

// hasSuccessfulPath recursively checks if there is any successful node
// in the downstream paths (success, failure, always).
func hasSuccessfulPath(node *db.JobNode, nodeMap map[int64]*db.JobNode) bool {
	var nextNodes []int
	nextNodes = append(nextNodes, node.SuccessNodes...)
	nextNodes = append(nextNodes, node.FailureNodes...)
	nextNodes = append(nextNodes, node.AlwaysNodes...)

	for _, nextID := range nextNodes {
		nextNode, exists := nodeMap[int64(nextID)]
		if !exists {
			continue
		}
		if nextNode.JobStatus != nil && *nextNode.JobStatus == "successful" {
			return true
		}
		if hasSuccessfulPath(nextNode, nodeMap) {
			return true
		}
	}
	return false
}
