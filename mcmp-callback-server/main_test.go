package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/it-at-m/mcmp/mcmp-callback-server/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-callback-server/pkg/config"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
)

// createTestLogger creates a logger for testing purposes that outputs to console
func createTestLogger() *logging.StructuredLogger {
	logConfig := logging.LogConfig{
		Level:      "DEBUG",
		Output:     "console",
		Format:     "plain",
		Filename:   "",
		MaxSize:    0,
		MaxBackups: 0,
		MaxAge:     0,
		Compress:   false,
	}

	logger, err := logging.NewStructuredLogger(logConfig)
	if err != nil {
		// Fallback to a basic logger for tests
		panic(fmt.Sprintf("Failed to create test logger: %v", err))
	}

	return logger
}

// mockMCMPClient is a mock implementation of the MCMP Client interface
type mockMCMPClient struct {
	findJobByIDFunc func(id int64) (*mcmp.Job, error)
	updateJobFunc   func(job *mcmp.Job) error
	closeFunc       func() error
}

func (m *mockMCMPClient) FindJobByID(id int64) (*mcmp.Job, error) {
	if m.findJobByIDFunc != nil {
		return m.findJobByIDFunc(id)
	}
	return nil, errors.New("FindJobByID not implemented in mock")
}

func (m *mockMCMPClient) UpdateJob(job *mcmp.Job) error {
	if m.updateJobFunc != nil {
		return m.updateJobFunc(job)
	}
	return errors.New("UpdateJob not implemented in mock")
}

func (m *mockMCMPClient) Close() error {
	if m.closeFunc != nil {
		return m.closeFunc()
	}
	return nil
}

