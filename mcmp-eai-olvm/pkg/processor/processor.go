// Package processor provides the data aggregation layer for the OLVM EAI service.
// It retrieves virtual machine, host, and cluster information from an oVirt/OLVM
// instance via the OLVM client, transforms the raw API responses into the
// canonical [Cloud] and [Server] structures, and returns them for further
// downstream processing (e.g. publishing to MCMP).
//
// Typical usage:
//
//	client, err := olvm.NewClient(cfg)
//	if err != nil { ... }
//
//	p, err := processor.NewProcessor(client, logger)
//	if err != nil { ... }
//
//	cloud, err := p.AggregateData(ctx)
//	if err != nil { ... }
package processor

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-olvm/pkg/client/olvm"
)

var ErrNilClient = errors.New("OLVM client must not be nil")

// Processor aggregates data from an OLVM instance and transforms it into the
// canonical cloud-server model consumed by MCMP.
//
// It must be created via [NewProcessor]; the zero value is not valid.
type Processor struct {
	olvmClient *olvm.Client
	logger     logging.Logger
}

// NewProcessor constructs a new [Processor] backed by the given OLVM client.
//
// If client is nil, [ErrNilClient] is returned.
// If logger is nil, a no-op logger is used so that callers are not required
// to provide one.
func NewProcessor(client *olvm.Client, logger logging.Logger) (*Processor, error) {
	if client == nil {
		return nil, ErrNilClient
	}
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}
	return &Processor{olvmClient: client, logger: logger}, nil
}

// AggregateData fetches all VMs, hosts, and clusters from the OLVM API in
// parallel-friendly sequential calls and assembles them into a single [Cloud]
// value.
//
// For every VM the method resolves the human-readable cluster and host names
// from the respective ID-to-name lookup maps and converts raw API field types
// (memory in bytes as a string, CPU topology strings) into the typed fields
// expected by [Server].
//
// The returned [Cloud] value contains:
//   - Cloud: the hostname of the OLVM instance.
//   - CloudType: always [CloudTypeOLVM].
//   - Servers: one [Server] entry per VM reported by the OLVM API.
//
// The context ctx is forwarded to all underlying HTTP calls; cancelling it
// aborts the operation and propagates the error back to the caller.
//
// Errors are wrapped with contextual messages so callers can distinguish which
// resource type caused the failure (VMs, hosts, or clusters).
func (p *Processor) AggregateData(ctx context.Context) (*Cloud, error) {
	vmsResp, err := p.olvmClient.GetVMs(ctx)
	if err != nil {
		p.logger.Error("failed to get VMs", "error", err)
		return nil, fmt.Errorf("failed to aggregate VM data: %w", err)
	}

	hostsResp, err := p.olvmClient.GetHosts(ctx)
	if err != nil {
		p.logger.Error("failed to get hosts", "error", err)
		return nil, fmt.Errorf("failed to aggregate host data: %w", err)
	}

	clustersResp, err := p.olvmClient.GetClusters(ctx)
	if err != nil {
		p.logger.Error("failed to get clusters", "error", err)
		return nil, fmt.Errorf("failed to aggregate cluster data: %w", err)
	}

	hostMap := make(map[string]string, len(hostsResp.Host))
	for _, h := range hostsResp.Host {
		hostMap[h.Id] = h.Name
	}

	clusterMap := make(map[string]string, len(clustersResp.Cluster))
	for _, c := range clustersResp.Cluster {
		clusterMap[c.Id] = c.Name
	}

	servers := make([]Server, 0, len(vmsResp.Vm))
	for _, vm := range vmsResp.Vm {
		guestName := strings.TrimSpace(vm.GuestOperatingSystem.Distribution + " " + vm.GuestOperatingSystem.Version.FullVersion)
		server := Server{
			ServerKind:          ServerKindVirtual,
			ServerType:          ServerTypeOLVM,
			Name:                &vm.Name,
			UUID:                &vm.ID,
			VMID:                &vm.ID,
			InstanceUUID:        &vm.ID,
			Cluster:             lookupName(clusterMap, vm.Cluster.ID),
			Host:                lookupName(hostMap, vm.Host.ID),
			PowerState:          &vm.Status,
			MemoryMB:            p.parseMemoryMB(vm.Memory),
			NumCPU:              p.parseUint(vm.CPU.Topology.Sockets),
			NumCoresPerSocket:   p.parseUint(vm.CPU.Topology.Cores),
			NumOfThreads:        p.parseUint(vm.CPU.Topology.Threads),
			GuestConfigFullName: stringPtrIfNotEmpty(guestName),
			BootTime:            formatBootTime(vm.StartTime),
		}
		servers = append(servers, server)
	}

	cloud := &Cloud{
		Cloud:     p.olvmClient.GetHostname(),
		CloudType: CloudTypeOLVM,
		Servers:   servers,
	}
	return cloud, nil
}

// lookupName returns a pointer to the map value for key, or nil if not found.
func lookupName(m map[string]string, id string) *string {
	if name, ok := m[id]; ok {
		return &name
	}
	return nil
}

// stringPtrIfNotEmpty returns nil if s is empty, otherwise a pointer to s.
func stringPtrIfNotEmpty(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}

// formatBootTime converts a Unix millisecond timestamp into a pointer to an
// RFC 3339-formatted string.
//
// If startTime is 0 (i.e. the VM has never been started or the field is
// absent in the API response), nil is returned so that the JSON output omits
// the field entirely.
func formatBootTime(startTime int64) *string {
	if startTime == 0 {
		return nil
	}
	s := time.UnixMilli(startTime).Format(time.RFC3339)
	return &s
}

// parseMemoryMB parses a raw memory string (bytes, base-10) returned by the
// OLVM API and converts it to mebibytes (MiB).
//
// Returns nil if memoryStr is empty or cannot be parsed as an unsigned 64-bit
// integer. Parsing failures are logged at WARN level, including the offending
// value.
func (p *Processor) parseMemoryMB(memoryStr string) *uint64 {
	if memoryStr == "" {
		return nil
	}
	memBytes, err := strconv.ParseUint(memoryStr, 10, 64)
	if err != nil {
		p.logger.Warn("failed to parse memory", "memory", memoryStr, "error", err)
		return nil
	}
	val := memBytes / (1024 * 1024)
	return &val
}

// parseUint parses a base-10 string into a *uint suitable for CPU topology
// fields (sockets, cores, threads).
//
// Returns nil if s is empty or cannot be parsed as an unsigned integer.
// Parsing failures are logged at WARN level, including the offending value.
func (p *Processor) parseUint(s string) *uint {
	if s == "" {
		return nil
	}
	val, err := strconv.ParseUint(s, 10, 32)
	if err != nil {
		p.logger.Warn("failed to parse uint", "value", s, "error", err)
		return nil
	}
	valRes := uint(val)
	return &valRes
}