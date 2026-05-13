package processor

import (
	"context"
	"encoding/json"
	"reflect"
	"testing"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-patchnight/pkg/clients/mcmp"
	"github.com/it-at-m/mcmp/mcmp-eai-patchnight/pkg/clients/patchnight"
)

// MockPatchnightClient implements PatchnightClientInterface for testing
type MockPatchnightClient struct {
	debug bool
}

func parseTimeLocal(timeStr string) time.Time {
	t, err := time.Parse("2006-01-02T15:04:05", timeStr)
	if err != nil {
		panic(err)
	}
	return t
}

// FetchPatchnightDates - updated method signature to match interface
func (m *MockPatchnightClient) FetchLinuxPatchnightDates(ctx context.Context) ([]patchnight.PatchnightDate, error) {
	// Test data as simple local times without timezone complexity
	return []patchnight.PatchnightDate{
		{Environment: "k", Date: "2025-04-11", StartDate: parseTimeLocal("2025-04-11T15:00:00"), EndDate: parseTimeLocal("2025-04-12T01:00:00")},
		{Environment: "p", Date: "2025-04-28", StartDate: parseTimeLocal("2025-04-28T20:00:00"), EndDate: parseTimeLocal("2025-04-29T06:00:00")},
		{Environment: "k", Date: "2025-05-09", StartDate: parseTimeLocal("2025-05-09T15:00:00"), EndDate: parseTimeLocal("2025-05-10T01:00:00")},
		{Environment: "p", Date: "2025-05-26", StartDate: parseTimeLocal("2025-05-26T20:00:00"), EndDate: parseTimeLocal("2025-05-27T06:00:00")},
		{Environment: "k", Date: "2025-06-06", StartDate: parseTimeLocal("2025-06-06T15:00:00"), EndDate: parseTimeLocal("2025-06-07T01:00:00")},
		{Environment: "p", Date: "2025-06-23", StartDate: parseTimeLocal("2025-06-23T20:00:00"), EndDate: parseTimeLocal("2025-06-24T06:00:00")},
		{Environment: "k", Date: "2025-07-11", StartDate: parseTimeLocal("2025-07-11T15:00:00"), EndDate: parseTimeLocal("2025-07-12T01:00:00")},
		{Environment: "p", Date: "2025-07-28", StartDate: parseTimeLocal("2025-07-28T20:00:00"), EndDate: parseTimeLocal("2025-07-29T06:00:00")},
		{Environment: "k", Date: "2025-08-08", StartDate: parseTimeLocal("2025-08-08T15:00:00"), EndDate: parseTimeLocal("2025-08-09T01:00:00")},
		{Environment: "p", Date: "2025-08-25", StartDate: parseTimeLocal("2025-08-25T20:00:00"), EndDate: parseTimeLocal("2025-08-26T06:00:00")},
		{Environment: "k", Date: "2025-08-29", StartDate: parseTimeLocal("2025-08-29T15:00:00"), EndDate: parseTimeLocal("2025-08-30T01:00:00")},
		{Environment: "p", Date: "2025-09-15", StartDate: parseTimeLocal("2025-09-15T20:00:00"), EndDate: parseTimeLocal("2025-09-16T06:00:00")},
		{Environment: "k", Date: "2025-10-10", StartDate: parseTimeLocal("2025-10-10T15:00:00"), EndDate: parseTimeLocal("2025-10-11T01:00:00")},
		{Environment: "p", Date: "2025-10-27", StartDate: parseTimeLocal("2025-10-27T20:00:00"), EndDate: parseTimeLocal("2025-10-28T06:00:00")},
		{Environment: "k", Date: "2025-11-07", StartDate: parseTimeLocal("2025-11-07T15:00:00"), EndDate: parseTimeLocal("2025-11-08T01:00:00")},
		{Environment: "p", Date: "2025-11-24", StartDate: parseTimeLocal("2025-11-24T20:00:00"), EndDate: parseTimeLocal("2025-11-25T06:00:00")},
		{Environment: "k", Date: "2025-11-28", StartDate: parseTimeLocal("2025-11-28T15:00:00"), EndDate: parseTimeLocal("2025-11-29T01:00:00")},
		{Environment: "p", Date: "2025-12-15", StartDate: parseTimeLocal("2025-12-15T20:00:00"), EndDate: parseTimeLocal("2025-12-16T06:00:00")},
	}, nil
}

