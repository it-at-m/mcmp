package mcmp

import (
	"database/sql/driver"
	"fmt"
)

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
)

// Scan implements the sql.Scanner interface to convert a database value into a ChangeStatus type.
// It supports nil values and string types.
// Returns an error if the value cannot be converted to ChangeStatus.
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

// Value converts a ChangeStatus instance to a driver.Value, returning the string representation and a nil error.
func (s ChangeStatus) Value() (driver.Value, error) {
	return string(s), nil
}

// String returns the ChangeStatus value as a string.
func (s ChangeStatus) String() string {
	return string(s)
}