// TestServer_handleChangeCallback tests the handleChangeCallback method
func TestServer_handleChangeCallback(t *testing.T) {
	testConfig := config.Config{
		GENERAL: config.General{
			Port:  8080,
			Debug: false,
		},
		LOGGING: logging.LogConfig{
			Level:      "DEBUG",
			Output:     "console",
			Filename:   "",
			MaxSize:    0,
			MaxBackups: 0,
			MaxAge:     0,
			Compress:   false,
		},
	}

	tests := []struct {
		name               string
		method             string
		path               string
		body               interface{}
		mockFindJob        func(id int64) (*mcmp.Job, error)
		mockUpdateJob      func(job *mcmp.Job) error
		expectedStatusCode int
		expectedStatus     string
		validateResponse   func(t *testing.T, body []byte)
		validateJob        func(t *testing.T, job *mcmp.Job)
	}{
		{
			name:   "SuccessfulApproval",
			method: http.MethodPost,
			path:   "/callback/change/123",
			body: ChangeCallbackRequest{
				Success:      true,
				ErrorMessage: "",
				Result: struct {
					Approval        string `json:"approval"`
					ApprovalSet     string `json:"approval_set"`
					ApprovalHistory []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					} `json:"approval_history"`
				}{
					Approval:    "approved",
					ApprovalSet: "2025-10-08T05:32:19.000+0000Z",
					ApprovalHistory: []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					}{
						{
							SysCreatedOn: "2025-10-08T05:32:19.000+0000Z",
							Value:        "Change Request has been approved by CMP Change Policy (Change Approval Policy Action).",
						},
						{
							SysCreatedOn: "2025-10-08T05:32:18.000+0000Z",
							Value:        "Group approval for Service: Hardware Server was approved by user Erika Mustermann.",
						},
						{
							SysCreatedOn: "2025-10-08T05:32:16.000+0000Z",
							Value:        "Erika Mustermann has approved the task.",
						},
						{
							SysCreatedOn: "2025-10-08T05:22:31.000+0000Z",
							Value:        "No Decisions matched. CMP Change Policy has been skipped (Change Approval Policy Action).",
						},
					},
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:             123,
					ChangeRequired: true,
					ChangeStatus:   mcmp.ChangeStatusWaitingForApproval,
					Status:         mcmp.JobStatusWaitingForApproval,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return nil
			},
			expectedStatusCode: http.StatusOK,
			expectedStatus:     "success",
			validateResponse: func(t *testing.T, body []byte) {
				var response map[string]interface{}
				if err := json.Unmarshal(body, &response); err != nil {
					t.Fatalf("Error parsing response: %v", err)
				}
				if response["status"] != "success" {
					t.Errorf("Expected status 'success', got '%v'", response["status"])
				}
				if response["job_id"] != float64(123) {
					t.Errorf("Expected job_id 123, got %v", response["job_id"])
				}
				if response["new_status"] != string(mcmp.ChangeStatusApproved) {
					t.Errorf("Expected new_status 'approved', got '%v'", response["new_status"])
				}
			},
			validateJob: func(t *testing.T, job *mcmp.Job) {
				if job.ChangeStatus != mcmp.ChangeStatusApproved {
					t.Errorf("Expected ChangeStatus 'approved', got '%s'", job.ChangeStatus)
				}
				if job.Status != mcmp.JobStatusApproved {
					t.Errorf("Expected status 'approved', got '%s'", job.Status)
				}
			},
		},
		{
			name:   "RejectionWithErrorMessage",
			method: http.MethodPost,
			path:   "/callback/change/456",
			body: ChangeCallbackRequest{
				Success:      false,
				ErrorMessage: "The Change Request was not approved.",
				Result: struct {
					Approval        string `json:"approval"`
					ApprovalSet     string `json:"approval_set"`
					ApprovalHistory []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					} `json:"approval_history"`
				}{
					Approval:    "rejected",
					ApprovalSet: "2025-10-08T13:37:14.000+0000Z",
					ApprovalHistory: []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					}{
						{
							SysCreatedOn: "2025-10-08T13:37:14.000+0000Z",
							Value:        "Change Request has been rejected by CMP Change Policy (Change Approval Policy Action).",
						},
						{
							SysCreatedOn: "2025-10-08T13:37:14.000+0000Z",
							Value:        "Group approval for Service: Hardware Server was rejected by user Erika Mustermann.",
						},
						{
							SysCreatedOn: "2025-10-08T13:37:12.000+0000Z",
							Value:        "Erika Mustermann has rejected the task.\n\nApproval Comments:\n08.10.2025 15:37:12 - Erika Mustermann (Comments)\nNot authorized!\n\n",
						},
						{
							SysCreatedOn: "2025-10-08T13:35:46.000+0000Z",
							Value:        "No Decisions matched. CMP Change Policy has been skipped (Change Approval Policy Action).",
						},
					},
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:             456,
					ChangeRequired: true,
					ChangeStatus:   mcmp.ChangeStatusWaitingForApproval,
					Status:         mcmp.JobStatusWaitingForApproval,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return nil
			},
			expectedStatusCode: http.StatusOK,
			expectedStatus:     "success",
			validateJob: func(t *testing.T, job *mcmp.Job) {
				if job.ChangeStatus != mcmp.ChangeStatusRejected {
					t.Errorf("Expected ChangeStatus 'rejected', got '%s'", job.ChangeStatus)
				}
				if job.Status != mcmp.JobStatusRejected {
					t.Errorf("Expected JobStatus 'rejected', got '%s'", job.Status)
				}
				if job.ChangeError == nil || *job.ChangeError != "The Change Request was not approved." {
					t.Errorf("Expected ChangeError 'The Change Request was not approved.', got '%v'", job.ChangeError)
				}
			},
		},
		{
			name:   "CancelWithErrorMessage",
			method: http.MethodPost,
			path:   "/callback/change/456",
			body: ChangeCallbackRequest{
				Success:      false,
				ErrorMessage: "The change request has been canceled.",
				Result: struct {
					Approval        string `json:"approval"`
					ApprovalSet     string `json:"approval_set"`
					ApprovalHistory []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					} `json:"approval_history"`
				}{
					Approval:    "approved",
					ApprovalSet: "2025-09-03T06:43:03.000+0000Z",
					ApprovalHistory: []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					}{
						{
							SysCreatedOn: "2025-09-03T06:43:03.000+0000Z",
							Value:        "Group approval for Service: Hardware Server was approved by user Erika Mustermann.",
						},
						{
							SysCreatedOn: "2025-09-03T06:43:03.000+0000Z",
							Value:        "Change Request has been approved by CMP Change Policy (Change Approval Policy Action).",
						},
						{
							SysCreatedOn: "2025-09-03T06:43:01.000+0000Z",
							Value:        "Erika Mustermann has approved the task.",
						},
						{
							SysCreatedOn: "2025-09-03T06:42:14.000+0000Z",
							Value:        "No Decisions matched. CMP Change Policy has been skipped (Change Approval Policy Action).",
						},
					},
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:             456,
					ChangeRequired: true,
					ChangeStatus:   mcmp.ChangeStatusWaitingForApproval,
					Status:         mcmp.JobStatusWaitingForApproval,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return nil
			},
			expectedStatusCode: http.StatusOK,
			expectedStatus:     "success",
			validateJob: func(t *testing.T, job *mcmp.Job) {
				if job.ChangeStatus != mcmp.ChangeStatusCanceled {
					t.Errorf("Expected ChangeStatus 'canceled', got '%s'", job.ChangeStatus)
				}
				if job.Status != mcmp.JobStatusCanceled {
					t.Errorf("Expected JobStatus 'canceled', got '%s'", job.Status)
				}
				if job.ChangeError == nil || *job.ChangeError != "The change request has been canceled." {
					t.Errorf("Expected ChangeError 'The change request has been canceled.', got '%v'", job.ChangeError)
				}
			},
		},
		{
			name:   "CancelWithErrorMessageAndApprovalNotRequested",
			method: http.MethodPost,
			path:   "/callback/change/815",
			body: ChangeCallbackRequest{
				Success:      false,
				ErrorMessage: "The change request has been canceled.",
				Result: struct {
					Approval        string `json:"approval"`
					ApprovalSet     string `json:"approval_set"`
					ApprovalHistory []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					} `json:"approval_history"`
				}{
					Approval:    "not requested",
					ApprovalSet: "",
					ApprovalHistory: []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					}{
						{
							SysCreatedOn: "2025-10-21T12:42:07.000+0000Z",
							Value:        "Jane Doe has requested approval of the task.",
						},
						{
							SysCreatedOn: "2025-10-21T12:42:07.000+0000Z",
							Value:        "John Doe has requested approval of the task.",
						},
						{
							SysCreatedOn: "2025-10-21T12:42:07.000+0000Z",
							Value:        "Erika Mustermann has requested approval of the task.",
						},
						{
							SysCreatedOn: "2025-10-21T12:42:06.000+0000Z",
							Value:        "No Decisions matched. CMP Change Policy has been skipped (Change Approval Policy Action).",
						},
						{
							SysCreatedOn: "2025-10-21T12:42:06.000+0000Z",
							Value:        "No Decisions matched. CMP Change Policy has been skipped (Change Approval Policy Action).",
						},
					},
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:             815,
					ChangeRequired: true,
					ChangeStatus:   mcmp.ChangeStatusWaitingForApproval,
					Status:         mcmp.JobStatusWaitingForApproval,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return nil
			},
			expectedStatusCode: http.StatusOK,
			expectedStatus:     "success",
			validateJob: func(t *testing.T, job *mcmp.Job) {
				if job.ChangeStatus != mcmp.ChangeStatusCanceled {
					t.Errorf("Expected ChangeStatus 'canceled', got '%s'", job.ChangeStatus)
				}
				if job.Status != mcmp.JobStatusCanceled {
					t.Errorf("Expected JobStatus 'canceled', got '%s'", job.Status)
				}
				if job.ChangeError == nil || *job.ChangeError != "The change request has been canceled." {
					t.Errorf("Expected ChangeError 'The change request has been canceled.', got '%v'", job.ChangeError)
				}
			},
		},
		{
			name:               "InvalidHTTPMethod",
			method:             http.MethodGet,
			path:               "/callback/change/123",
			body:               nil,
			mockFindJob:        nil,
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusMethodNotAllowed,
		},
		{
			name:               "MissingIDInURL",
			method:             http.MethodPost,
			path:               "/callback/change/",
			body:               ChangeCallbackRequest{},
			mockFindJob:        nil,
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusBadRequest,
		},
		{
			name:               "InvalidIDFormat",
			method:             http.MethodPost,
			path:               "/callback/change/abc",
			body:               ChangeCallbackRequest{},
			mockFindJob:        nil,
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusBadRequest,
		},
		{
			name:   "JobNotFound",
			method: http.MethodPost,
			path:   "/callback/change/999",
			body: ChangeCallbackRequest{
				Success: true,
				Result: struct {
					Approval        string `json:"approval"`
					ApprovalSet     string `json:"approval_set"`
					ApprovalHistory []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					} `json:"approval_history"`
				}{
					Approval: "approved",
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return nil, errors.New("job not found")
			},
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusNotFound,
		},
		{
			name:   "InvalidChangeStatus",
			method: http.MethodPost,
			path:   "/callback/change/789",
			body: ChangeCallbackRequest{
				Success: true,
				Result: struct {
					Approval        string `json:"approval"`
					ApprovalSet     string `json:"approval_set"`
					ApprovalHistory []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					} `json:"approval_history"`
				}{
					Approval: "approved",
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:             789,
					ChangeRequired: true,
					ChangeStatus:   mcmp.ChangeStatusApproved, // Already approved
				}, nil
			},
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusBadRequest,
		},
		{
			name:   "UpdateJobError",
			method: http.MethodPost,
			path:   "/callback/change/321",
			body: ChangeCallbackRequest{
				Success: true,
				Result: struct {
					Approval        string `json:"approval"`
					ApprovalSet     string `json:"approval_set"`
					ApprovalHistory []struct {
						SysCreatedOn string `json:"sys_created_on"`
						Value        string `json:"value"`
					} `json:"approval_history"`
				}{
					Approval: "approved",
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:             321,
					ChangeRequired: true,
					ChangeStatus:   mcmp.ChangeStatusWaitingForApproval,
					Status:         mcmp.JobStatusWaitingForApproval,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return errors.New("database error")
			},
			expectedStatusCode: http.StatusInternalServerError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Logf("=== Testing: %s ===", tt.name)
			t.Logf("Method: %s, Path: %s", tt.method, tt.path)

			// Create mock client
			var capturedJob *mcmp.Job
			mockClient := &mockMCMPClient{
				findJobByIDFunc: tt.mockFindJob,
				updateJobFunc: func(job *mcmp.Job) error {
					capturedJob = job
					if tt.mockUpdateJob != nil {
						return tt.mockUpdateJob(job)
					}
					return nil
				},
			}

			// Create server with mock
			srv := &Server{
				mcmpClient: mockClient,
				logger:     createTestLogger(),
				config:     &testConfig,
			}

			// Create request body
			var bodyReader *bytes.Reader
			if tt.body != nil {
				bodyBytes, err := json.Marshal(tt.body)
				if err != nil {
					t.Fatalf("Failed to marshal request body: %v", err)
				}
				bodyReader = bytes.NewReader(bodyBytes)
			} else {
				bodyReader = bytes.NewReader([]byte{})
			}

			// Create HTTP request and response recorder
			req := httptest.NewRequest(tt.method, tt.path, bodyReader)
			w := httptest.NewRecorder()

			// Execute handler
			srv.handleChangeCallback(w, req)

			// Verify status code
			if w.Code != tt.expectedStatusCode {
				t.Errorf("Status code mismatch: expected=%d, got=%d", tt.expectedStatusCode, w.Code)
			} else {
				t.Logf("✓ Status code correct: %d", w.Code)
			}

			// Validate response if specified
			if tt.validateResponse != nil {
				t.Log("Validating response...")
				tt.validateResponse(t, w.Body.Bytes())
			}

			// Validate job state if specified
			if tt.validateJob != nil && capturedJob != nil {
				t.Log("Validating job state...")
				tt.validateJob(t, capturedJob)
			}

			t.Logf("=== Completed: %s ===\n", tt.name)
		})
	}
}

