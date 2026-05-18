package utils

import (
	"reflect"
	"testing"
	"time"
)

func Test_ParseUint(t *testing.T) {
	type args struct {
		s string
	}
	type want struct {
		i   uint
		err bool
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{""}, want{0, true}},
		{"2", args{"-1"}, want{0, true}},
		{"3", args{"0"}, want{0, false}},
		{"4", args{"42"}, want{42, false}},
		{"5", args{"12345678"}, want{12345678, false}},
		{"6", args{"18446744073709551615"}, want{18446744073709551615, false}},
		{"7", args{"18446744073709551616"}, want{0, true}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			i, err := ParseUint(tt.args.s)
			if !reflect.DeepEqual(i, tt.want.i) || (err != nil) != tt.want.err {
				t.Errorf("ParseUint(\"%v\") = (%v, %v), want (%v, %v), error = %v", tt.args.s, i, err != nil, tt.want.i, tt.want.err, err)
			}
		})
	}
}

func Test_ParseUcsTime(t *testing.T) {
	type args struct {
		ut string
	}
	type want struct {
		ut  *time.Time
		err bool
	}
	location, _ = time.LoadLocation("Europe/Berlin")
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{"2022-11-09T16:00:29.721"}, want{new(time.Date(2022, 11, 9, 16, 0, 29, 721000000, location)), false}},
		{"2", args{"2022-11-09T16:00:29"}, want{new(time.Date(2022, 11, 9, 16, 0, 29, 0, location)), false}},
		{"3", args{""}, want{nil, true}},
		{"4", args{"2024-08-20T13:04:35+00:00"}, want{new(time.Date(2024, 8, 20, 15, 4, 35, 0, location)), false}}, // M7 UTC Time
		{"5", args{"2024-08-20T13:04:35-00:00"}, want{new(time.Date(2024, 8, 20, 15, 4, 35, 0, location)), false}},
		{"6", args{"2024-08-20T13:04:35+02:00"}, want{new(time.Date(2024, 8, 20, 13, 4, 35, 0, location)), false}},
		{"7", args{"2024-08-20T13:04:35-02:00"}, want{new(time.Date(2024, 8, 20, 17, 4, 35, 0, location)), false}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ut, err := ParseUcsTime(tt.args.ut)
			if ut != nil && tt.want.ut != nil && !ut.Equal(*tt.want.ut) || (err != nil) != tt.want.err {
				t.Errorf("ParseUcsTime(\"%v\") = (%v, %v), want (%v, %v), error = %v", tt.args.ut, ut, err != nil, tt.want.ut, tt.want.err, err)
			}
		})
	}
}

func Test_ParseDate(t *testing.T) {
	type args struct {
		ut string
	}
	type want struct {
		ut *time.Time
	}
	location, _ = time.LoadLocation("Europe/Berlin")
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{"2022-11-09"}, want{new(time.Date(2022, 11, 9, 0, 0, 0, 0, location))}},
		{"2", args{"2023-01-15"}, want{new(time.Date(2023, 1, 15, 0, 0, 0, 0, location))}},
		{"3", args{""}, want{nil}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ut := ParseDate(tt.args.ut)
			if ut != nil && tt.want.ut != nil && *ut != *tt.want.ut {
				t.Errorf("ParseDate(\"%v\") = (%v), want (%v)", tt.args.ut, ut, tt.want.ut)
			}
		})
	}
}
