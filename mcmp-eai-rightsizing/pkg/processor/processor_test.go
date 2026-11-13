package processor

import (
	"testing"
)

const (
	// mbPerGB is the memory sizing increment used by calculateRecommendedMemory.
	mbPerGB = 1024

	// defaultTestMemoryMargin mirrors the package default safety margin for memory.
	defaultTestMemoryMargin = 1.15

	// nonDatabaseVM is a VM name which does NOT match the database name pattern.
	nonDatabaseVM = "web01.example.com"

	// databaseVM matches reDB (`\w+db[cdkps][0-9]{3}.*`).
	databaseVM = "srvdbp001.example.com"
)

// memoryCase is a single table-driven test case for Processor.calculateRecommendedMemory.
type memoryCase struct {
	name            string
	vmName          string
	currentMemoryMB int
	memUtilPercent  float64
	marginFactor    float64 // 0 means: use defaultTestMemoryMargin
	want            int
}

// newMemoryTestProcessor returns a Processor with the given memory margin.
//
// calculateRecommendedMemory only reads config.MemoryMarginFactor, therefore
// neither an MCMP client nor a logger is required for these tests.
func newMemoryTestProcessor(marginFactor float64) *Processor {
	return &Processor{
		config: Config{MemoryMarginFactor: marginFactor},
	}
}

// runMemoryCases executes the given cases and asserts the recommendation as well as
// the 1 GiB sizing grid property.
func runMemoryCases(t *testing.T, cases []memoryCase) {
	t.Helper()

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			margin := tc.marginFactor
			if margin == 0 {
				margin = defaultTestMemoryMargin
			}

			got := newMemoryTestProcessor(margin).calculateRecommendedMemory(
				tc.vmName,
				tc.currentMemoryMB,
				tc.memUtilPercent,
			)

			if got != tc.want {
				t.Fatalf("calculateRecommendedMemory(vm=%q, current=%d MB, util=%.2f%%, margin=%.2f) = %d MB, want %d MB (%d GB)",
					tc.vmName, tc.currentMemoryMB, tc.memUtilPercent, margin,
					got, tc.want, tc.want/mbPerGB)
			}

			// Any recommendation must be a multiple of 1 GiB.
			if got%mbPerGB != 0 {
				t.Errorf("calculateRecommendedMemory(vm=%q, current=%d MB, util=%.2f%%) = %d MB, which is not a multiple of %d MB",
					tc.vmName, tc.currentMemoryMB, tc.memUtilPercent, got, mbPerGB)
			}
		})
	}
}

// TestCalculateRecommendedMemory_InvalidUtilization covers the early exit for
// missing or invalid utilization values.
func TestCalculateRecommendedMemory_InvalidUtilization(t *testing.T) {
	runMemoryCases(t, []memoryCase{
		{
			name:            "zero utilization keeps current allocation",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  0,
			want:            16 * mbPerGB,
		},
		{
			name:            "negative utilization keeps current allocation",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  -25,
			want:            8 * mbPerGB,
		},
		{
			name:            "zero utilization keeps current allocation for db server",
			vmName:          databaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  0,
			want:            8 * mbPerGB,
		},
		{
			name:            "zero utilization on small vm keeps current allocation",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 2 * mbPerGB,
			memUtilPercent:  0,
			want:            2 * mbPerGB,
		},
		{
			name:            "zero current memory stays zero",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 0,
			memUtilPercent:  50,
			want:            0,
		},
	})
}

// TestCalculateRecommendedMemory_GeneralServers covers the rules for servers which
// are not detected as database servers (4 GB floor, half clamp, 1 GiB rounding).
func TestCalculateRecommendedMemory_GeneralServers(t *testing.T) {
	runMemoryCases(t, []memoryCase{
		{
			name:            "1 gb vm stays untouched",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 1 * mbPerGB,
			memUtilPercent:  1,
			want:            1 * mbPerGB,
		},
		{
			name:            "2 gb vm is never reduced",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 2 * mbPerGB,
			memUtilPercent:  50,
			want:            2 * mbPerGB,
		},
		{
			name:            "4 gb vm is protected by the general floor",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 4 * mbPerGB,
			memUtilPercent:  50,
			want:            4 * mbPerGB,
		},
		{
			name:            "4 gb vm at 10 percent stays untouched",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 4 * mbPerGB,
			memUtilPercent:  10,
			want:            4 * mbPerGB,
		},
		{
			name:            "4 gb vm keeps its allocation even at 150 percent utilization",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 4 * mbPerGB,
			memUtilPercent:  150,
			want:            4 * mbPerGB,
		},
		{
			name:            "8 gb at 50 percent is halved incl. safety margin",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  50,
			want:            5 * mbPerGB,
		},
		{
			name:            "8 gb at 20 percent is floored to 4 gb",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  20,
			want:            4 * mbPerGB,
		},
		{
			name:            "8 gb at 1 percent is floored to exactly half",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  1,
			want:            4 * mbPerGB,
		},
		{
			name:            "8 gb at 100 percent is unchanged",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  100,
			want:            8 * mbPerGB,
		},
		{
			name:            "8 gb at 120 percent does not get an extra margin",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  120,
			want:            10 * mbPerGB,
		},
		{
			name:            "12 gb at 50 percent",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 12 * mbPerGB,
			memUtilPercent:  50,
			want:            7 * mbPerGB,
		},
		{
			name:            "12 gb at 33 percent is clamped to half",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 12 * mbPerGB,
			memUtilPercent:  33,
			want:            6 * mbPerGB,
		},
		{
			name:            "16 gb at 5 percent is clamped to half",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  5,
			want:            8 * mbPerGB,
		},
		{
			name:            "16 gb at 50 percent",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  50,
			want:            9 * mbPerGB,
		},
		{
			name:            "16 gb at 51 percent rounds down",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  51,
			want:            9 * mbPerGB,
		},
		{
			name:            "16 gb at 56 percent rounds up",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  56,
			want:            10 * mbPerGB,
		},
		{
			name:            "16 gb at 90 percent grows above the current allocation",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  90,
			want:            17 * mbPerGB,
		},
		{
			name:            "16 gb at 100 percent is unchanged",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  100,
			want:            16 * mbPerGB,
		},
		{
			name:            "32 gb at 50 percent",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 32 * mbPerGB,
			memUtilPercent:  50,
			want:            18 * mbPerGB,
		},
		{
			name:            "64 gb at 50 percent",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 64 * mbPerGB,
			memUtilPercent:  50,
			want:            37 * mbPerGB,
		},
	})
}

