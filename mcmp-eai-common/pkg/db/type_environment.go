package db

import (
	"database/sql/driver"
	"fmt"
)

type EnvironmentType string

const (
	EnvironmentTypeC  EnvironmentType = "C"
	EnvironmentTypeD  EnvironmentType = "D"
	EnvironmentTypeK  EnvironmentType = "K"
	EnvironmentTypeP  EnvironmentType = "P"
	EnvironmentTypeS  EnvironmentType = "S"
	EnvironmentTypeTL EnvironmentType = "T"
)

func (s *EnvironmentType) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = EnvironmentType(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into EnvironmentType", value)
}

func (s *EnvironmentType) Value() (driver.Value, error) {
	return string(*s), nil
}

func (s *EnvironmentType) String() string {
	return string(*s)
}
