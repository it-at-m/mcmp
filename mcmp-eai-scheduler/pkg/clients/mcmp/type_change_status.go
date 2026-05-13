package mcmp

import (
	"database/sql/driver"
	"fmt"
)

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
)

func (s *ChangeStatus) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = ChangeStatus(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into ChangeStatus", value)
}

func (s ChangeStatus) Value() (driver.Value, error) {
	return string(s), nil
}

func (s ChangeStatus) String() string {
	return string(s)
}
