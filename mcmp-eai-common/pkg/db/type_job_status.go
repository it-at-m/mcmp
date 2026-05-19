package db

import "database/sql/driver"

// JobStatus represents the status of a job, defined as a string value.
type JobStatus string

const (
	JobStatusNew                               JobStatus = "new"
	JobStatusWaitingForApproval                JobStatus = "waiting_for_approval"
	JobStatusApproved                          JobStatus = "approved"
	JobStatusAwxRunning                        JobStatus = "awx_running"
	JobStatusAwxCompleted                      JobStatus = "awx_completed"
	JobStatusWaitingForQuickdiscovery          JobStatus = "waiting_for_quickdiscovery"
	JobStatusQuickdiscoveryCompleted           JobStatus = "quickdiscovery_completed"
	JobStatusWaitingForTagging                 JobStatus = "waiting_for_tagging"
	JobStatusTaggingCompleted                  JobStatus = "tagging_completed"
	JobStatusSuccessful                        JobStatus = "successful"
	JobStatusFailed                            JobStatus = "failed"
	JobStatusError                             JobStatus = "error"
	JobStatusCanceled                          JobStatus = "canceled"
	JobStatusRejected                          JobStatus = "rejected"
	JobStatusWaitingForAwxEnablement           JobStatus = "waiting_for_awx_enablement"
	JobStatusWaitingForAwxConfiguration        JobStatus = "waiting_for_awx_configuration"
	JobStatusWaitingForServiceNowEnablement    JobStatus = "waiting_for_service_now_enablement"
	JobStatusWaitingForServiceNowConfiguration JobStatus = "waiting_for_service_now_configuration"
	JobStatusQuickdiscoveryFailed              JobStatus = "quickdiscovery_failed"
	JobStatusTaggingFailed                     JobStatus = "tagging_failed"
	JobStatusWaitingForIncidentResolution      JobStatus = "waiting_for_incident_resolution"
	JobStatusIncidentFailed                    JobStatus = "incident_failed"
)

// Scan converts a database value into a JobStatus. It handles nil, string, and []byte types, returning an error otherwise.
func (s *JobStatus) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s JobStatus) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s JobStatus) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the JobStatus type as "job_status".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (JobStatus) GormDataType() string {
	return "job_status"
}
