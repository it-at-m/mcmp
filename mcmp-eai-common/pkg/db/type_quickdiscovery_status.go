package db

import "database/sql/driver"

// QuickdiscoveryStatus represents the status of a quick discovery process, defined as a string value.
type QuickdiscoveryStatus string

const (
	QuickdiscoveryStatusNew                               QuickdiscoveryStatus = "new"
	QuickdiscoveryStatusSkipped                           QuickdiscoveryStatus = "skipped"
	QuickdiscoveryStatusWaiting                           QuickdiscoveryStatus = "waiting"
	QuickdiscoveryStatusSuccessful                        QuickdiscoveryStatus = "successful"
	QuickdiscoveryStatusFailed                            QuickdiscoveryStatus = "failed"
	QuickdiscoveryStatusError                             QuickdiscoveryStatus = "error"
	QuickdiscoveryStatusCanceled                          QuickdiscoveryStatus = "canceled"
	QuickdiscoveryStatusWaitingForServiceNowEnablement    QuickdiscoveryStatus = "waiting_for_service_now_enablement"
	QuickdiscoveryStatusWaitingForServiceNowConfiguration QuickdiscoveryStatus = "waiting_for_service_now_configuration"
	QuickdiscoveryStatusWaitingForIncidentResolution      QuickdiscoveryStatus = "waiting_for_incident_resolution"
	QuickdiscoveryStatusIncidentFailed                    QuickdiscoveryStatus = "incident_failed"
)

// Scan converts a database value into a QuickdiscoveryStatus. It handles nil, string, and []byte types, returning an error otherwise.
func (s *QuickdiscoveryStatus) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s QuickdiscoveryStatus) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s QuickdiscoveryStatus) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the QuickdiscoveryStatus type as "quickdiscovery_status".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (QuickdiscoveryStatus) GormDataType() string {
	return "quickdiscovery_status"
}