// TestServer_handleChangeCallback_InvalidJSON tests invalid JSON
func TestServer_handleChangeCallback_InvalidJSON(t *testing.T) {
	testConfig := config.Config{
		GENERAL: config.General{
			Port:  8080,
			Debug: false,
		},
		LOGGING: logging.LogConfig{
			Level:      "DEBUG",
			Output:     "console",
			Filename:   "",
			MaxSize:    0,
			MaxBackups: 0,
			MaxAge:     0,
			Compress:   false,
		},
	}

	mockClient := &mockMCMPClient{}

	srv := &Server{
		mcmpClient: mockClient,
		logger:     createTestLogger(),
		config:     &testConfig,
	}

	// Invalid JSON
	invalidJSON := []byte(`{"success": true, "result": invalid}`)
	req := httptest.NewRequest(http.MethodPost, "/callback/change/123", bytes.NewReader(invalidJSON))
	w := httptest.NewRecorder()

	srv.handleChangeCallback(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status code %d, got %d", http.StatusBadRequest, w.Code)
	}
}

// TestServer_handleQuickDiscoveryCallback tests the handleQuickDiscoveryCallback method
func TestServer_handleQuickDiscoveryCallback(t *testing.T) {
	testConfig := config.Config{
		GENERAL: config.General{
			Port:  8080,
			Debug: false,
		},
		LOGGING: logging.LogConfig{
			Level:      "DEBUG",
			Output:     "console",
			Filename:   "",
			MaxSize:    0,
			MaxBackups: 0,
			MaxAge:     0,
			Compress:   false,
		},
	}

	tests := []struct {
		name               string
		method             string
		path               string
		body               interface{}
		mockFindJob        func(id int64) (*mcmp.Job, error)
		mockUpdateJob      func(job *mcmp.Job) error
		expectedStatusCode int
		expectedStatus     string
		validateResponse   func(t *testing.T, body []byte)
		validateJob        func(t *testing.T, job *mcmp.Job)
	}{
		{
			name:   "SuccessfulQuickDiscovery",
			method: http.MethodPost,
			path:   "/callback/quickdiscovery/123",
			body: QuickDiscoveryCallbackRequest{
				Success:      true,
				ErrorMessage: "",
				Result: struct {
					CiSysid string `json:"ci_sysid"`
					CiName  string `json:"ci_name"`
				}{
					CiSysid: "ba3ff73f1b1b4c9040d5bb31dd4bcb88",
					CiName:  "examplek001",
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:                   123,
					QuickDiscovery:       true,
					QuickDiscoveryStatus: mcmp.QuickdiscoveryStatusWaiting,
					Status:               mcmp.JobStatusWaitingForQuickdiscovery,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return nil
			},
			expectedStatusCode: http.StatusOK,
			expectedStatus:     "success",
			validateResponse: func(t *testing.T, body []byte) {
				var response map[string]interface{}
				if err := json.Unmarshal(body, &response); err != nil {
					t.Fatalf("Error parsing response: %v", err)
				}
				if response["status"] != "success" {
					t.Errorf("Expected status 'success', got '%v'", response["status"])
				}
				if response["job_id"] != float64(123) {
					t.Errorf("Expected job_id 123, got %v", response["job_id"])
				}
				if response["new_status"] != string(mcmp.QuickdiscoveryStatusSuccessful) {
					t.Errorf("Expected new_status 'successful', got '%v'", response["new_status"])
				}
			},
			validateJob: func(t *testing.T, job *mcmp.Job) {
				if job.QuickDiscoveryStatus != mcmp.QuickdiscoveryStatusSuccessful {
					t.Errorf("Expected QuickDiscoveryStatus 'successful', got '%s'", job.QuickDiscoveryStatus)
				}
				if job.QuickDiscoveryCiSysid == nil || *job.QuickDiscoveryCiSysid != "ba3ff73f1b1b4c9040d5bb31dd4bcb88" {
					t.Errorf("Expected CiSysid 'ba3ff73f1b1b4c9040d5bb31dd4bcb88', got '%v'", job.QuickDiscoveryCiSysid)
				}
				if job.QuickDiscoveryCiName == nil || *job.QuickDiscoveryCiName != "examplek001" {
					t.Errorf("Expected CiName 'examplek001', got '%v'", job.QuickDiscoveryCiName)
				}
			},
		},
		{
			name:   "FailedQuickDiscoveryWithErrorMessage",
			method: http.MethodPost,
			path:   "/callback/quickdiscovery/456",
			body: QuickDiscoveryCallbackRequest{
				Success:      false,
				ErrorMessage: "Invalid IP",
				Result: struct {
					CiSysid string `json:"ci_sysid"`
					CiName  string `json:"ci_name"`
				}{},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:                   456,
					QuickDiscovery:       true,
					QuickDiscoveryStatus: mcmp.QuickdiscoveryStatusWaiting,
					Status:               mcmp.JobStatusWaitingForQuickdiscovery,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return nil
			},
			expectedStatusCode: http.StatusOK,
			expectedStatus:     "success",
			validateJob: func(t *testing.T, job *mcmp.Job) {
				if job.QuickDiscoveryStatus != mcmp.QuickdiscoveryStatusFailed {
					t.Errorf("Expected QuickDiscoveryStatus 'failed', got '%s'", job.QuickDiscoveryStatus)
				}
				if job.QuickDiscoveryError == nil || *job.QuickDiscoveryError != "Invalid IP" {
					t.Errorf("Expected QuickDiscoveryError 'Invalid IP', got '%v'", job.QuickDiscoveryError)
				}
			},
		},
		{
			name:               "InvalidHTTPMethod",
			method:             http.MethodGet,
			path:               "/callback/quickdiscovery/123",
			body:               nil,
			mockFindJob:        nil,
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusMethodNotAllowed,
		},
		{
			name:               "MissingIDInURL",
			method:             http.MethodPost,
			path:               "/callback/quickdiscovery/",
			body:               QuickDiscoveryCallbackRequest{},
			mockFindJob:        nil,
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusBadRequest,
		},
		{
			name:               "InvalidIDFormat",
			method:             http.MethodPost,
			path:               "/callback/quickdiscovery/abc",
			body:               QuickDiscoveryCallbackRequest{},
			mockFindJob:        nil,
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusBadRequest,
		},
		{
			name:   "JobNotFound",
			method: http.MethodPost,
			path:   "/callback/quickdiscovery/999",
			body: QuickDiscoveryCallbackRequest{
				Success: true,
				Result: struct {
					CiSysid string `json:"ci_sysid"`
					CiName  string `json:"ci_name"`
				}{
					CiSysid: "ba3ff73f1b1b4c9040d5bb31dd4bcb88",
					CiName:  "examplek001",
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return nil, errors.New("job not found")
			},
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusNotFound,
		},
		{
			name:   "InvalidQuickDiscoveryStatus",
			method: http.MethodPost,
			path:   "/callback/quickdiscovery/789",
			body: QuickDiscoveryCallbackRequest{
				Success: true,
				Result: struct {
					CiSysid string `json:"ci_sysid"`
					CiName  string `json:"ci_name"`
				}{
					CiSysid: "ba3ff73f1b1b4c9040d5bb31dd4bcb88",
					CiName:  "examplek001",
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:                   789,
					QuickDiscovery:       true,
					QuickDiscoveryStatus: mcmp.QuickdiscoveryStatusSuccessful, // Already successful
					Status:               mcmp.JobStatusWaitingForQuickdiscovery,
				}, nil
			},
			mockUpdateJob:      nil,
			expectedStatusCode: http.StatusBadRequest,
		},
		{
			name:   "UpdateJobError",
			method: http.MethodPost,
			path:   "/callback/quickdiscovery/321",
			body: QuickDiscoveryCallbackRequest{
				Success: true,
				Result: struct {
					CiSysid string `json:"ci_sysid"`
					CiName  string `json:"ci_name"`
				}{
					CiSysid: "ba3ff73f1b1b4c9040d5bb31dd4bcb88",
					CiName:  "examplek001",
				},
			},
			mockFindJob: func(id int64) (*mcmp.Job, error) {
				return &mcmp.Job{
					ID:                   321,
					QuickDiscovery:       true,
					QuickDiscoveryStatus: mcmp.QuickdiscoveryStatusWaiting,
					Status:               mcmp.JobStatusWaitingForQuickdiscovery,
				}, nil
			},
			mockUpdateJob: func(job *mcmp.Job) error {
				return errors.New("database error")
			},
			expectedStatusCode: http.StatusInternalServerError,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Logf("=== Testing: %s ===", tt.name)
			t.Logf("Method: %s, Path: %s", tt.method, tt.path)

			// Create mock client
			var capturedJob *mcmp.Job
			mockClient := &mockMCMPClient{
				findJobByIDFunc: tt.mockFindJob,
				updateJobFunc: func(job *mcmp.Job) error {
					capturedJob = job
					if tt.mockUpdateJob != nil {
						return tt.mockUpdateJob(job)
					}
					return nil
				},
			}

			// Create server with mock
			srv := &Server{
				mcmpClient: mockClient,
				logger:     createTestLogger(),
				config:     &testConfig,
			}

			// Create request body
			var bodyReader *bytes.Reader
			if tt.body != nil {
				bodyBytes, err := json.Marshal(tt.body)
				if err != nil {
					t.Fatalf("Failed to marshal request body: %v", err)
				}
				bodyReader = bytes.NewReader(bodyBytes)
			} else {
				bodyReader = bytes.NewReader([]byte{})
			}

			// Create HTTP request and response recorder
			req := httptest.NewRequest(tt.method, tt.path, bodyReader)
			w := httptest.NewRecorder()

			// Execute handler
			srv.handleQuickDiscoveryCallback(w, req)

			// Verify status code
			if w.Code != tt.expectedStatusCode {
				t.Errorf("Status code mismatch: expected=%d, got=%d", tt.expectedStatusCode, w.Code)
			} else {
				t.Logf("✓ Status code correct: %d", w.Code)
			}

			// Validate response if specified
			if tt.validateResponse != nil {
				t.Log("Validating response...")
				tt.validateResponse(t, w.Body.Bytes())
			}

			// Validate job state if specified
			if tt.validateJob != nil && capturedJob != nil {
				t.Log("Validating job state...")
				tt.validateJob(t, capturedJob)
			}

			t.Logf("=== Completed: %s ===\n", tt.name)
		})
	}
}

// TestServer_handleQuickDiscoveryCallback_InvalidJSON tests invalid JSON
func TestServer_handleQuickDiscoveryCallback_InvalidJSON(t *testing.T) {
	testConfig := config.Config{
		GENERAL: config.General{
			Port:  8080,
			Debug: false,
		},
		LOGGING: logging.LogConfig{
			Level:      "DEBUG",
			Output:     "console",
			Format:     "plain",
			Filename:   "",
			MaxSize:    0,
			MaxBackups: 0,
			MaxAge:     0,
			Compress:   false,
		},
	}

	mockClient := &mockMCMPClient{}

	srv := &Server{
		mcmpClient: mockClient,
		logger:     createTestLogger(),
		config:     &testConfig,
	}

	// Invalid JSON
	invalidJSON := []byte(`{"success": true, "result": invalid}`)
	req := httptest.NewRequest(http.MethodPost, "/callback/quickdiscovery/123", bytes.NewReader(invalidJSON))
	w := httptest.NewRecorder()

	srv.handleQuickDiscoveryCallback(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("Expected status code %d, got %d", http.StatusBadRequest, w.Code)
	}
}

// TestExtractIDFromPath tests the extractIDFromPath helper function
func TestExtractIDFromPath(t *testing.T) {
	tests := []struct {
		name     string
		path     string
		prefix   string
		expected string
	}{
		{
			name:     "ValidID",
			path:     "/callback/change/123",
			prefix:   "/callback/change/",
			expected: "123",
		},
		{
			name:     "IDWithTrailingSlash",
			path:     "/callback/change/456/",
			prefix:   "/callback/change/",
			expected: "456",
		},
		{
			name:     "MissingPrefix",
			path:     "/other/path/123",
			prefix:   "/callback/change/",
			expected: "",
		},
		{
			name:     "EmptyID",
			path:     "/callback/change/",
			prefix:   "/callback/change/",
			expected: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Logf("Testing: %s - Path: %s, Prefix: %s", tt.name, tt.path, tt.prefix)
			result := extractIDFromPath(tt.path, tt.prefix)
			if result != tt.expected {
				t.Errorf("ID mismatch: expected='%s', got='%s'", tt.expected, result)
			} else {
				t.Logf("✓ Extracted ID correctly: '%s'", result)
			}
		})
	}
}

