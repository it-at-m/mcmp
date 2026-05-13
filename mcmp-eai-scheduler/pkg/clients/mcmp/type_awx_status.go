package mcmp

import (
	"database/sql/driver"
	"fmt"
)

type AwxStatus string

const (
	AwxStatusNew                        AwxStatus = "new"
	AwxStatusSkipped                    AwxStatus = "skipped"
	AwxStatusRunning                    AwxStatus = "running"
	AwxStatusSuccessful                 AwxStatus = "successful"
	AwxStatusFailed                     AwxStatus = "failed"
	AwxStatusError                      AwxStatus = "error"
	AwxStatusCanceled                   AwxStatus = "canceled"
	AwxStatusWaitingForAwxEnablement    AwxStatus = "waiting_for_awx_enablement"
	AwxStatusWaitingForAwxConfiguration AwxStatus = "waiting_for_awx_configuration"
)

func (s *AwxStatus) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = AwxStatus(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into AwxStatus", value)
}

func (s AwxStatus) Value() (driver.Value, error) {
	return string(s), nil
}

func (s AwxStatus) String() string {
	return string(s)
}