// TestCalculateRecommendedMemory_DatabaseServers covers the database specific rules
// (Oracle safety buffer of 2 GB, 6 GB minimum, half clamp).
func TestCalculateRecommendedMemory_DatabaseServers(t *testing.T) {
	runMemoryCases(t, []memoryCase{
		{
			name:            "6 gb db is never downsized",
			vmName:          databaseVM,
			currentMemoryMB: 6 * mbPerGB,
			memUtilPercent:  50,
			want:            6 * mbPerGB,
		},
		{
			name:            "4 gb db is never downsized",
			vmName:          databaseVM,
			currentMemoryMB: 4 * mbPerGB,
			memUtilPercent:  50,
			want:            4 * mbPerGB,
		},
		{
			name:            "8 gb db at 20 percent is floored to the 6 gb db minimum",
			vmName:          databaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  20,
			want:            6 * mbPerGB,
		},
		{
			name:            "8 gb db at 60 percent stays unchanged",
			vmName:          databaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  60,
			want:            8 * mbPerGB,
		},
		{
			name:            "16 gb db at 5 percent is clamped to half",
			vmName:          databaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  5,
			want:            8 * mbPerGB,
		},
		{
			name:            "16 gb db at 20 percent is clamped to half",
			vmName:          databaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  20,
			want:            8 * mbPerGB,
		},
		{
			name:            "16 gb db at 50 percent includes 2 gb oracle safety buffer",
			vmName:          databaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  50,
			want:            11 * mbPerGB,
		},
		{
			name:            "32 gb db at 30 percent is clamped to half",
			vmName:          databaseVM,
			currentMemoryMB: 32 * mbPerGB,
			memUtilPercent:  30,
			want:            16 * mbPerGB,
		},
		{
			name:            "32 gb db at 50 percent",
			vmName:          databaseVM,
			currentMemoryMB: 32 * mbPerGB,
			memUtilPercent:  50,
			want:            20 * mbPerGB,
		},
		{
			name:            "64 gb db at 5 percent is clamped to half",
			vmName:          databaseVM,
			currentMemoryMB: 64 * mbPerGB,
			memUtilPercent:  5,
			want:            32 * mbPerGB,
		},
		{
			name:            "64 gb db at 50 percent",
			vmName:          databaseVM,
			currentMemoryMB: 64 * mbPerGB,
			memUtilPercent:  50,
			want:            39 * mbPerGB,
		},
	})
}

// TestCalculateRecommendedMemory_MarginFactor verifies that the configured
// MemoryMarginFactor is honoured and only applied when the allocation shrinks.
func TestCalculateRecommendedMemory_MarginFactor(t *testing.T) {
	runMemoryCases(t, []memoryCase{
		{
			name:            "margin 1.0 produces no safety buffer",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  50,
			marginFactor:    1.0,
			want:            8 * mbPerGB,
		},
		{
			name:            "margin 1.0 on db server",
			vmName:          databaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  50,
			marginFactor:    1.0,
			want:            10 * mbPerGB,
		},
		{
			name:            "margin 1.0 at 80 percent utilization",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  80,
			marginFactor:    1.0,
			want:            13 * mbPerGB,
		},
		{
			name:            "margin 1.3 increases the recommendation",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  50,
			marginFactor:    1.3,
			want:            10 * mbPerGB,
		},
		{
			name:            "margin 2.0 keeps the current allocation",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 16 * mbPerGB,
			memUtilPercent:  50,
			marginFactor:    2.0,
			want:            16 * mbPerGB,
		},
		{
			name:            "margin is not applied when the recommendation grows",
			vmName:          nonDatabaseVM,
			currentMemoryMB: 8 * mbPerGB,
			memUtilPercent:  120,
			marginFactor:    1.15,
			want:            10 * mbPerGB,
		},
	})
}

