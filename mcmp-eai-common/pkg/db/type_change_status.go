package db

import "database/sql/driver"

// ChangeStatus represents the status of a change, defined as a string value.
type ChangeStatus string

const (
	ChangeStatusNew                               ChangeStatus = "new"
	ChangeStatusSkipped                           ChangeStatus = "skipped"
	ChangeStatusWaitingForApproval                ChangeStatus = "waiting_for_approval"
	ChangeStatusApproved                          ChangeStatus = "approved"
	ChangeStatusRejected                          ChangeStatus = "rejected"
	ChangeStatusFailed                            ChangeStatus = "failed"
	ChangeStatusCanceled                          ChangeStatus = "canceled"
	ChangeStatusWaitingForServiceNowEnablement    ChangeStatus = "waiting_for_service_now_enablement"
	ChangeStatusWaitingForServiceNowConfiguration ChangeStatus = "waiting_for_service_now_configuration"
	ChangeStatusWaitingForIncidentResolution      ChangeStatus = "waiting_for_incident_resolution"
	ChangeStatusIncidentFailed                    ChangeStatus = "incident_failed"
)

// Scan converts a database value into a ChangeStatus. It handles nil, string, and []byte types, returning an error otherwise.
func (s *ChangeStatus) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s ChangeStatus) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s ChangeStatus) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the ChangeStatus type as "change_status".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (ChangeStatus) GormDataType() string {
	return "change_status"
}
