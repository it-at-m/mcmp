package db

import "database/sql/driver"

// IncidentSourceType represents the status of a ServiceNow incident source type, defined as a string value.
type IncidentSourceType string

const (
	IncidentSourceTypeAwx            IncidentSourceType = "awx"
	IncidentSourceTypeChange         IncidentSourceType = "change"
	IncidentSourceTypeQuickdiscovery IncidentSourceType = "quickdiscovery"
	IncidentSourceTypeTagging        IncidentSourceType = "tagging"
)

// Scan converts a database value into an IncidentSourceType. It handles nil, string, and []byte types, returning an error otherwise.
func (s *IncidentSourceType) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s IncidentSourceType) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s IncidentSourceType) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the IncidentSourceType type as "incident_source_type".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (IncidentSourceType) GormDataType() string {
	return "incident_source_type"
}