// FetchIncludedServers - updated method signature to match interface
func (m *MockPatchnightClient) FetchLinuxIncludedServers(ctx context.Context) ([]patchnight.PatchnightLinuxIncludedServer, error) {
	return []patchnight.PatchnightLinuxIncludedServer{
		{Environment: "k", Name: "linuxk001.example.com", StartTime: "15:00", EndTime: "17:00", OS: "RedHat", OSVersion: "7.9"},
		{Environment: "k", Name: "linuxk002.example.com", StartTime: "19:00", EndTime: "21:00", OS: "SLES", OSVersion: "15.4"},
		{Environment: "k", Name: "linuxk003.example.com", StartTime: "21:00", EndTime: "23:00", OS: "RedHat", OSVersion: "9.5"},
		{Environment: "k", Name: "linuxk004.example.com", StartTime: "23:00", EndTime: "01:00", OS: "OracleLinux", OSVersion: "8.9"},
		{Environment: "p", Name: "linuxp001.example.com", StartTime: "20:00", EndTime: "22:00", OS: "RedHat", OSVersion: "9.6"},
		{Environment: "p", Name: "linuxp002.example.com", StartTime: "22:00", EndTime: "00:00", OS: "RedHat", OSVersion: "10.0"},
		{Environment: "p", Name: "linuxp003.example.com", StartTime: "00:00", EndTime: "02:00", OS: "SLES", OSVersion: "15.4"},
		{Environment: "p", Name: "linuxp004.example.com", StartTime: "02:00", EndTime: "04:00", OS: "OracleLinux", OSVersion: "8.10"},
		{Environment: "p", Name: "linuxp005.example.com", StartTime: "04:00", EndTime: "06:00", OS: "RedHat", OSVersion: "7.9"},
	}, nil
}

// FetchExcludedServers - updated method signature to match interface
func (m *MockPatchnightClient) FetchLinuxExcludedServers(ctx context.Context) ([]patchnight.PatchnightLinuxExcludedServer, error) {
	return []patchnight.PatchnightLinuxExcludedServer{
		{Name: "linuxc001.example.com", OS: "SLES", OSVersion: "15.1"},
		{Name: "linuxk005.example.com", OS: "RedHat", OSVersion: "8.10"},
		{Name: "linuxp006.example.com", OS: "OracleLinux", OSVersion: "8.6"},
	}, nil
}

func (m *MockPatchnightClient) FetchWindowsPatchnightDates(ctx context.Context) ([]patchnight.PatchnightDate, error) {
	return []patchnight.PatchnightDate{}, nil
}

func (m *MockPatchnightClient) FetchWindowsKIncludedServers(ctx context.Context) ([]string, error) {
	return []string{}, nil
}

func (m *MockPatchnightClient) FetchWindowsPIncludedServers(ctx context.Context) ([]string, error) {
	return []string{}, nil
}

func (m *MockPatchnightClient) FetchWindowsExcludedServers(ctx context.Context) ([]string, error) {
	return []string{}, nil
}

func (m *MockPatchnightClient) FetchWindowsUpdateStatus(ctx context.Context) ([]patchnight.WindowsPatchnightStatus, error) {
	return []patchnight.WindowsPatchnightStatus{}, nil
}

func (m *MockPatchnightClient) EnableDebug() {
	m.debug = true
}

// Helper function to parse time strings - renamed to avoid conflict with processor.go parseTime
func parseTimeHelper(timeStr string) time.Time {
	t, err := time.Parse("2006-01-02T15:04:05", timeStr)
	if err != nil {
		panic(err)
	}
	return t
}

