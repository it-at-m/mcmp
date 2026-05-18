package db

import (
	"database/sql/driver"
	"fmt"
)

// IncidentStatus represents the status of a ServiceNow incident, defined as a string value.
type IncidentStatus string

const (
	IncidentStatusOpen     IncidentStatus = "open"
	IncidentStatusResolved IncidentStatus = "resolved"
	IncidentStatusFailed   IncidentStatus = "failed"
)

// Scan implements the sql.Scanner interface to convert a database value into an IncidentStatus type.
// It supports nil values and string types.
// Returns an error if the value cannot be converted to IncidentStatus.
func (s *IncidentStatus) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = IncidentStatus(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into IncidentStatus", value)
}

// Value converts an IncidentStatus instance to a driver.Value, returning the string representation and a nil error.
func (s *IncidentStatus) Value() (driver.Value, error) {
	return string(*s), nil
}

// String returns the IncidentStatus value as a string.
func (s *IncidentStatus) String() string {
	return string(*s)
}
