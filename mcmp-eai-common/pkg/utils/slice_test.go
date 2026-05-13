package utils

import (
	"reflect"
	"testing"
)

func Test_Split(t *testing.T) {
	type args struct {
		slice []string
		size  int
	}
	type want struct {
		got [][]string
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{[]string{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P"}, 5}, want{[][]string{{"A", "B", "C", "D", "E"}, {"F", "G", "H", "I", "J"}, {"K", "L", "M", "N", "O"}, {"P"}}}},

		{"2", args{[]string{}, 3}, want{nil}},
		{"3", args{[]string{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P"}, 0}, want{nil}},
		{"4", args{[]string{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"}, 7}, want{[][]string{{"A", "B", "C", "D", "E", "F", "G"}, {"H", "I", "J", "K", "L", "M", "N"}}}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := Split(tt.args.slice, tt.args.size)
			if !reflect.DeepEqual(got, tt.want.got) {
				t.Errorf("Split(\"%v\", %d) = %v, want %v", tt.args.slice, tt.args.size, got, tt.want.got)
			}
		})
	}
}