// Helper function to parse time strings in Berlin timezone
func parseTimeBerlin(timeStr string) time.Time {
	berlin, _ := time.LoadLocation("Europe/Berlin")
	t, err := time.Parse("2006-01-02T15:04:05", timeStr)
	if err != nil {
		panic(err)
	}
	return t.In(berlin)
}

func TestServiceProcessor_ProcessPatchnightData(t *testing.T) {
	testCases := []struct {
		name                    string
		currentTime             string
		expectedNextPatchnightK string
		expectedNextPatchnightP string
		description             string
	}{
		{
			name:                    "Before July K patchnight",
			currentTime:             "2025-07-02T15:00:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should find next patchnight for both environments",
		},
		{
			name:                    "Just before K patchnight starts",
			currentTime:             "2025-07-11T14:59:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should still show upcoming K patchnight",
		},
		{
			name:                    "During K patchnight - early phase",
			currentTime:             "2025-07-11T15:59:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running K patchnight",
		},
		{
			name:                    "During K patchnight - mid phase",
			currentTime:             "2025-07-11T17:11:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running K patchnight",
		},
		{
			name:                    "During K patchnight - evening",
			currentTime:             "2025-07-11T19:00:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running K patchnight",
		},
		{
			name:                    "During K patchnight - late evening",
			currentTime:             "2025-07-11T21:45:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running K patchnight",
		},
		{
			name:                    "During K patchnight - near midnight",
			currentTime:             "2025-07-11T23:34:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running K patchnight",
		},
		{
			name:                    "During K patchnight - after midnight",
			currentTime:             "2025-07-12T00:34:00",
			expectedNextPatchnightK: "2025-07-11",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running K patchnight",
		},
		{
			name:                    "After K patchnight ends",
			currentTime:             "2025-07-12T01:12:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should find next K patchnight after current one ends",
		},
		{
			name:                    "Just before P patchnight",
			currentTime:             "2025-07-28T19:59:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should show upcoming P patchnight",
		},
		{
			name:                    "During P patchnight - start",
			currentTime:             "2025-07-28T20:01:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running P patchnight",
		},
		{
			name:                    "During P patchnight - evening",
			currentTime:             "2025-07-28T22:30:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running P patchnight",
		},
		{
			name:                    "During P patchnight - midnight crossover",
			currentTime:             "2025-07-29T00:30:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running P patchnight past midnight",
		},
		{
			name:                    "During P patchnight - early morning",
			currentTime:             "2025-07-29T02:30:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running P patchnight",
		},
		{
			name:                    "During P patchnight - late morning",
			currentTime:             "2025-07-29T04:30:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-07-28",
			description:             "Should detect running P patchnight",
		},
		{
			name:                    "After P patchnight ends",
			currentTime:             "2025-07-29T06:30:00",
			expectedNextPatchnightK: "2025-08-08",
			expectedNextPatchnightP: "2025-08-25",
			description:             "Should find next P patchnight after current one ends",
		},
		{
			name:                    "Future date",
			currentTime:             "2025-08-11T00:30:00",
			expectedNextPatchnightK: "2025-08-29",
			expectedNextPatchnightP: "2025-08-25",
			description:             "Should find next patchnights in the future",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			// Setup
			mockClient := &MockPatchnightClient{}
			processor := NewServiceProcessor(mockClient, false)

			// Set current time for this test case
			currentTime := parseTimeBerlin(tc.currentTime)
			processor.SetCurrentTime(func() time.Time { return currentTime })

			// Execute
			err := processor.ProcessPatchnightData()
			if err != nil {
				t.Fatalf("ProcessPatchnightData() returned error: %v", err)
			}
			result, err := processor.GetPatchnightData()
			if err != nil {
				t.Fatalf("GetPatchnightData() returned error: %v", err)
			}

			// Verify result is not nil
			if result == nil {
				t.Fatal("ProcessPatchnightData() returned nil result")
			}

			// Verify total server count (9 included + 3 excluded = 12)
			expectedTotalServers := 12
			if len(result.Servers) != expectedTotalServers {
				t.Errorf("Expected %d servers, got %d", expectedTotalServers, len(result.Servers))
			}

			// Verify servers are sorted by name
			for i := 1; i < len(result.Servers); i++ {
				if result.Servers[i-1].Name >= result.Servers[i].Name {
					t.Errorf("Servers are not sorted by name: %s >= %s",
						result.Servers[i-1].Name, result.Servers[i].Name)
				}
			}

			// Count included and excluded servers
			includedCount := 0
			excludedCount := 0
			for _, server := range result.Servers {
				if server.Include {
					includedCount++
				} else {
					excludedCount++
				}
			}

			if includedCount != 9 {
				t.Errorf("Expected 9 included servers, got %d", includedCount)
			}
			if excludedCount != 3 {
				t.Errorf("Expected 3 excluded servers, got %d", excludedCount)
			}

			// Verify included servers have maintenance windows
			for _, server := range result.Servers {
				if server.Include && server.Environment != nil {
					if server.StartDate == nil || server.EndDate == nil {
						t.Errorf("Included server %s missing maintenance window", server.Name)
					}
				}
			}

			// Verify excluded servers don't have maintenance windows
			for _, server := range result.Servers {
				if !server.Include {
					if server.StartDate != nil || server.EndDate != nil {
						t.Errorf("Excluded server %s should not have maintenance window", server.Name)
					}
				}
			}
		})
	}
}

