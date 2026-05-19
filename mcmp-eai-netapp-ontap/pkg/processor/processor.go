package processor

import (
	"context"
	"errors"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"sync"

	"github.com/hashicorp/go-multierror"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/utils"
	"github.com/it-at-m/mcmp/mcmp-eai-netapp-ontap/pkg/client/netapp/ontap"
	"golang.org/x/sync/errgroup"
)

const (
	DefaultConcurrency = 2
	MaxConcurrency     = 10
)

// Processor aggregates NetApp ONTAP data from various API endpoints.
type Processor struct {
	netappClient *ontap.Client
	concurrency  int
	logger       logging.Logger
}

// NewProcessor creates a new Processor with the given client, concurrency level, and logger.
// Concurrency is clamped between 1 and MaxConcurrency.
func NewProcessor(client *ontap.Client, concurrency int, logger logging.Logger) (*Processor, error) {
	if client == nil {
		return nil, errors.New("netapp client must not be nil")
	}

	concurrency = utils.ClampConcurrency(concurrency, DefaultConcurrency, MaxConcurrency)

	if logger == nil {
		logger = logging.NewNoOpLogger()
	}
	return &Processor{
		netappClient: client,
		concurrency:  concurrency,
		logger:       logger,
	}, nil
}

// AggregateData fetches all relevant data from NetApp ONTAP in parallel and aggregates it.
func (p *Processor) AggregateData(ctx context.Context) (*ontap.OntapData, error) {
	// 1. Fetch base data entities in parallel to reduce latency
	var (
		aggregates     []ontap.Aggregate
		volumes        []ontap.Volume
		exportPolicies []ontap.ExportPolicy
		cifsShares     []ontap.CIFSShare
		quotas         []ontap.Quota
		qtrees         []ontap.QTree
	)

	fetchGroup, fetchCtx := errgroup.WithContext(ctx)

	fetchGroup.Go(func() (err error) {
		aggregates, err = p.netappClient.FetchAggregates(fetchCtx)
		return p.wrapError(err, "aggregates")
	})

	fetchGroup.Go(func() (err error) {
		volumes, err = p.netappClient.FetchVolumes(fetchCtx)
		return p.wrapError(err, "volumes")
	})
	fetchGroup.Go(func() (err error) {
		exportPolicies, err = p.netappClient.FetchExportPolicies(fetchCtx)
		return p.wrapError(err, "exportPolicies")
	})
	fetchGroup.Go(func() (err error) {
		cifsShares, err = p.netappClient.FetchCIFSShares(fetchCtx)
		return p.wrapError(err, "cifsShares")
	})
	fetchGroup.Go(func() (err error) {
		quotas, err = p.netappClient.FetchQuotas(fetchCtx)
		return p.wrapError(err, "quotas")
	})
	fetchGroup.Go(func() (err error) {
		qtrees, err = p.netappClient.FetchQTrees(fetchCtx)
		return p.wrapError(err, "qtrees")
	})

	if err := fetchGroup.Wait(); err != nil {
		return nil, err
	}

	// Convert aggregates to AggregateData
	convertedAggregates := make([]ontap.AggregateData, len(aggregates))
	for i, agg := range aggregates {
		convertedAggregates[i] = NewAggregateData(agg)
	}

	// 2. Build lookup maps (CPU bound, fast)
	exportPolicyMap := p.buildExportPolicyMap(exportPolicies)
	cifsShareMap := p.buildCIFSShareMap(cifsShares)
	qtreeMap := p.buildQTreeMap(qtrees, quotas, exportPolicyMap)

	// 3. Aggregate per Volume and Group by SVM
	// We use a map to group volumes by SVM UUID temporarily
	svmMap := make(map[string]*ontap.SVMData)
	var (
		partialErrors error
		errorMutex    sync.Mutex
		svmMutex      sync.Mutex
	)

	aggregateGroup, aggregateCtx := errgroup.WithContext(ctx)
	aggregateGroup.SetLimit(p.concurrency)

	for _, vol := range volumes {
		// Capture loop variable
		currentVol := vol

		aggregateGroup.Go(func() error {
			// Pre-calculate SVM FQDN for mount paths
			// Format: svmName.srv.muenchen.de
			svmFQDN := fmt.Sprintf("%s.srv.muenchen.de", currentVol.SVM.Name)

			// A. Prepare Child Data (QTrees, Shares, Snapshots)
			var cleanQTrees []ontap.QTreeData
			var cleanShares []ontap.ShareData
			var cleanSnapshots []ontap.SnapshotData
			var cleanExportPolicy *ontap.ExportPolicyData

			// Process Export Policy
			if policy, ok := exportPolicyMap[currentVol.NAS.ExportPolicy.ID]; ok {
				cleanExportPolicy = &ontap.ExportPolicyData{
					ID:    policy.ID,
					Name:  policy.Name,
					Rules: toInterfaceSlice(policy.Rules),
				}
			}

			// Process CIFS Shares
			if shares, ok := cifsShareMap[currentVol.UUID]; ok {
				for _, s := range shares {
					// Calculate CIFS Mount Path: \\svm.srv.muenchen.de\ShareName
					cifsMountPath := fmt.Sprintf(`\\%s\%s`, svmFQDN, s.Name)

					if currentVol.NAS.Path == s.Path {

						cleanShares = append(cleanShares, ontap.ShareData{
							Name:          s.Name,
							Path:          s.Path,
							MountPathCIFS: cifsMountPath,
							ACLs:          toInterfaceSlice(s.ACLs),
						})
					}
				}
			}

			// Process QTrees
			if qtrees, ok := qtreeMap[currentVol.UUID]; ok {
				for _, q := range qtrees {
					// Calculate NFS Mount Path for QTree: svm.srv.muenchen.de:/vol/path/qtree
					var nfsMountPath *string
					if strings.HasSuffix(currentVol.SVM.Name, "dcc") {
						nfsMountPath = nil
					} else {
						s := fmt.Sprintf("%s:%s", svmFQDN, q.NAS.Path)
						nfsMountPath = &s
					}

					qtd := ontap.QTreeData{
						ID:           q.ID,
						Name:         q.Name,
						Path:         q.NAS.Path,
						MountPathNFS: nfsMountPath,
						Security:     q.SecurityStyle,
					}
					// Mapping Quota if exists (Quota is already enriched in buildQTreeMap)
					if q.Quota.Index != 0 || q.Quota.Type != "" {
						qtd.Quota = &ontap.QuotaData{
							Index:       q.Quota.Index,
							Type:        q.Quota.Type,
							HardLimit:   q.Quota.Space.HardLimit,
							Used:        q.Quota.Space.Used.Total,
							UsedPercent: q.Quota.Space.Used.HardLimitPercent,
						}
					}
					// Mapping QTree specific Export Policy
					if q.ExportPolicy.ID != 0 {
						qtd.ExportPolicy = &ontap.ExportPolicyData{
							ID:    q.ExportPolicy.ID,
							Name:  q.ExportPolicy.Name,
							Rules: toInterfaceSlice(q.ExportPolicy.Rules),
						}
					}

					var cleanQTreeShares []ontap.ShareData
					if shares, ok := cifsShareMap[currentVol.UUID]; ok {
						for _, s := range shares {
							// Calculate CIFS Mount Path: \\svm.srv.muenchen.de\ShareName
							cifsMountPath := fmt.Sprintf(`\\%s\%s`, svmFQDN, s.Name)
							if q.NAS.Path == s.Path {
								cleanQTreeShares = append(cleanQTreeShares, ontap.ShareData{
									Name:          s.Name,
									Path:          s.Path,
									MountPathCIFS: cifsMountPath,
									ACLs:          toInterfaceSlice(s.ACLs),
								})
							}
						}
					}
					qtd.CIFSShares = cleanQTreeShares

					cleanQTrees = append(cleanQTrees, qtd)
				}
			}

			// Fetch Snapshots (Expensive IO)
			snapshots, err := p.netappClient.FetchSnapshots(aggregateCtx, currentVol.UUID)
			if err != nil {
				// Log and record partial error, but continue without snapshots
				p.logger.Warn("failed to fetch snapshots", "volume", currentVol.Name, "error", err)
				errorMutex.Lock()
				partialErrors = multierror.Append(partialErrors, fmt.Errorf("volume %s: %w", currentVol.Name, err))
				errorMutex.Unlock()
			} else {
				for _, snap := range snapshots {
					cleanSnapshots = append(cleanSnapshots, ontap.SnapshotData{
						UUID:       snap.UUID,
						Name:       snap.Name,
						CreateTime: snap.CreateTime,
					})
				}
			}

			// Calculate NFS Mount Path for Volume
			var volNFSMountPath *string
			if strings.HasSuffix(currentVol.SVM.Name, "dcc") {
				volNFSMountPath = nil
			} else {
				s := fmt.Sprintf("%s:%s", svmFQDN, currentVol.NAS.Path)
				volNFSMountPath = &s
			}

			aggregateUUIDs := make([]string, 0, len(aggregates))
			for _, a := range currentVol.Aggregates {
				aggregateUUIDs = append(aggregateUUIDs, a.UUID)
			}

			// B. Build the Clean Volume Object
			volData := ontap.VolumeData{
				UUID:               currentVol.UUID,
				Name:               currentVol.Name,
				Size:               currentVol.Size,
				State:              currentVol.State,
				Type:               currentVol.Type,
				Style:              currentVol.Style,
				SnapshotPolicy:     currentVol.SnapshotPolicy.Name,
				Space:              currentVol.Space,
				AggregateUUIDs:     aggregateUUIDs,
				NASPath:            currentVol.NAS.Path,
				MountPathNFS:       volNFSMountPath,
				IsFlexClone:        currentVol.Clone.IsFlexClone,
				ParentVolumeName:   currentVol.Clone.ParentVolume.Name,
				ParentVolumeUUID:   currentVol.Clone.ParentVolume.UUID,
				ParentSnapshotName: currentVol.Clone.ParentSnapshot.Name,
				ParentSnapshotUUID: currentVol.Clone.ParentSnapshot.UUID,
				ParentSVMName:      currentVol.Clone.ParentSVM.Name,
				ParentSVMUUID:      currentVol.Clone.ParentSVM.UUID,
				IsSplitInitiated:   currentVol.Clone.SplitInitiated,
				Snaplock:           p.mapSnaplock(currentVol.Snaplock),
				ExportPolicy:       cleanExportPolicy,
				CIFSShares:         cleanShares,
				QTrees:             cleanQTrees,
				Snapshots:          cleanSnapshots,
			}

			// C. Add to SVM Grouping (Thread Safe)
			svmMutex.Lock()
			defer svmMutex.Unlock()

			svmUUID := currentVol.SVM.UUID
			if _, exists := svmMap[svmUUID]; !exists {
				svmMap[svmUUID] = &ontap.SVMData{
					Name:    currentVol.SVM.Name,
					UUID:    currentVol.SVM.UUID,
					Volumes: []ontap.VolumeData{},
				}
			}
			svmMap[svmUUID].Volumes = append(svmMap[svmUUID].Volumes, volData)

			return nil
		})
	}

	if err := aggregateGroup.Wait(); err != nil {
		return nil, err
	}

	// 4. Flatten Map to Slice for Output
	finalSVMs := make([]ontap.SVMData, 0, len(svmMap))
	for _, svm := range svmMap {
		if svm == nil {
			continue
		}
		if strings.HasSuffix(svm.Name, "-mc") {
			continue
		}
		finalSVMs = append(finalSVMs, *svm)
	}

	return &ontap.OntapData{
		Hostname:   p.netappClient.Hostname(),
		DataCenter: p.determineDatacenter(p.netappClient.Hostname()),
		Aggregates: convertedAggregates,
		SVMs:       finalSVMs,
	}, partialErrors
}

