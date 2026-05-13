package vcenter

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func Test_getQuantile(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		sorted  []int64
		quantil float64
		want    float64
	}{
		{
			name:    "empty slice",
			sorted:  []int64{},
			quantil: 0.5,
			want:    0,
		},
		{
			name:    "single element",
			sorted:  []int64{25},
			quantil: 0.5,
			want:    25.0,
		},
		{
			name:    "multiple elements median",
			sorted:  []int64{10, 20, 30, 40, 50},
			quantil: 0.5,
			want:    30.0,
		},
		{
			name:    "25th percentile",
			sorted:  []int64{10, 20, 30, 40},
			quantil: 0.25,
			want:    17.5,
		},
		{
			name:    "75th percentile",
			sorted:  []int64{10, 20, 30, 40},
			quantil: 0.75,
			want:    32.5,
		},
		{
			name:    "Quantile at lower edge",
			sorted:  []int64{5, 15, 25},
			quantil: 0.0,
			want:    5.0,
		},
		{
			name:    "Quantile at upper edge",
			sorted:  []int64{5, 15, 25},
			quantil: 1.0,
			want:    25.0,
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			got := getQuantile(tt.sorted, tt.quantil)
			assert.InDelta(t, tt.want, got, 1e-6, "Quantil Berechnung für Testfall '%s' fehlgeschlagen.", tt.name)
		})
	}
}