func TestServiceProcessor_ExportAsJSON(t *testing.T) {
	// Setup
	mockClient := &MockPatchnightClient{}
	processor := NewServiceProcessor(mockClient, false)

	// Set a fixed time for consistent testing
	fixedTime := parseTimeBerlin("2025-07-02T15:00:00")
	processor.SetCurrentTime(func() time.Time { return fixedTime })

	// Execute
	jsonString, err := processor.ExportToJSON()
	if err != nil {
		t.Fatalf("ExportToJSON() returned error: %v", err)
	}

	// Verify JSON is not empty
	if len(jsonString) == 0 {
		t.Fatal("ExportToJSON() returned empty string")
	}

	expectedJSON := `{
  "server": [
    {
      "name": "linuxc001.example.com",
      "include": false
    },
    {
      "env": "k",
      "name": "linuxk001.example.com",
      "include": true,
      "start_date": "2025-07-11T15:00:00+02:00",
      "end_date": "2025-07-11T17:00:00+02:00"
    },
    {
      "env": "k",
      "name": "linuxk002.example.com",
      "include": true,
      "start_date": "2025-07-11T19:00:00+02:00",
      "end_date": "2025-07-11T21:00:00+02:00"
    },
    {
      "env": "k",
      "name": "linuxk003.example.com",
      "include": true,
      "start_date": "2025-07-11T21:00:00+02:00",
      "end_date": "2025-07-11T23:00:00+02:00"
    },
    {
      "env": "k",
      "name": "linuxk004.example.com",
      "include": true,
      "start_date": "2025-07-11T23:00:00+02:00",
      "end_date": "2025-07-12T01:00:00+02:00"
    },
    {
      "name": "linuxk005.example.com",
      "include": false
    },
    {
      "env": "p",
      "name": "linuxp001.example.com",
      "include": true,
      "start_date": "2025-07-28T20:00:00+02:00",
      "end_date": "2025-07-28T22:00:00+02:00"
    },
    {
      "env": "p",
      "name": "linuxp002.example.com",
      "include": true,
      "start_date": "2025-07-28T22:00:00+02:00",
      "end_date": "2025-07-29T00:00:00+02:00"
    },
    {
      "env": "p",
      "name": "linuxp003.example.com",
      "include": true,
      "start_date": "2025-07-29T00:00:00+02:00",
      "end_date": "2025-07-29T02:00:00+02:00"
    },
    {
      "env": "p",
      "name": "linuxp004.example.com",
      "include": true,
      "start_date": "2025-07-29T02:00:00+02:00",
      "end_date": "2025-07-29T04:00:00+02:00"
    },
    {
      "env": "p",
      "name": "linuxp005.example.com",
      "include": true,
      "start_date": "2025-07-29T04:00:00+02:00",
      "end_date": "2025-07-29T06:00:00+02:00"
    },
    {
      "name": "linuxp006.example.com",
      "include": false
    }
  ]
}`
	var actual, expected map[string]interface{}
	if err := json.Unmarshal([]byte(jsonString), &actual); err != nil {
		t.Fatalf("Failed to parse actual JSON: %v", err)
	}
	if err := json.Unmarshal([]byte(expectedJSON), &expected); err != nil {
		t.Fatalf("Failed to parse expected JSON: %v", err)
	}

	if !reflect.DeepEqual(actual, expected) {
		t.Error("JSON structure does not match expected format")
	}
}