// Helper to convert slices to []interface{} for generic embedding
func toInterfaceSlice[T any](input []T) []interface{} {
	result := make([]interface{}, len(input))
	for i, v := range input {
		result[i] = v
	}
	return result
}

func (p *Processor) buildExportPolicyMap(policies []ontap.ExportPolicy) map[int64]ontap.ExportPolicy {
	m := make(map[int64]ontap.ExportPolicy, len(policies))
	for _, pol := range policies {
		m[pol.ID] = pol
	}
	return m
}

// buildCIFSShareMap creates a lookup map from volume UUID to CIFS shares
func (p *Processor) buildCIFSShareMap(shares []ontap.CIFSShare) map[string][]ontap.CIFSShare {
	shareMap := make(map[string][]ontap.CIFSShare, len(shares))
	for _, share := range shares {
		shareMap[share.Volume.UUID] = append(shareMap[share.Volume.UUID], share)
	}
	return shareMap
}

func (p *Processor) buildQTreeMap(qtrees []ontap.QTree, quotas []ontap.Quota, exportPolicyMap map[int64]ontap.ExportPolicy) map[string][]ontap.QTree {
	// First: Map Quotas by VolumeUUID -> QTreeID -> Quota
	quotaMap := make(map[string]map[int64]ontap.Quota, len(quotas))
	for _, quota := range quotas {
		volUUID := quota.Volume.UUID
		if _, ok := quotaMap[volUUID]; !ok {
			quotaMap[volUUID] = make(map[int64]ontap.Quota)
		}
		quotaMap[volUUID][quota.QTree.ID] = quota
	}

	// Second: Assign Quotas to QTrees and group QTrees by Volume
	qtreeMap := make(map[string][]ontap.QTree, len(qtrees))
	for _, qtree := range qtrees {
		// Look up quota safely
		if volQuotas, ok := quotaMap[qtree.Volume.UUID]; ok {
			if quota, found := volQuotas[qtree.ID]; found {
				qtree.Quota = quota
			}
		}
		if pol, ok := exportPolicyMap[qtree.ExportPolicy.ID]; ok {
			qtree.ExportPolicy = pol
		}
		qtreeMap[qtree.Volume.UUID] = append(qtreeMap[qtree.Volume.UUID], qtree)
	}
	return qtreeMap
}

