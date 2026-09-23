package processor

import (
	"context"
	"fmt"
	"mcmp-eai-proxmox/pkg/clients/pdm"
)

const MountPointSourceProxmox = "proxmox"

// A MountPoint mounted in the Guest OS.
type MountPoint struct {
	DiskPath         string `json:"disk_path"`
	CapacityInBytes  uint64 `json:"capacity_in_bytes"`
	FreeSpaceInBytes uint64 `json:"free_space_in_bytes"`
	FilesystemType   string `json:"filesystem_type"`
	Source           string `json:"source"`
}

// ProcessMountPoints queries the VM's cluster for QEMU Guest Agent
// data on the Guest OS's mount points.  If successful, the data is
// written to the given Server object.
//
// Warning: This query may take quite a bit of time. Frequently
// fetching this data is discouraged.
func (p *Processor) ProcessMountPoints(ctx context.Context, res *pdm.Resource, server *Server) error {
	filesystems, err := p.client.Filesystems(ctx, server.Cluster, res.Node, res.VMID)
	if err != nil {
		return fmt.Errorf("failed to fetch filesystems: %w", err)
	}

	server.MountPoints = make([]*MountPoint, 0, len(filesystems))
	for _, fs := range filesystems {
		server.MountPoints = append(server.MountPoints, &MountPoint{
			DiskPath:         fs.Mountpoint,
			CapacityInBytes:  fs.TotalBytes,
			FreeSpaceInBytes: fs.TotalBytes - fs.UsedBytes,
			FilesystemType:   fs.Type,
			Source:           MountPointSourceProxmox,
		})
	}

	return nil
}
