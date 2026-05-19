package db

import "database/sql/driver"

// AwxTemplateType represents the type of an AWX template, defined as a string value.
type AwxTemplateType string

const (
	AwxTemplateTypeTemplate AwxTemplateType = "template"
	AwxTemplateTypeWorkflow AwxTemplateType = "workflow"
)

// Scan converts a database value into an AwxTemplateType. It handles nil, string, and []byte types, returning an error otherwise.
func (s *AwxTemplateType) Scan(value interface{}) error {
	return ScanString(s, value)
}

// Value returns the database value for the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s AwxTemplateType) Value() (driver.Value, error) {
	return ValueString(s)
}

// String returns the string representation of the status.
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (s AwxTemplateType) String() string {
	return string(s)
}

// GormDataType specifies the custom Gorm data type for the AwxTemplateType type as "awx_template_type".
// Mixed receivers are intentional here: Scan needs a pointer, Value/String are better as values for string types.
// noinspection GoMixedReceiverTypes
func (AwxTemplateType) GormDataType() string {
	return "awx_template_type"
}