func TestServiceProcessor_findNextPatchnight(t *testing.T) {
	mockClient := &MockPatchnightClient{}
	processor := NewServiceProcessor(mockClient, false)

	// Get test data
	ctx := context.Background()
	patchnightDates, _ := mockClient.FetchLinuxPatchnightDates(ctx)

	testCases := []struct {
		name        string
		currentTime string
		env         string
		expected    string
		description string
	}{
		{
			name:        "Find next K patchnight before July",
			currentTime: "2025-07-02T15:00:00",
			env:         "k",
			expected:    "2025-07-11",
			description: "Should find July K patchnight",
		},
		{
			name:        "Find next P patchnight before July",
			currentTime: "2025-07-02T15:00:00",
			env:         "p",
			expected:    "2025-07-28",
			description: "Should find July P patchnight",
		},
		{
			name:        "During K patchnight",
			currentTime: "2025-07-11T16:00:00",
			env:         "k",
			expected:    "2025-07-11",
			description: "Should detect running K patchnight",
		},
		{
			name:        "After K patchnight",
			currentTime: "2025-07-12T02:00:00",
			env:         "k",
			expected:    "2025-08-08",
			description: "Should find next K patchnight after current one ends",
		},
		{
			name:        "No patchnight found",
			currentTime: "2025-12-30T15:00:00",
			env:         "k",
			expected:    "",
			description: "Should return empty when no future patchnight found",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			currentTime := parseTimeLocal(tc.currentTime)
			result := processor.findNextPatchnight(patchnightDates, tc.env, currentTime)

			if tc.expected == "" {
				if result != nil {
					t.Errorf("Expected nil, got %v", result.Date)
				}
			} else {
				if result == nil {
					t.Errorf("Expected %s, got nil", tc.expected)
				} else if result.Date != tc.expected {
					t.Errorf("Expected %s, got %s", tc.expected, result.Date)
				}
			}
		})
	}
}

func TestServiceProcessor_calculatePatchnightTimes(t *testing.T) {
	mockClient := &MockPatchnightClient{}
	processor := NewServiceProcessor(mockClient, false)

	// Create a test patchnight date
	testPatchnight := &patchnight.PatchnightDate{
		Environment: "k",
		Date:        "2025-07-11",
		StartDate:   parseTimeLocal("2025-07-11T15:00:00"),
		EndDate:     parseTimeLocal("2025-07-12T01:00:00"),
	}

	testCases := []struct {
		name        string
		startTime   string
		endTime     string
		expectError bool
		description string
	}{
		{
			name:        "Normal time range",
			startTime:   "15:00",
			endTime:     "17:00",
			expectError: false,
			description: "Should handle normal time range",
		},
		{
			name:        "Cross midnight",
			startTime:   "23:00",
			endTime:     "01:00",
			expectError: false,
			description: "Should handle cross midnight range",
		},
		{
			name:        "Invalid start time",
			startTime:   "25:00",
			endTime:     "17:00",
			expectError: true,
			description: "Should return error for invalid start time",
		},
		{
			name:        "Invalid end time",
			startTime:   "15:00",
			endTime:     "25:00",
			expectError: true,
			description: "Should return error for invalid end time",
		},
		{
			name:        "Invalid time format",
			startTime:   "15",
			endTime:     "17:00",
			expectError: true,
			description: "Should return error for invalid time format",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			startDate, endDate, err := processor.calculatePatchnightTimes(testPatchnight, tc.startTime, tc.endTime)

			if tc.expectError {
				if err == nil {
					t.Error("Expected error, got nil")
				}
			} else {
				if err != nil {
					t.Errorf("Expected no error, got %v", err)
				}
				if startDate == nil || endDate == nil {
					t.Error("Expected non-nil dates")
				}
				if startDate != nil && endDate != nil && endDate.Before(*startDate) {
					t.Error("End date should be after start date")
				}
			}
		})
	}
}

