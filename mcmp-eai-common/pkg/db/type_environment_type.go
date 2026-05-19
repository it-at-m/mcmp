package db

import "database/sql/driver"

// EnvironmentType represents the type of an environment, defined as a string value.
type EnvironmentType string

const (
	EnvironmentTypeC EnvironmentType = "C"
	EnvironmentTypeD EnvironmentType = "D"
	EnvironmentTypeK EnvironmentType = "K"
	EnvironmentTypeP EnvironmentType = "P"
	EnvironmentTypeS EnvironmentType = "S"
	EnvironmentTypeT EnvironmentType = "T"
)

// Scan converts a database value into an EnvironmentType. It handles nil, string, and []byte types, returning an error otherwise.
func (s *EnvironmentType) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s EnvironmentType) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s EnvironmentType) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the EnvironmentType type as "environment_type".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (EnvironmentType) GormDataType() string {
	return "environment_type"
}
