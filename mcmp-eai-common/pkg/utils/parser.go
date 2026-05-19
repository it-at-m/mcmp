package utils

import (
	"fmt"
	"strconv"
	"time"
)

var location, _ = time.LoadLocation("Europe/Berlin")

func ParseUint(str string) (uint, error) {
	u64, err := strconv.ParseUint(str, 10, 0)
	if err != nil {
		return 0, fmt.Errorf("parse uint %q: %w", str, err)
	}
	return uint(u64), nil
}

func ParseUcsTime(str string) (*time.Time, error) {
	var err error
	var t time.Time
	if len(str) == 23 {
		t, err = time.ParseInLocation("2006-01-02T15:04:05.000", str, location)
	} else if len(str) == 25 {
		t, err = time.ParseInLocation("2006-01-02T15:04:05-07:00", str, location)
		if err == nil {
			t = t.UTC()
		}
	} else {
		t, err = time.ParseInLocation("2006-01-02T15:04:05", str, location)
	}
	if err != nil {
		return nil, err
	}
	return &t, nil
}

func ParseDate(date string) *time.Time {
	if len(date) > 0 {
		t, err := time.ParseInLocation("2006-01-02", date, location)
		if err == nil {
			return &t
		}
	}
	return nil
}