func TestServiceProcessor_parseTime(t *testing.T) {
	testCases := []struct {
		name         string
		timeStr      string
		expectedHour int
		expectedMin  int
		expectError  bool
	}{
		{
			name:         "Valid time",
			timeStr:      "15:30",
			expectedHour: 15,
			expectedMin:  30,
			expectError:  false,
		},
		{
			name:         "Midnight",
			timeStr:      "00:00",
			expectedHour: 0,
			expectedMin:  0,
			expectError:  false,
		},
		{
			name:         "Late evening",
			timeStr:      "23:59",
			expectedHour: 23,
			expectedMin:  59,
			expectError:  false,
		},
		{
			name:        "Invalid format",
			timeStr:     "15-30",
			expectError: true,
		},
		{
			name:        "Invalid hour",
			timeStr:     "25:30",
			expectError: true,
		},
		{
			name:        "Invalid minute",
			timeStr:     "15:60",
			expectError: true,
		},
		{
			name:        "Empty string",
			timeStr:     "",
			expectError: true,
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			hour, minute, err := parseTime(tc.timeStr)

			if tc.expectError {
				if err == nil {
					t.Errorf("Expected error for time string '%s', but got none", tc.timeStr)
				}
				return
			}

			if err != nil {
				t.Errorf("Unexpected error for time string '%s': %v", tc.timeStr, err)
				return
			}

			if hour != tc.expectedHour {
				t.Errorf("Expected hour %d, got %d", tc.expectedHour, hour)
			}

			if minute != tc.expectedMin {
				t.Errorf("Expected minute %d, got %d", tc.expectedMin, minute)
			}
		})
	}
}

func TestServiceProcessor_NewServiceProcessor(t *testing.T) {
	mockClient := &MockPatchnightClient{}

	processor := NewServiceProcessor(mockClient, true)

	if processor == nil {
		t.Fatal("NewServiceProcessor() returned nil")
	}

	got, ok := processor.patchnightClient.(*MockPatchnightClient)
	if !ok || got != mockClient {
		t.Error("patchnightClient not set correctly")
	}

	if !processor.debug {
		t.Error("debug flag not set correctly")
	}

	if processor.currentTime == nil {
		t.Error("currentTime function not set")
	}
}

func TestServiceProcessor_SetCurrentTime(t *testing.T) {
	mockClient := &MockPatchnightClient{}
	processor := NewServiceProcessor(mockClient, false)

	testTime := time.Date(2025, 7, 11, 15, 30, 0, 0, time.UTC)
	processor.SetCurrentTime(func() time.Time { return testTime })

	if processor.currentTime().Unix() != testTime.Unix() {
		t.Error("SetCurrentTime() did not set time correctly")
	}
}

