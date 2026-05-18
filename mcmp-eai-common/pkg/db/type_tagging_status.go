package db

import (
	"database/sql/driver"
	"fmt"
)

type TaggingStatus string

const (
	TaggingStatusNew        TaggingStatus = "new"
	TaggingStatusSkipped    TaggingStatus = "skipped"
	TaggingStatusWaiting    TaggingStatus = "waiting"
	TaggingStatusSuccessful TaggingStatus = "successful"
	TaggingStatusFailed     TaggingStatus = "failed"
	TaggingStatusError      TaggingStatus = "error"
	TaggingStatusCanceled   TaggingStatus = "canceled"
)

func (s *TaggingStatus) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = TaggingStatus(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into TaggingStatus", value)
}

func (s *TaggingStatus) Value() (driver.Value, error) {
	return string(*s), nil
}

func (s *TaggingStatus) String() string {
	return string(*s)
}
