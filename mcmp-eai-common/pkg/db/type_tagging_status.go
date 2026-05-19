package db

import "database/sql/driver"

// TaggingStatus represents the status of a tagging process, defined as a string value.
type TaggingStatus string

const (
	TaggingStatusNew                          TaggingStatus = "new"
	TaggingStatusSkipped                      TaggingStatus = "skipped"
	TaggingStatusWaiting                      TaggingStatus = "waiting"
	TaggingStatusSuccessful                   TaggingStatus = "successful"
	TaggingStatusFailed                       TaggingStatus = "failed"
	TaggingStatusError                        TaggingStatus = "error"
	TaggingStatusCanceled                     TaggingStatus = "canceled"
	TaggingStatusWaitingForIncidentResolution TaggingStatus = "waiting_for_incident_resolution"
	TaggingStatusIncidentFailed               TaggingStatus = "incident_failed"
)

// Scan converts a database value into a TaggingStatus. It handles nil, string, and []byte types, returning an error otherwise.
func (s *TaggingStatus) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s TaggingStatus) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s TaggingStatus) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the TaggingStatus type as "tagging_status".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (TaggingStatus) GormDataType() string {
	return "tagging_status"
}
