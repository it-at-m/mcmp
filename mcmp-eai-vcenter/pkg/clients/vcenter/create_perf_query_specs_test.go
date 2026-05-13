package vcenter

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/vmware/govmomi/vim25/mo"
	"github.com/vmware/govmomi/vim25/types"
)

func TestCreatePerfQuerySpecs(t *testing.T) {
	t.Parallel()

	startTime := time.Now().Add(-1 * time.Hour)
	endTime := time.Now()

	vm1 := mo.VirtualMachine{
		ManagedEntity: mo.ManagedEntity{
			ExtensibleManagedObject: mo.ExtensibleManagedObject{
				Self: types.ManagedObjectReference{Type: "VirtualMachine", Value: "vm-01"},
			},
		},
	}
	vm2 := mo.VirtualMachine{
		ManagedEntity: mo.ManagedEntity{
			ExtensibleManagedObject: mo.ExtensibleManagedObject{
				Self: types.ManagedObjectReference{Type: "VirtualMachine", Value: "vm-02"},
			},
		},
	}

	vms := []mo.VirtualMachine{vm1, vm2}
	counterID := int32(123)
	intervalSeconds := int32(300)

	expectedSpecs := []types.PerfQuerySpec{
		{
			Entity:     vm1.Reference(),
			MetricId:   []types.PerfMetricId{{CounterId: counterID, Instance: "*"}},
			IntervalId: intervalSeconds,
			StartTime:  toUTCPtr(startTime),
			EndTime:    toUTCPtr(endTime),
		},
		{
			Entity:     vm2.Reference(),
			MetricId:   []types.PerfMetricId{{CounterId: counterID, Instance: "*"}},
			IntervalId: intervalSeconds,
			StartTime:  toUTCPtr(startTime),
			EndTime:    toUTCPtr(endTime),
		},
	}

	actualSpecs := createPerfQuerySpecs(vms, counterID, intervalSeconds, startTime, endTime)

	assert.Len(t, actualSpecs, len(expectedSpecs), "Die Länge der specs sollte übereinstimmen")

	for i, expectedSpec := range expectedSpecs {
		actualSpec := actualSpecs[i]
		assert.Equal(t, expectedSpec.Entity, actualSpec.Entity, "Entität sollte übereinstimmen")
		assert.Equal(t, expectedSpec.IntervalId, actualSpec.IntervalId, "IntervallID sollte übereinstimmen")
		assert.Equal(t, expectedSpec.MetricId, actualSpec.MetricId, "MetricId sollte übereinstimmen")
		assert.True(t, expectedSpec.StartTime.Equal(*actualSpec.StartTime), "Startzeit sollte übereinstimmen")
		assert.True(t, expectedSpec.EndTime.Equal(*actualSpec.EndTime), "Endzeit sollte übereinstimmen")
	}
}

func toUTCPtr(t time.Time) *time.Time {
	utcTime := t.UTC()
	return &utcTime
}