// TestCalculateRecommendedMemory_DatabaseNameDetection verifies that the database
// specific rules are only applied for names matching reDB
// (`\w+db[cdkps][0-9]{3}.*`).
func TestCalculateRecommendedMemory_DatabaseNameDetection(t *testing.T) {
	const (
		current = 16 * mbPerGB
		util    = 50.0

		generalRecommendation  = 9 * mbPerGB  // 16 GB * 50 % * 1.15 = 9420.8 MB -> 9216 MB
		databaseRecommendation = 11 * mbPerGB // 9216 MB + 2 GB oracle buffer = 11264 MB
	)

	tests := []struct {
		vmName     string
		wantDBRule bool
	}{
		{vmName: "appdbp001.example.com", wantDBRule: true},
		{vmName: "oracledbc123", wantDBRule: true},
		{vmName: "srvdbs999.prod.local", wantDBRule: true},
		{vmName: "webserverdbk001", wantDBRule: true},
		{vmName: "appdbx123.example.com", wantDBRule: false}, // 'x' is not in [cdkps]
		{vmName: "dbp001", wantDBRule: false},                // needs at least one character before "db"
		{vmName: "appdbp01.example.com", wantDBRule: false},  // only two digits
		{vmName: "web01.example.com", wantDBRule: false},
		{vmName: "<unknown>", wantDBRule: false},
	}

	for _, tc := range tests {
		t.Run(tc.vmName, func(t *testing.T) {
			want := generalRecommendation
			if tc.wantDBRule {
				want = databaseRecommendation
			}

			got := newMemoryTestProcessor(defaultTestMemoryMargin).
				calculateRecommendedMemory(tc.vmName, current, util)

			if got != want {
				t.Fatalf("calculateRecommendedMemory(vm=%q, current=%d MB, util=%.2f%%) = %d MB, want %d MB (db rule applied: %t)",
					tc.vmName, current, util, got, want, tc.wantDBRule)
			}
		})
	}
}

// TestCalculateRecommendedMemory_Invariants checks general properties of the
// recommendation over a broad matrix of inputs:
//
//   - the result is a positive value
//   - the result is a multiple of 1 GiB
//   - the result is never smaller than half of the current allocation
//   - the result never exceeds current * margin (+ half an increment for the
//     rounding, plus the 2 GB buffer for database servers)
//   - below the break-even point of the safety margin (100 % / 1.15 ≈ 86.9 %)
//     a non-database server never grows
func TestCalculateRecommendedMemory_Invariants(t *testing.T) {
	p := newMemoryTestProcessor(defaultTestMemoryMargin)

	vmClasses := []struct {
		vmName      string
		extraBuffer int
	}{
		{vmName: nonDatabaseVM, extraBuffer: 0},
		{vmName: databaseVM, extraBuffer: 2 * mbPerGB},
	}

	currents := []int{
		1 * mbPerGB,
		2 * mbPerGB,
		4 * mbPerGB,
		6 * mbPerGB,
		8 * mbPerGB,
		12 * mbPerGB,
		16 * mbPerGB,
		32 * mbPerGB,
		64 * mbPerGB,
	}
	utils := []float64{0, 0.5, 1, 5, 10, 25, 50, 75, 85, 90, 99, 100}

	for _, class := range vmClasses {
		for _, current := range currents {
			for _, util := range utils {
				got := p.calculateRecommendedMemory(class.vmName, current, util)

				if got <= 0 {
					t.Errorf("vm=%q current=%d MB util=%.1f%%: got %d MB, want a positive recommendation",
						class.vmName, current, util, got)
				}

				if got%mbPerGB != 0 {
					t.Errorf("vm=%q current=%d MB util=%.1f%%: got %d MB, which is not a multiple of %d MB",
						class.vmName, current, util, got, mbPerGB)
				}

				if got*2 < current {
					t.Errorf("vm=%q current=%d MB util=%.1f%%: got %d MB, which is less than half of the current allocation",
						class.vmName, current, util, got)
				}

				// Upper bound: the safety margin is only applied while reducing,
				// so at most current * margin is used as basis. Rounding to the
				// 1 GiB grid may add up to 512 MB, the DB rule adds 2 GB.
				upperBound := float64(current)*defaultTestMemoryMargin + 512 + float64(class.extraBuffer)
				if float64(got) > upperBound {
					t.Errorf("vm=%q current=%d MB util=%.1f%%: got %d MB, which exceeds the upper bound of %.0f MB",
						class.vmName, current, util, got, upperBound)
				}

				// Only above 100 % / 1.15 ≈ 86.9 % the margin can push the
				// recommendation beyond the current allocation.
				if class.extraBuffer == 0 && util <= 85 && got > current {
					t.Errorf("vm=%q current=%d MB util=%.1f%%: got %d MB, but a non-database server must not grow below the break-even point",
						class.vmName, current, util, got)
				}
			}
		}
	}
}