// TestServer_Close tests the Close method
func TestServer_Close(t *testing.T) {
	t.Run("SuccessfulClose", func(t *testing.T) {
		t.Log("Testing successful close operation")
		closeCalled := false
		mockClient := &mockMCMPClient{
			closeFunc: func() error {
				closeCalled = true
				return nil
			},
		}

		srv := &Server{
			mcmpClient: mockClient,
		}

		err := srv.Close()
		if err != nil {
			t.Errorf("Unexpected error during close: %v", err)
		}
		if !closeCalled {
			t.Error("Close method was not invoked on MCMP client")
		} else {
			t.Log("✓ Close method invoked successfully")
		}
	})

	t.Run("CloseWithError", func(t *testing.T) {
		t.Log("Testing close operation with error")
		expectedErr := errors.New("close error")
		mockClient := &mockMCMPClient{
			closeFunc: func() error {
				return expectedErr
			},
		}

		srv := &Server{
			mcmpClient: mockClient,
			logger:     createTestLogger(),
		}

		err := srv.Close()
		if err != expectedErr {
			t.Errorf("Error mismatch: expected='%v', got='%v'", expectedErr, err)
		} else {
			t.Log("✓ Error handled correctly")
		}
	})

	t.Run("CloseWithNilClient", func(t *testing.T) {
		t.Log("Testing close operation with nil client")
		srv := &Server{
			mcmpClient: nil,
			logger:     createTestLogger(),
		}

		err := srv.Close()
		if err != nil {
			t.Errorf("Unexpected error with nil client: %v", err)
		} else {
			t.Log("✓ Nil client handled gracefully")
		}
	})
}

