package processor

import (
	"fmt"
	"mcmp-eai-proxmox/pkg/clients/proxmox"
	"regexp"
	"strconv"
	"strings"
)

// A Disk object to save to the MCMP database.
type Disk struct {
	VDiskKey          uint32 `json:"vdisk_key"`
	UnitNumber        uint32 `json:"unit_number,omitempty"`
	DiskProvisioning  string `json:"disk_provisioning,omitempty"`
	FileName          string `json:"file_name,omitempty"`
	CapacityInBytes   uint64 `json:"capacity_in_bytes,omitempty"`
	VDiskID           string `json:"vdisk_id,omitempty"`
	Device            string `json:"device,omitempty"`
	VirtualDiskFormat string `json:"virtual_disk_format,omitempty"`
	DiskMode          string `json:"disk_mode,omitempty"`
}

// processDisks processes all Disks from the given VMConfig data.
func (p *Processor) processDisks(cfg *proxmox.VMConfig) []*Disk {
	result := make([]*Disk, 0, len(cfg.Disks))

	for name, d := range cfg.Disks {
		// ignore CD drives
		if d.File == "none" {
			continue
		}

		capacity, err := parseBinaryPrefixNumber(d.Size)
		if err != nil {
			p.logger.Error("failed to parse disk size", "size", d.Size, "err", err)
			continue
		}

		result = append(result, &Disk{
			VDiskKey:        generateUniqueVDiskKey(&d),
			UnitNumber:      uint32(d.Seq),
			FileName:        d.File,
			CapacityInBytes: uint64(capacity),
			Device:          name,
		})
	}

	return result
}

func generateUniqueVDiskKey(disk *proxmox.Disk) uint32 {
	switch disk.Bus {
	case "ide":
		return 0 + uint32(disk.Seq)
	case "sata":
		return 1000 + uint32(disk.Seq)
	case "scsi":
		return 2000 + uint32(disk.Seq)
	case "virtio":
		return 3000 + uint32(disk.Seq)
	default:
		return 10000 + uint32(disk.Seq)
	}
}

var binaryPrefixedNumberRegexp = regexp.MustCompile(`^([-0-9]+)([kmgt])i?b?$`)

func parseBinaryPrefixNumber(input string) (int64, error) {
	matches := binaryPrefixedNumberRegexp.FindStringSubmatch(strings.ToLower(input))
	if len(matches) < 3 {
		return 0, fmt.Errorf("failed to parse binary prefixed number: %s", input)
	}

	num, prefix := matches[1], matches[2]

	val, err := strconv.ParseInt(num, 10, 64)
	if err != nil {
		return 0, err
	}

	switch prefix {
	case "k":
		return val * 1024, nil
	case "m":
		return val * 1024 * 1024, nil
	case "g":
		return val * 1024 * 1024 * 1024, nil
	case "t":
		return val * 1024 * 1024 * 1024 * 1024, nil
	default:
		return 0, fmt.Errorf("unknown binary prefix: %s (number: %s)", prefix, input)
	}
}
