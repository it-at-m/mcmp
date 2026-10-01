package processor

import (
	"context"
	"fmt"
	"time"
)

// A Snapshot of a Server on the hypervisor.
type Snapshot struct {
	Name        string        `json:"name,omitempty"`        // Name of the snapshot.
	Description string        `json:"description,omitempty"` // Description of the snapshot.
	CreateTime  *time.Time    `json:"create_time,omitempty"` // Time of snapshot creation.
	State       SnapshotState `json:"state,omitempty"`       // If the snapshot is powered on or off.
}

// processSnapshots processes a QEMU resource's snapshots. This involves
// an API call to the PDM instance.
//
// The "current" snapshot is omitted from the output.
//
// An error is returned if the API call fails, which may happen if the
// resource is invalid (e.g. not a QEMU VM) or if the processor
// is missing the required permissions.
func (p *Processor) processSnapshots(ctx context.Context, cluster string, vmid uint64) ([]*Snapshot, error) {
	snapshots, err := p.client.Snapshots(ctx, cluster, vmid)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch snapshots: %w", err)
	}

	result := make([]*Snapshot, 0, len(snapshots)-1)
	for _, snapshot := range snapshots {
		if snapshot.Name == "current" {
			continue
		}

		createTime := time.Unix(snapshot.Snaptime, 0)

		var state SnapshotState
		if snapshot.VMState {
			state = SnapshotPoweredOn
		} else {
			state = SnapshotPoweredOff
		}

		result = append(result, &Snapshot{
			Name:        snapshot.Name,
			Description: snapshot.Description,
			CreateTime:  &createTime,
			State:       state,
		})
	}

	return result, nil
}
