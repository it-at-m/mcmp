package db

import (
	"database/sql/driver"
	"errors"
	"fmt"
)

// ErrNilDestination is returned when the destination pointer is nil.
var ErrNilDestination = errors.New("destination pointer is nil")

// ScanString attempts to scan a value into a string-like type T, handling strings, byte slices, and nil values.
func ScanString[T ~string](ptr *T, value interface{}) error {
	if ptr == nil {
		return ErrNilDestination
	}
	if value == nil {
		*ptr = ""
		return nil
	}
	switch v := value.(type) {
	case string:
		*ptr = T(v)
		return nil
	case []byte:
		*ptr = T(v)
		return nil
	case int64:
		*ptr = T(fmt.Sprintf("%d", v))
		return nil
	case fmt.Stringer:
		*ptr = T(v.String())
		return nil
	}
	return fmt.Errorf("cannot scan %T into %T", value, ptr)
}

// ValueString converts a custom string-like type to a driver.Value for database compatibility.
func ValueString[T ~string](v T) (driver.Value, error) {
	return string(v), nil
}
