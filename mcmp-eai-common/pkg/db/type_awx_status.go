package db

import "database/sql/driver"

// AwxStatus represents the status of an AWX job, defined as a string value.
type AwxStatus string

const (
	AwxStatusNew                          AwxStatus = "new"
	AwxStatusSkipped                      AwxStatus = "skipped"
	AwxStatusRunning                      AwxStatus = "running"
	AwxStatusSuccessful                   AwxStatus = "successful"
	AwxStatusFailed                       AwxStatus = "failed"
	AwxStatusError                        AwxStatus = "error"
	AwxStatusCanceled                     AwxStatus = "canceled"
	AwxStatusWaitingForAwxEnablement      AwxStatus = "waiting_for_awx_enablement"
	AwxStatusWaitingForAwxConfiguration   AwxStatus = "waiting_for_awx_configuration"
	AwxStatusWaitingForIncidentResolution AwxStatus = "waiting_for_incident_resolution"
	AwxStatusIncidentSuccessful           AwxStatus = "incident_successful"
	AwxStatusIncidentFailed               AwxStatus = "incident_failed"
	AwxStatusLogicalFailed                AwxStatus = "logical_failed"
)

// Scan converts a database value into an AwxStatus. It handles nil, string, and []byte types, returning an error otherwise.
func (s *AwxStatus) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s AwxStatus) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s AwxStatus) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the AwxStatus type as "awx_status".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (AwxStatus) GormDataType() string {
	return "awx_status"
}
