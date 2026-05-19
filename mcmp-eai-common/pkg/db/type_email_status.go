package db

import "database/sql/driver"

// EmailStatus represents the status of an email, defined as a string value.
type EmailStatus string

const (
	EmailStatusNew     EmailStatus = "new"
	EmailStatusSent    EmailStatus = "sent"
	EmailStatusSkipped EmailStatus = "skipped"
	EmailStatusFailed  EmailStatus = "failed"
)

// Scan converts a database value into an EmailStatus. It handles nil, string, and []byte types, returning an error otherwise.
func (s *EmailStatus) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s EmailStatus) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s EmailStatus) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the EmailStatus type as "email_status".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (EmailStatus) GormDataType() string {
	return "email_status"
}
