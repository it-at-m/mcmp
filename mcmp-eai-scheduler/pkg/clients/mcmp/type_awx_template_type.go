package mcmp

import (
	"database/sql/driver"
	"fmt"
)

type AwxTemplateType string

const (
	AwxTemplateTypeTemplate AwxTemplateType = "template"
	AwxTemplateTypeWorkflow AwxTemplateType = "workflow"
)

func (s *AwxTemplateType) Scan(value interface{}) error {
	if value == nil {
		*s = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*s = AwxTemplateType(str)
		return nil
	}
	return fmt.Errorf("cannot scan %T into AwxTemplateType", value)
}

func (s AwxTemplateType) Value() (driver.Value, error) {
	return string(s), nil
}

func (s AwxTemplateType) String() string {
	return string(s)
}