func (p *Processor) wrapError(err error, contextInfo string) error {
	if err != nil {
		return fmt.Errorf("%s: %w", contextInfo, err)
	}
	return nil
}

// determineDatacenter extracts the first number from the name (e.g. SVM name)
// and returns "A20" if odd, "K30" if even. Returns "" if no number found.
func (p *Processor) determineDatacenter(name string) string {
	re := regexp.MustCompile(`\d+`)
	match := re.FindString(name)
	if match == "" {
		return ""
	}

	num, err := strconv.Atoi(match)
	if err != nil {
		return "" // Should not happen given regex match
	}

	if num%2 != 0 {
		return "A20" // Odd
	}
	return "K30" // Even
}

func (p *Processor) mapSnaplock(snaplock ontap.Snaplock) *ontap.SnaplockData {
	out := &ontap.SnaplockData{
		AppendModeEnabled: snaplock.AppendModeEnabled,
		AutocommitPeriod:  snaplock.AutocommitPeriod,
		Type:              snaplock.Type,
	}

	// retention nur setzen, wenn mindestens ein Feld befüllt ist
	if snaplock.Retention.Default != "" || snaplock.Retention.Minimum != "" || snaplock.Retention.Maximum != "" {
		out.Retention = &struct {
			Default string `json:"default,omitempty"`
			Minimum string `json:"minimum,omitempty"`
			Maximum string `json:"maximum,omitempty"`
		}{
			Default: snaplock.Retention.Default,
			Minimum: snaplock.Retention.Minimum,
			Maximum: snaplock.Retention.Maximum,
		}
	}

	return out
}

func NewAggregateData(agg ontap.Aggregate) ontap.AggregateData {
	return ontap.AggregateData{
		UUID:          agg.UUID,
		Name:          agg.Name,
		DiskClass:     agg.BlockStorage.Primary.DiskClass,
		MirrorEnabled: agg.BlockStorage.Mirror.Enabled,
	}
}
