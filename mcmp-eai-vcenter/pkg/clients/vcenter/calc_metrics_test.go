package vcenter

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func Test_calcMetrics(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name   string
		values []int64
		want   IntervalMetrics
	}{
		{
			name:   "Einfacher Testfall",
			values: []int64{10, 20, 30, 40, 50},
			want: IntervalMetrics{
				Min:    10,
				Q1:     20,
				Median: 30,
				Q3:     40,
				Max:    50,
				Avg:    30,
			},
		},
		{
			name:   "Gerade Anzahl von Elementen",
			values: []int64{1, 2, 3, 4},
			want: IntervalMetrics{
				Min:    1,
				Q1:     1.75,
				Median: 2.5,
				Q3:     3.25,
				Max:    4,
				Avg:    2.5,
			},
		},
		{
			name:   "Zahlen nicht sortiert",
			values: []int64{7, 2, 9, 4, 5},
			want: IntervalMetrics{
				Min:    2,
				Q1:     4,
				Median: 5,
				Q3:     7,
				Max:    9,
				Avg:    5.4,
			},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			got := calcMetrics(tt.values)
			assert.InDelta(t, tt.want.Min, got.Min, 1e-6, "Min im Testfall '%s' falsch", tt.name)
			assert.InDelta(t, tt.want.Q1, got.Q1, 1e-6, "LowerQuartile im Testfall '%s' falsch", tt.name)
			assert.InDelta(t, tt.want.Median, got.Median, 1e-6, "Median im Testfall '%s' falsch", tt.name)
			assert.InDelta(t, tt.want.Q3, got.Q3, 1e-6, "UpperQuartile im Testfall '%s' falsch", tt.name)
			assert.InDelta(t, tt.want.Max, got.Max, 1e-6, "Max im Testfall '%s' falsch", tt.name)
			assert.InDelta(t, tt.want.Avg, got.Avg, 1e-6, "Avg im Testfall '%s' falsch", tt.name)
		})
	}
}
