package ucs

import (
	"reflect"
	"testing"
)

func Test_GetServerDN(t *testing.T) {
	type args struct {
		dn string
	}
	type want struct {
		dn       string
		contains bool
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{"sys/rack-unit-5/board/mini-storage-SD-2"}, want{"sys/rack-unit-5", true}},
		{"2", args{"sys/chassis-11/blade-5/board/mini-storage-SD-1"}, want{"sys/chassis-11/blade-5", true}},
		{"3", args{"sys/chassis-3/blade-8/board/storage-SAS-1/onboard-device-sbr-1"}, want{"sys/chassis-3/blade-8", true}},
		{"4", args{"sys/chassis-7/blade-8"}, want{"sys/chassis-7/blade-8", true}},
		{"5", args{"sys/rack-unit-26"}, want{"sys/rack-unit-26", true}},
		{"6", args{"sys/fex-8"}, want{"", false}},
		{"7", args{"sys/chassis-3/blade-6/locator-led"}, want{"sys/chassis-3/blade-6", true}},
		{"8", args{"sys/chassis-1/blade-5/board/memarray-1/mem-21/error-stats"}, want{"sys/chassis-1/blade-5", true}},
		{"9", args{""}, want{"", false}},
		{"10", args{"sys/chassis-1"}, want{"", false}},
		{"11", args{"sys/switch-A"}, want{"", false}},
		{"12", args{"sys/chassis-1/blade-1/board/memarray-1/mem-23"}, want{"sys/chassis-1/blade-1", true}},
		{"13", args{"sys/chassis-1/psu-3"}, want{"", false}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dn, contains := GetServerDN(tt.args.dn)
			if !reflect.DeepEqual(dn, tt.want.dn) || contains != tt.want.contains {
				t.Errorf("GetServerDN(\"%v\") = result (\"%v\", %v), want (\"%v\", %v)", tt.args.dn, dn, contains, tt.want.dn, tt.want.contains)
			}
		})
	}
}

func Test_GetChassisDN(t *testing.T) {
	type args struct {
		dn string
	}
	type want struct {
		dn       string
		contains bool
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{"sys/rack-unit-5/board/mini-storage-SD-2"}, want{"", false}},
		{"2", args{"sys/chassis-11/blade-5/board/mini-storage-SD-1"}, want{"sys/chassis-11", true}},
		{"3", args{"sys/chassis-3/blade-8/board/storage-SAS-1/onboard-device-sbr-1"}, want{"sys/chassis-3", true}},
		{"4", args{"sys/chassis-7/blade-8"}, want{"sys/chassis-7", true}},
		{"5", args{"sys/rack-unit-26"}, want{"", false}},
		{"6", args{"sys/fex-8"}, want{"", false}},
		{"7", args{"sys/chassis-3/blade-6/locator-led"}, want{"sys/chassis-3", true}},
		{"8", args{"sys/chassis-1/blade-5/board/memarray-1/mem-21/error-stats"}, want{"sys/chassis-1", true}},
		{"9", args{""}, want{"", false}},
		{"10", args{"sys/chassis-1"}, want{"sys/chassis-1", true}},
		{"11", args{"sys/switch-A"}, want{"", false}},
		{"12", args{"sys/chassis-1/blade-1/board/memarray-1/mem-23"}, want{"sys/chassis-1", true}},
		{"13", args{"sys/chassis-1/psu-3"}, want{"sys/chassis-1", true}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dn, contains := GetChassisDN(tt.args.dn)
			if !reflect.DeepEqual(dn, tt.want.dn) || contains != tt.want.contains {
				t.Errorf("GetChassisDN(\"%v\") = result (\"%v\", %v), want (\"%v\", %v)", tt.args.dn, dn, contains, tt.want.dn, tt.want.contains)
			}
		})
	}
}

func Test_GetFexDN(t *testing.T) {
	type args struct {
		dn string
	}
	type want struct {
		dn       string
		contains bool
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{"sys/rack-unit-5/board/mini-storage-SD-2"}, want{"", false}},
		{"2", args{"sys/chassis-11/blade-5/board/mini-storage-SD-1"}, want{"", false}},
		{"3", args{"sys/chassis-3/blade-8/board/storage-SAS-1/onboard-device-sbr-1"}, want{"", false}},
		{"4", args{"sys/chassis-7/blade-8"}, want{"", false}},
		{"5", args{"sys/rack-unit-26"}, want{"", false}},
		{"6", args{"sys/fex-8"}, want{"sys/fex-8", true}},
		{"7", args{"sys/chassis-3/blade-6/locator-led"}, want{"", false}},
		{"8", args{"sys/chassis-1/blade-5/board/memarray-1/mem-21/error-stats"}, want{"", false}},
		{"9", args{""}, want{"", false}},
		{"10", args{"sys/chassis-1"}, want{"", false}},
		{"11", args{"sys/switch-A"}, want{"", false}},
		{"12", args{"sys/chassis-1/blade-1/board/memarray-1/mem-23"}, want{"", false}},
		{"13", args{"sys/chassis-1/psu-3"}, want{"", false}},
		{"14", args{"sys/fex-2/psu-2"}, want{"sys/fex-2", true}},
		{"15", args{"sys/fex-7/slot-1/host/port-32"}, want{"sys/fex-7", true}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dn, contains := GetFexDN(tt.args.dn)
			if !reflect.DeepEqual(dn, tt.want.dn) || contains != tt.want.contains {
				t.Errorf("GetFexDN(\"%v\") = result (\"%v\", %v), want (\"%v\", %v)", tt.args.dn, dn, contains, tt.want.dn, tt.want.contains)
			}
		})
	}
}