// BenchmarkServer_handleChangeCallback benchmarks the handleChangeCallback method
func BenchmarkServer_handleChangeCallback(b *testing.B) {
	testConfig := config.Config{
		GENERAL: config.General{
			Port:  8080,
			Debug: false,
		},
		LOGGING: logging.LogConfig{
			Level:      "INFO", // Use INFO level for benchmarks to reduce overhead
			Output:     "console",
			Filename:   "",
			MaxSize:    0,
			MaxBackups: 0,
			MaxAge:     0,
			Compress:   false,
		},
	}

	mockClient := &mockMCMPClient{
		findJobByIDFunc: func(id int64) (*mcmp.Job, error) {
			return &mcmp.Job{
				ID:             123,
				ChangeRequired: true,
				ChangeStatus:   mcmp.ChangeStatusWaitingForApproval,
			}, nil
		},
		updateJobFunc: func(job *mcmp.Job) error {
			return nil
		},
	}

	srv := &Server{
		mcmpClient: mockClient,
		logger:     createTestLogger(),
		config:     &testConfig,
	}

	body := ChangeCallbackRequest{
		Success: true,
		Result: struct {
			Approval        string `json:"approval"`
			ApprovalSet     string `json:"approval_set"`
			ApprovalHistory []struct {
				SysCreatedOn string `json:"sys_created_on"`
				Value        string `json:"value"`
			} `json:"approval_history"`
		}{
			Approval: "approved",
		},
	}

	bodyBytes, _ := json.Marshal(body)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		req := httptest.NewRequest(http.MethodPost, fmt.Sprintf("/callback/change/%d", 123), bytes.NewReader(bodyBytes))
		w := httptest.NewRecorder()
		srv.handleChangeCallback(w, req)
	}
}

