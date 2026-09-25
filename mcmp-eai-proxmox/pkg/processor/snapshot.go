package processor

import (
	"context"
	"fmt"
	"mcmp-eai-proxmox/pkg/clients/proxmox"
	"time"
)

// A Snapshot of a Server on the hypervisor.
type Snapshot struct {
	Name        string        `json:"name,omitempty"`        // Name of the snapshot.
	Description string        `json:"description,omitempty"` // Description of the snapshot.
	CreateTime  *time.Time    `json:"create_time,omitempty"` // Time of snapshot creation.
	State       SnapshotState `json:"state,omitempty"`       // If the snapshot is powered on or off.
}

// ProcessSnapshots processes a QEMU resource's snapshots. This involves
// an API call to the PDM instance.
//
// The results are written to the outparam server. The "current" snapshot
// is not included in the snapshot list.
//
// An error is returned if the API call fails, which may happen if the
// resource is invalid (e.g. not a QEMU VM) or if the processor
// is missing the required permissions.
func (p *Processor) processSnapshots(ctx context.Context, res *proxmox.Resource, server *Server) error {
	snapshots, err := p.client.Snapshots(ctx, server.Cluster, res.VMID)
	if err != nil {
		return fmt.Errorf("failed to fetch snapshots: %w", err)
	}

	server.Snapshots = make([]*Snapshot, 0, len(snapshots)-1)
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

		server.Snapshots = append(server.Snapshots, &Snapshot{
			Name:        snapshot.Name,
			Description: snapshot.Description,
			CreateTime:  &createTime,
			State:       state,
		})
	}

	return nil
}
