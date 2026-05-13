package vcenter

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/vmware/govmomi/vim25/types"
)

func TestGroupTimeValues(t *testing.T) {
	t.Parallel()

	baseTime := time.Date(2024, time.April, 16, 10, 30, 0, 0, time.UTC)

	tests := []struct {
		name          string
		series        *types.PerfMetricIntSeries
		sampleInfo    []types.PerfSampleInfo
		blockDuration time.Duration
		expected      map[time.Time][]int64
	}{
		{
			name: "Standardfall Gruppierung 5-Minuten Blöcke",
			series: &types.PerfMetricIntSeries{
				Value: []int64{1000, 2000, 1500, 3000},
			},
			sampleInfo: []types.PerfSampleInfo{
				{Timestamp: baseTime},                      // 10:30
				{Timestamp: baseTime.Add(3 * time.Minute)}, // 10:33
				{Timestamp: baseTime.Add(6 * time.Minute)}, // 10:36
				{Timestamp: baseTime.Add(8 * time.Minute)}, // 10:38
			},
			blockDuration: 5 * time.Minute,
			expected: map[time.Time][]int64{
				baseTime.Truncate(5 * time.Minute).Add(5 * time.Minute).Local():                      {1000, 2000}, // bis 10:35
				baseTime.Add(6 * time.Minute).Truncate(5 * time.Minute).Add(5 * time.Minute).Local(): {1500, 3000}, // bis 10:40
			},
		},
		{
			name: "Ein Zeitblock für alle Werte (großes Intervall)",
			series: &types.PerfMetricIntSeries{
				Value: []int64{500, 1500, 2500},
			},
			sampleInfo: []types.PerfSampleInfo{
				{Timestamp: baseTime.Add(1 * time.Minute)},
				{Timestamp: baseTime.Add(4 * time.Minute)},
				{Timestamp: baseTime.Add(9 * time.Minute)},
			},
			blockDuration: 15 * time.Minute,
			expected: map[time.Time][]int64{
				baseTime.Truncate(15 * time.Minute).Add(15 * time.Minute).Local(): {500, 1500, 2500},
			},
		},
		{
			name: "Leere Zeitserie",
			series: &types.PerfMetricIntSeries{
				Value: []int64{},
			},
			sampleInfo:    []types.PerfSampleInfo{},
			blockDuration: 5 * time.Minute,
			expected:      map[time.Time][]int64{},
		},
		{
			name: "Negative Werte und Nullwerte",
			series: &types.PerfMetricIntSeries{
				Value: []int64{-500, 0, 1000},
			},
			sampleInfo: []types.PerfSampleInfo{
				{Timestamp: baseTime},
				{Timestamp: baseTime.Add(1 * time.Minute)},
				{Timestamp: baseTime.Add(6 * time.Minute)},
			},
			blockDuration: 5 * time.Minute,
			expected: map[time.Time][]int64{
				baseTime.Truncate(5 * time.Minute).Add(5 * time.Minute).Local():                      {-500, 0}, // bis 10:35
				baseTime.Add(6 * time.Minute).Truncate(5 * time.Minute).Add(5 * time.Minute).Local(): {1000},    // bis 10:40
			},
		},
		{
			name: "Kurzes Blockintervall (1 Minute)",
			series: &types.PerfMetricIntSeries{
				Value: []int64{100, 200, 300, 400},
			},
			sampleInfo: []types.PerfSampleInfo{
				{Timestamp: baseTime.Add(10 * time.Second)},
				{Timestamp: baseTime.Add(50 * time.Second)},
				{Timestamp: baseTime.Add(70 * time.Second)},
				{Timestamp: baseTime.Add(130 * time.Second)},
			},
			blockDuration: 1 * time.Minute,
			expected: map[time.Time][]int64{
				baseTime.Truncate(time.Minute).Add(1 * time.Minute).Local():                        {100, 200}, // bis 10:31
				baseTime.Add(70 * time.Second).Truncate(time.Minute).Add(1 * time.Minute).Local():  {300},      // bis 10:32
				baseTime.Add(130 * time.Second).Truncate(time.Minute).Add(1 * time.Minute).Local(): {400},      // bis 10:33
			},
		},
	}

	for _, tt := range tests {
		tt := tt
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			actual := groupTimeValues(tt.series, tt.sampleInfo, tt.blockDuration)

			// Anzahl der Gruppen prüfen
			assert.Equal(t, len(tt.expected), len(actual), "Die Anzahl der Gruppen sollte übereinstimmen.")

			// Prüfen, ob alle erwarteten Zeitstempel und Werte vorhanden sind
			for expectedTs, expectedValues := range tt.expected {
				actualValues, exists := actual[expectedTs]
				if assert.True(t, exists, "Der Zeitstempel %v fehlt in der Ausgabe.", expectedTs) {
					assert.ElementsMatch(t, expectedValues, actualValues, "Die Werte für den Zeitstempel %v stimmen nicht.", expectedTs)
				}
			}
		})
	}
}