// BenchmarkServer_handleQuickDiscoveryCallback benchmarks the handleQuickDiscoveryCallback method
func BenchmarkServer_handleQuickDiscoveryCallback(b *testing.B) {
	testConfig := config.Config{
		GENERAL: config.General{
			Port:  8080,
			Debug: false,
		},
		LOGGING: logging.LogConfig{
			Level:      "INFO", // Use INFO level for benchmarks to reduce overhead
			Output:     "console",
			Filename:   "",
			MaxSize:    0,
			MaxBackups: 0,
			MaxAge:     0,
			Compress:   false,
		},
	}

	mockClient := &mockMCMPClient{
		findJobByIDFunc: func(id int64) (*mcmp.Job, error) {
			return &mcmp.Job{
				ID:                   123,
				QuickDiscovery:       true,
				QuickDiscoveryStatus: mcmp.QuickdiscoveryStatusWaiting,
			}, nil
		},
		updateJobFunc: func(job *mcmp.Job) error {
			return nil
		},
	}

	srv := &Server{
		mcmpClient: mockClient,
		logger:     createTestLogger(),
		config:     &testConfig,
	}

	body := QuickDiscoveryCallbackRequest{
		Success: true,
		Result: struct {
			CiSysid string `json:"ci_sysid"`
			CiName  string `json:"ci_name"`
		}{
			CiSysid: "ba3ff73f1b1b4c9040d5bb31dd4bcb88",
			CiName:  "examplek001",
		},
	}

	bodyBytes, _ := json.Marshal(body)

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		req := httptest.NewRequest(http.MethodPost, fmt.Sprintf("/callback/quickdiscovery/%d", 123), bytes.NewReader(bodyBytes))
		w := httptest.NewRecorder()
		srv.handleQuickDiscoveryCallback(w, req)
	}
}
