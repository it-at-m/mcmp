package db

import (
	"database/sql/driver"
	"fmt"
)

// ScanString attempts to scan a value into a string-like type T, handling strings, byte slices, and nil values.
func ScanString[T ~string](ptr *T, value interface{}) error {
	if value == nil {
		*ptr = ""
		return nil
	}
	if str, ok := value.(string); ok {
		*ptr = T(str)
		return nil
	}
	if b, ok := value.([]byte); ok {
		*ptr = T(b)
		return nil
	}
	return fmt.Errorf("cannot scan %T into %T", value, ptr)
}

// ValueString converts a custom string-like type to a driver.Value for database compatibility.
func ValueString[T ~string](v T) (driver.Value, error) {
	return string(v), nil
}