func TestServiceProcessor_PatchnightDataTimestamps(t *testing.T) {
	testCases := []struct {
		name              string
		currentTime       string
		serverName        string
		expectedStartTime string
		expectedEndTime   string
		description       string
	}{
		{
			name:              "K environment server - normal hours",
			currentTime:       "2025-07-02T15:00:00",
			serverName:        "linuxk001.example.com",
			expectedStartTime: "2025-07-11T15:00:00", // StartTime: "15:00"
			expectedEndTime:   "2025-07-11T17:00:00", // EndTime: "17:00"
			description:       "Should calculate correct timestamps for K environment server",
		},
		{
			name:              "K environment server - crossing midnight",
			currentTime:       "2025-07-02T15:00:00",
			serverName:        "linuxk004.example.com",
			expectedStartTime: "2025-07-11T23:00:00", // StartTime: "23:00"
			expectedEndTime:   "2025-07-12T01:00:00", // EndTime: "01:00" (next day)
			description:       "Should handle midnight crossing for K environment server",
		},
		{
			name:              "K environment server - evening hours",
			currentTime:       "2025-07-02T15:00:00",
			serverName:        "linuxk002.example.com",
			expectedStartTime: "2025-07-11T19:00:00", // StartTime: "19:00"
			expectedEndTime:   "2025-07-11T21:00:00", // EndTime: "21:00"
			description:       "Should calculate correct timestamps for K environment server evening hours",
		},
		{
			name:              "P environment server - normal hours",
			currentTime:       "2025-07-02T15:00:00",
			serverName:        "linuxp001.example.com",
			expectedStartTime: "2025-07-28T20:00:00", // StartTime: "20:00"
			expectedEndTime:   "2025-07-28T22:00:00", // EndTime: "22:00"
			description:       "Should calculate correct timestamps for P environment server",
		},
		{
			name:              "P environment server - midnight crossing",
			currentTime:       "2025-07-02T15:00:00",
			serverName:        "linuxp002.example.com",
			expectedStartTime: "2025-07-28T22:00:00", // StartTime: "22:00"
			expectedEndTime:   "2025-07-29T00:00:00", // EndTime: "00:00" (next day)
			description:       "Should handle midnight crossing for P environment server",
		},
		{
			name:              "P environment server - midnight start",
			currentTime:       "2025-07-02T15:00:00",
			serverName:        "linuxp003.example.com",
			expectedStartTime: "2025-07-29T00:00:00", // StartTime: "00:00"
			expectedEndTime:   "2025-07-29T02:00:00", // EndTime: "02:00"
			description:       "Should handle midnight start for P environment server",
		},
		{
			name:              "P environment server - midnight start",
			currentTime:       "2025-07-02T15:00:00",
			serverName:        "linuxp004.example.com",
			expectedStartTime: "2025-07-29T02:00:00", // StartTime: "00:00"
			expectedEndTime:   "2025-07-29T04:00:00", // EndTime: "02:00"
			description:       "Should handle midnight start for P environment server",
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			// Setup
			mockClient := &MockPatchnightClient{}
			processor := NewServiceProcessor(mockClient, false)

			// Set current time for this test case (local time)
			currentTime := parseTimeLocal(tc.currentTime)
			processor.SetCurrentTime(func() time.Time { return currentTime })

			err := processor.ProcessPatchnightData()
			if err != nil {
				t.Fatalf("ProcessPatchnightData() returned error: %v", err)
			}
			result, err := processor.GetPatchnightData()
			if err != nil {
				t.Fatalf("GetPatchnightData() returned error: %v", err)
			}

			// Find the specific server
			var targetServer *mcmp.Server
			for i := range result.Servers {
				if result.Servers[i].Name == tc.serverName {
					targetServer = &result.Servers[i]
					break
				}
			}

			if targetServer == nil {
				t.Fatalf("Server %s not found in result", tc.serverName)
			}

			// Verify server is included
			if !targetServer.Include {
				t.Fatalf("Server %s should be included", tc.serverName)
			}

			// Verify start date is set
			if targetServer.StartDate == nil {
				t.Errorf("Server %s should have StartDate set", tc.serverName)
				return
			}

			// Verify end date is set
			if targetServer.EndDate == nil {
				t.Errorf("Server %s should have EndDate set", tc.serverName)
				return
			}

			// Parse expected times for comparison (simple local time)
			expectedStartTime := parseTimeLocal(tc.expectedStartTime)
			expectedEndTime := parseTimeLocal(tc.expectedEndTime)

			// Compare times by formatting them to avoid timezone comparison issues
			actualStartFormatted := targetServer.StartDate.Format("2006-01-02T15:04:05")
			expectedStartFormatted := expectedStartTime.Format("2006-01-02T15:04:05")

			actualEndFormatted := targetServer.EndDate.Format("2006-01-02T15:04:05")
			expectedEndFormatted := expectedEndTime.Format("2006-01-02T15:04:05")

			// Verify start timestamp
			if actualStartFormatted != expectedStartFormatted {
				t.Errorf("Start timestamp mismatch for server %s:\nExpected: %s\nActual: %s",
					tc.serverName, expectedStartFormatted, actualStartFormatted)
			}

			// Verify end timestamp
			if actualEndFormatted != expectedEndFormatted {
				t.Errorf("End timestamp mismatch for server %s:\nExpected: %s\nActual: %s",
					tc.serverName, expectedEndFormatted, actualEndFormatted)
			}

			// Verify end time is after start time
			if !targetServer.EndDate.After(*targetServer.StartDate) {
				t.Errorf("End timestamp should be after start timestamp for server %s:\nStart: %v\nEnd: %v",
					tc.serverName, *targetServer.StartDate, *targetServer.EndDate)
			}

			// Log success for debugging
			t.Logf("Server %s timestamps verified successfully:", tc.serverName)
			t.Logf("  Start: %v", *targetServer.StartDate)
			t.Logf("  End: %v", *targetServer.EndDate)
		})
	}
}

