package baas

import (
	"encoding/json"
	"testing"
	"time"
)

func TestSaveTime_UnmarshalJSON(t *testing.T) {
	berlin, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		t.Fatalf("konnte Zeitzone Europe/Berlin nicht laden: %v", err)
	}
	tests := []struct {
		name           string
		jsonInput      string
		expectedString string
		expectedTime   time.Time
		expectError    bool
	}{
		{
			name:           "Gültiger Zeitstempel",
			jsonInput:      `"2024-04-02 12:34:56"`,
			expectedString: "2024-04-02 12:34:56",
			expectedTime:   time.Date(2024, 4, 2, 12, 34, 56, 0, berlin),
			expectError:    false,
		},
		{
			name:        "Ungültiger Zeitstempel (falsches Format)",
			jsonInput:   `"02-04-2024 12:34:56"`,
			expectError: true,
		},
		{
			name:        "Leerer String",
			jsonInput:   `""`,
			expectError: true,
		},
		{
			name:        "Nicht-String Daten (Zahl)",
			jsonInput:   `123456789`,
			expectError: true,
		},
		{
			name:        "Nicht-String Daten (boolean)",
			jsonInput:   `true`,
			expectError: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var saveTime SaveTime
			err := json.Unmarshal([]byte(tt.jsonInput), &saveTime)

			if tt.expectError {
				if err == nil {
					t.Errorf("Erwartete Fehler, jedoch keinen Fehler erhalten. JSON-Input: %s", tt.jsonInput)
				} else {
					t.Logf("Erwarteter Fehler erhalten: %v", err)
				}
				return
			}

			if err != nil {
				t.Errorf("Unerwarteter Fehler beim JSON-Unmarshal: %v", err)
				return
			}

			if saveTime.StringValue != tt.expectedString {
				t.Errorf("SaveTime.StringValue falsch. Erwartet: '%s', erhalten: '%s'", tt.expectedString, saveTime.StringValue)
			}

			if !saveTime.TimeValue.Equal(tt.expectedTime) {
				t.Errorf("SaveTime.TimeValue falsch. Erwartet: %v, erhalten: %v", tt.expectedTime, saveTime.TimeValue)
			}
		})
	}
}
