package mcmp

import (
	"database/sql/driver"
	"fmt"
)

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
)

func (s *QuickdiscoveryStatus) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = QuickdiscoveryStatus(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into QuickdiscoveryStatus", value)
}

func (s QuickdiscoveryStatus) Value() (driver.Value, error) {
	return string(s), nil
}

func (s QuickdiscoveryStatus) String() string {
	return string(s)
}
