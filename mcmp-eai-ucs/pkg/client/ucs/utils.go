package ucs

import (
	"fmt"
	"strconv"
	"strings"
	"time"
)

var location *time.Location

func init() {
	loc, err := time.LoadLocation("Europe/Berlin")
	if err != nil {
		location = time.UTC
		return
	}
	location = loc
}

func ParseUint(str string) (uint, error) {
	u64, err := strconv.ParseUint(str, 10, 0)
	if err != nil {
		return 0, fmt.Errorf("parse uint %q: %w", str, err)
	}
	return uint(u64), nil
}

func ParseUcsTime(str string) (*time.Time, error) {
	var (
		err error
		t   time.Time
	)

	switch len(str) {
	case 23:
		t, err = time.ParseInLocation("2006-01-02T15:04:05.000", str, location)
	case 25:
		t, err = time.ParseInLocation("2006-01-02T15:04:05-07:00", str, location)
		if err == nil {
			t = t.UTC()
		}
	default:
		t, err = time.ParseInLocation("2006-01-02T15:04:05", str, location)
	}

	if err != nil {
		return nil, fmt.Errorf("parse UCS time %q: %w", str, err)
	}
	return &t, nil
}

func ParseDate(date string) *time.Time {
	if date != "" {
		t, err := time.ParseInLocation("2006-01-02", date, location)
		if err == nil {
			return &t
		}
	}
	return nil
}

func IsBladeServer(dn string) bool {
	return strings.Contains(dn, "chassis-") && strings.Contains(dn, "blade-")
}

func IsRackUnitServer(dn string) bool {
	return strings.Contains(dn, "rack-unit-")
}
