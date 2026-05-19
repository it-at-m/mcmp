package db

import "database/sql/driver"

// IncidentStatus represents the status of a ServiceNow incident, defined as a string value.
type IncidentStatus string

const (
	IncidentStatusOpen     IncidentStatus = "open"
	IncidentStatusResolved IncidentStatus = "resolved"
	IncidentStatusFailed   IncidentStatus = "failed"
)

// Scan converts a database value into an IncidentStatus. It handles nil, string, and []byte types, returning an error otherwise.
func (s *IncidentStatus) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s IncidentStatus) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s IncidentStatus) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the IncidentStatus type as "incident_status".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (IncidentStatus) GormDataType() string {
	return "incident_status"
}
