package db

import (
	"database/sql/driver"
	"fmt"
)

// QuickdiscoveryStatus represents the status of a quick discovery process as a string type.
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

// Scan implements the sql.Scanner interface for QuickdiscoveryStatus to allow database scanning of its value.
// It assigns the string representation of the database value to the QuickdiscoveryStatus instance.
// Returns an error if the provided value cannot be converted to a string.
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

// Value implements the driver.Valuer interface for QuickdiscoveryStatus by returning its string representation.
func (s *QuickdiscoveryStatus) Value() (driver.Value, error) {
	return string(*s), nil
}

// String converts the QuickdiscoveryStatus to its string representation and returns it.
func (s *QuickdiscoveryStatus) String() string {
	return string(*s)
}