func Test_GetFiDN(t *testing.T) {
	type args struct {
		dn string
	}
	type want struct {
		dn       string
		contains bool
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{"sys/rack-unit-5/board/mini-storage-SD-2"}, want{"", false}},
		{"2", args{"sys/chassis-11/blade-5/board/mini-storage-SD-1"}, want{"", false}},
		{"3", args{"sys/chassis-3/blade-8/board/storage-SAS-1/onboard-device-sbr-1"}, want{"", false}},
		{"4", args{"sys/chassis-7/blade-8"}, want{"", false}},
		{"5", args{"sys/rack-unit-26"}, want{"", false}},
		{"6", args{"sys/fex-8"}, want{"", false}},
		{"7", args{"sys/chassis-3/blade-6/locator-led"}, want{"", false}},
		{"8", args{"sys/chassis-1/blade-5/board/memarray-1/mem-21/error-stats"}, want{"", false}},
		{"9", args{""}, want{"", false}},
		{"10", args{"sys/chassis-1"}, want{"", false}},
		{"11", args{"sys/switch-A"}, want{"sys/switch-A", true}},
		{"12", args{"sys/chassis-1/blade-1/board/memarray-1/mem-23"}, want{"", false}},
		{"13", args{"sys/chassis-1/psu-3"}, want{"", false}},
		{"14", args{"sys/fex-2/psu-2"}, want{"", false}},
		{"15", args{"sys/switch-B/psu-1"}, want{"sys/switch-B", true}},
		{"16", args{"sys/switch-A/slot-1/switch-ether/port-19"}, want{"sys/switch-A", true}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dn, contains := GetFiDN(tt.args.dn)
			if !reflect.DeepEqual(dn, tt.want.dn) || contains != tt.want.contains {
				t.Errorf("GetFiDN(\"%v\") = result (\"%v\", %v), want (\"%v\", %v)", tt.args.dn, dn, contains, tt.want.dn, tt.want.contains)
			}
		})
	}
}

func Test_GetHealthDN(t *testing.T) {
	type args struct {
		dn string
	}
	type want struct {
		dn       string
		contains bool
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{"sys/rack-unit-5/board/mini-storage-SD-2"}, want{"", false}},
		{"2", args{"sys/chassis-11/blade-5/board/mini-storage-SD-1"}, want{"", false}},
		{"3", args{"sys/chassis-3/blade-8/board/storage-SAS-1/onboard-device-sbr-1"}, want{"", false}},
		{"4", args{"sys/chassis-7/blade-8"}, want{"", false}},
		{"5", args{"sys/rack-unit-26"}, want{"", false}},
		{"6", args{"sys/fex-8"}, want{"", false}},
		{"7", args{"sys/chassis-3/blade-6/locator-led"}, want{"", false}},
		{"8", args{"sys/chassis-1/blade-5/board/memarray-1/mem-21/error-stats"}, want{"", false}},
		{"9", args{""}, want{"", false}},
		{"10", args{"sys/chassis-1"}, want{"", false}},
		{"11", args{"sys/switch-A"}, want{"", false}},
		{"12", args{"sys/chassis-1/blade-1/board/memarray-1/mem-23"}, want{"", false}},
		{"13", args{"sys/chassis-1/psu-3"}, want{"", false}},
		{"14", args{"sys/fex-2/psu-2"}, want{"", false}},
		{"15", args{"sys/switch-B/psu-1"}, want{"", false}},
		{"16", args{"sys/switch-A/slot-1/switch-ether/port-19"}, want{"", false}},
		{"17", args{"sys/chassis-1/blade-1/mgmt/health/fault-F1705"}, want{"sys/chassis-1/blade-1/mgmt/health", true}},
		{"18", args{"sys/chassis-12/blade-6/mgmt/health/fault-F1706"}, want{"sys/chassis-12/blade-6/mgmt/health", true}},
		{"19", args{"sys/rack-unit-3/mgmt/health/fault-F1705"}, want{"sys/rack-unit-3/mgmt/health", true}},
		{"20", args{"sys/rack-unit-45/mgmt/health/fault-F1706"}, want{"sys/rack-unit-45/mgmt/health", true}},
		{"21", args{"sys/rack-unit-47/mgmt/health/RAS Event (2C)"}, want{"sys/rack-unit-47/mgmt/health", true}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dn, contains := GetHealthDN(tt.args.dn)
			if !reflect.DeepEqual(dn, tt.want.dn) || contains != tt.want.contains {
				t.Errorf("GetHealthDN(\"%v\") = result (\"%v\", %v), want (\"%v\", %v)", tt.args.dn, dn, contains, tt.want.dn, tt.want.contains)
			}
		})
	}
}
