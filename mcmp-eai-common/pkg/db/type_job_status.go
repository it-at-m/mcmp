package db

import (
	"database/sql/driver"
	"fmt"
)

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
)

func (s *JobStatus) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = JobStatus(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into JobStatus", value)
}

func (s *JobStatus) Value() (driver.Value, error) {
	return string(*s), nil
}

func (s *JobStatus) String() string {
	return string(*s)
}