// Additional test for excluded servers (should not have timestamps)
func TestServiceProcessor_ExcludedServersNoTimestamps(t *testing.T) {
	// Setup
	mockClient := &MockPatchnightClient{}
	processor := NewServiceProcessor(mockClient, false)

	// Set current time
	currentTime := parseTimeBerlin("2025-07-02T15:00:00")
	processor.SetCurrentTime(func() time.Time { return currentTime })

	err := processor.ProcessPatchnightData()
	if err != nil {
		t.Fatalf("ProcessPatchnightData() returned error: %v", err)
	}
	result, err := processor.GetPatchnightData()
	if err != nil {
		t.Fatalf("GetPatchnightData() returned error: %v", err)
	}

	// Test excluded servers
	excludedServers := []string{
		"linuxc001.example.com",
		"linuxk005.example.com",
		"linuxp006.example.com",
	}

	for _, serverName := range excludedServers {
		var targetServer *mcmp.Server
		for i := range result.Servers {
			if result.Servers[i].Name == serverName {
				targetServer = &result.Servers[i]
				break
			}
		}

		if targetServer == nil {
			t.Fatalf("Excluded server %s not found in result", serverName)
		}

		// Verify server is excluded
		if targetServer.Include {
			t.Errorf("Server %s should be excluded (Include=false)", serverName)
		}

		// Verify no timestamps for excluded servers
		if targetServer.StartDate != nil {
			t.Errorf("Excluded server %s should not have StartDate set", serverName)
		}

		if targetServer.EndDate != nil {
			t.Errorf("Excluded server %s should not have EndDate set", serverName)
		}

		// Verify no environment for excluded servers
		if targetServer.Environment != nil {
			t.Errorf("Excluded server %s should not have Env set", serverName)
		}

		t.Logf("Excluded server %s verified successfully - no timestamps", serverName)
	}
}

// Testvalidierung mit besserer Fehlerbehandlung
func TestValidateServerData(t *testing.T) {
	tests := []struct {
		name    string
		server  mcmp.Server
		wantErr bool
	}{
		{
			name: "valid server",
			server: mcmp.Server{
				Name:    "test.example.com",
				Include: true,
			},
			wantErr: false,
		},
		{
			name: "invalid server - empty name",
			server: mcmp.Server{
				Name:    "",
				Include: true,
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.server.Validate()
			if (err != nil) != tt.wantErr {
				t.Errorf("Server.Validate() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}
