package vcenter

import (
	"context"
	"errors"
	"fmt"
	"log"
	"math"
	"net/url"
	"runtime/debug"
	"sort"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/vmware/govmomi/object"
	"github.com/vmware/govmomi/performance"
	"github.com/vmware/govmomi/property"
	"github.com/vmware/govmomi/session/cache"
	"github.com/vmware/govmomi/vapi/rest"
	"github.com/vmware/govmomi/vapi/tags"
	"github.com/vmware/govmomi/view"
	"github.com/vmware/govmomi/vim25"
	"github.com/vmware/govmomi/vim25/mo"
	"github.com/vmware/govmomi/vim25/soap"
	"github.com/vmware/govmomi/vim25/types"
)

func (m IntervalMetrics) ConvertToPercent() IntervalMetrics {
	return IntervalMetrics{
		Min:    m.Min / 100,
		Q1:     m.Q1 / 100,
		Median: m.Median / 100,
		Q3:     m.Q3 / 100,
		Max:    m.Max / 100,
		Avg:    m.Avg / 100,
	}
}

func New(fqdn string, username string, password string) (*Client, error) {
	c := new(Client)
	c.fqdn = fqdn
	escapedUsername := url.QueryEscape(username)
	escapedPassword := url.QueryEscape(password)
	sdkUrl := fmt.Sprintf("https://%s:%s@%s/sdk", escapedUsername, escapedPassword, fqdn)
	var err error
	c.url, err = soap.ParseURL(sdkUrl)
	if err != nil {
		return nil, err
	}
	c.session = &cache.Session{
		URL:      c.url,
		Insecure: true,
	}
	c.client = new(vim25.Client)
	c.context = context.Background()
	c.DebugLogger = logging.NewDebugLogger(nil)
	return c, nil
}

func (c *Client) Login() error {
	return c.session.Login(c.context, c.client, nil)
}

func (c *Client) Logout() error {
	return c.session.Logout(c.context, c.client)
}

func (c *Client) GetVCenterHostStatusSnMap() (hostMap map[string]VCenterHostStatus, err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("recovered in GetVCenterHostStatusSnMap(). Error: %#v", r)
			debug.PrintStack()
		}
	}()
	m := view.NewManager(c.client)
	v, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"HostSystem"}, true)
	if err != nil {
		return nil, err
	}
	defer safeDestroyView(c.context, v)
	var hss []mo.HostSystem
	err = v.Retrieve(c.context, []string{"HostSystem"}, []string{"summary"}, &hss)
	if err != nil {
		return nil, err
	}
	hostMap = make(map[string]VCenterHostStatus)
	for _, hs := range hss {
		host := VCenterHostStatus{}
		if hs.Summary.Hardware != nil {
			for _, info := range hs.Summary.Hardware.OtherIdentifyingInfo {
				elementDescription := info.IdentifierType.GetElementDescription()
				if elementDescription != nil && elementDescription.Key == "SerialNumberTag" {
					host.SN = info.IdentifierValue
					break
				}
			}
		}
		if len(host.SN) > 0 {
			host.Hostname = hs.Summary.Config.Name
			host.OverallStatus = string(hs.Summary.OverallStatus)
			product := hs.Summary.Config.Product
			if product != nil {
				host.OS = hs.Summary.Config.Product.FullName
			}
			runtime := hs.Summary.Runtime
			if runtime != nil {
				host.PowerState = string(hs.Summary.Runtime.PowerState)
				host.InMaintenanceMode = hs.Summary.Runtime.InMaintenanceMode
				host.ConnectionState = string(hs.Summary.Runtime.ConnectionState)
				host.BootTime = hs.Summary.Runtime.BootTime
			}
			hostMap[host.SN] = host
		}
	}
	return hostMap, nil
}

func (c *Client) ReadVMs() (template int, vmPoweredOn int, vmPoweredOff int, vmSuspended int, vmNumCPUs int, vmMemoryMB int, err error) {
	m := view.NewManager(c.client)
	v, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"VirtualMachine"}, true)
	if err != nil {
		return 0, 0, 0, 0, 0, 0, err
	}
	defer safeDestroyView(c.context, v)
	var vms []mo.VirtualMachine
	err = v.Retrieve(c.context, []string{"VirtualMachine"}, []string{"name", "config", "runtime", "summary"}, &vms)
	if err != nil {
		return 0, 0, 0, 0, 0, 0, err
	}
	for _, vm := range vms {
		if vm.Summary.Config.Template {
			template++
		} else {
			switch vm.Runtime.PowerState {
			case types.VirtualMachinePowerStatePoweredOn:
				vmPoweredOn++
			case types.VirtualMachinePowerStatePoweredOff:
				vmPoweredOff++
			case types.VirtualMachinePowerStateSuspended:
				vmSuspended++
			}
			if vm.Config != nil {
				vmNumCPUs += int(vm.Config.Hardware.NumCPU)
				vmMemoryMB += int(vm.Config.Hardware.MemoryMB)
			}
		}
	}
	return template, vmPoweredOn, vmPoweredOff, vmSuspended, vmNumCPUs, vmMemoryMB, nil
}

func (c *Client) ReadServerGUI() (serverGUI string) {
	return c.client.ServiceContent.About.InstanceUuid
}

func (c *Client) ReadVirtualMachineStats() ([]VmPerfData, error) {
	m := view.NewManager(c.client)
	v, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"VirtualMachine"}, true)
	if err != nil {
		return nil, err
	}
	defer safeDestroyView(c.context, v)

	var vms []mo.VirtualMachine
	err = v.Retrieve(c.context, []string{"VirtualMachine"}, []string{"name", "config.uuid", "config.hardware", "summary.runtime"}, &vms)
	if err != nil {
		return nil, err
	}

	// Performance Manager abrufen
	perfManager := performance.NewManager(c.client)
	endTime := time.Now().Truncate(30 * time.Minute).Add(-time.Second) // rundet z.B. 14:37 auf 14:29:59
	startTime := endTime.Add(-30 * time.Minute)                        // z.B. 14:29:59 auf 13:59:59, damit der Wert für 14:00:00 von der vSphere API mitgeliefert wird
	counterNames := []MetricName{
		CPUUsageAverage,
		CPUUsageMHzAverage,
		CPUReadySummation,
		CPUCostopSummation,
		CPUWaitSummation,
		CPUDemandAverage,
		MemUsageAverage,
		MemActiveAverage,
		MemVMMemctlAverage,
		MemSwapInRateAverage,
		MemSwapOutRateAverage,
		MemConsumedAverage,
		MemGrantedAverage,
		MemSwapUsedAverage,
		DiskUsageAverage,
		DiskMaxTotalLatencyLatest,
		VirtualDiskTotalReadLatencyAvg,
		VirtualDiskTotalWriteLatencyAvg,
		NetPacketsRxSummation,
		NetPacketsTxSummation,
		NetUsageAverage,
	}
	vmStatsData, err := c.getPerfData(
		vms,
		perfManager,
		counterNames,
		startTime,
		endTime,
		20,             // 20-Sekunden-Intervall = Echtzeit (nur 1 Stunde verfügbar)
		30*time.Minute, // 30min zusammenfassen
	)
	if err != nil {
		return nil, err
	}
	return vmStatsData, nil
}

func (c *Client) getPerfData(vms []mo.VirtualMachine, perfManager *performance.Manager, counterNames []MetricName, startTime, endTime time.Time, intervalSeconds int32, intervalDuration time.Duration) ([]VmPerfData, error) {
	c.DebugPrintf("Perf-Daten für %d VMs zwischen %v und %v abfragen.", len(vms), startTime, endTime)

	// Performance-Counter abrufen
	counters, err := perfManager.CounterInfoByName(c.context)
	if err != nil {
		return nil, fmt.Errorf("Counter-Infos konnten nicht geladen werden: %v", err)
	}

	// Counter überprüfen und IDs sammeln
	counterIds := make(map[MetricName]int32)
	for _, counterName := range counterNames {
		counter, ok := counters[string(counterName)]
		if !ok {
			return nil, fmt.Errorf("counter %s existiert nicht", counterName)
		}
		counterIds[counterName] = counter.Key
	}

	resultMetrics := make(map[string]map[time.Time]map[MetricName][]int64)

	// Alle Counter nacheinander abfragen
	for counterName, counterKey := range counterIds {
		specs := createPerfQuerySpecs(vms, counterKey, intervalSeconds, startTime, endTime)
		results, err := perfManager.Query(c.context, specs)
		if err != nil {
			return nil, fmt.Errorf("fehler bei Abfrage für Counter %s: %v", counterName, err)
		}

		processPerfDataGeneral(results, counterName, resultMetrics, intervalDuration, counterKey)
	}

	type vmInfo struct {
		Name           string
		UUID           string
		NumCores       int32
		MemoryMB       int32
		MaxCpuUsage    int32
		MaxMemoryUsage int32
		MemoryOverhead int64
		PowerState     string
	}

	vmRefToInfo := make(map[string]vmInfo)
	for _, vm := range vms {
		vmRefToInfo[vm.Reference().String()] = vmInfo{
			Name:           vm.Name,
			UUID:           vm.Config.Uuid,
			NumCores:       vm.Config.Hardware.NumCPU,
			MemoryMB:       vm.Config.Hardware.MemoryMB,
			MaxCpuUsage:    vm.Summary.Runtime.MaxCpuUsage,
			MaxMemoryUsage: vm.Summary.Runtime.MaxMemoryUsage,
			MemoryOverhead: vm.Summary.Runtime.MemoryOverhead,
			PowerState:     string(vm.Summary.Runtime.PowerState),
		}
	}

	var vmPerfDataList []VmPerfData

	for vmRef, timestamps := range resultMetrics {
		vmData, found := vmRefToInfo[vmRef]
		if !found {
			continue
		}
		for timestamp, metricsMap := range timestamps {
			vmMetrics := make(map[MetricName]IntervalMetrics)
			validMetrics := true

			for metricName, values := range metricsMap {
				if len(values) == 0 {
					validMetrics = false
					break
				}
				vmMetrics[metricName] = calcMetrics(values)
			}

			if !validMetrics {
				continue
			}

			vmPerfDataList = append(vmPerfDataList, VmPerfData{
				Timestamp:      timestamp,
				VMName:         vmData.Name,
				UUID:           vmData.UUID,
				NumCores:       vmData.NumCores,
				MemoryMB:       vmData.MemoryMB,
				MaxCpuUsage:    vmData.MaxCpuUsage,
				MaxMemoryUsage: vmData.MaxMemoryUsage,
				MemoryOverhead: vmData.MemoryOverhead,
				PowerState:     vmData.PowerState,
				Metrics:        vmMetrics,
			})
		}
	}

	return vmPerfDataList, nil
}

// processPerfDataGeneral verarbeitet die Perf-Ergebnisse allgemein.
func processPerfDataGeneral(
	results []types.BasePerfEntityMetricBase,
	counterName MetricName,
	resultMetrics map[string]map[time.Time]map[MetricName][]int64,
	intervalDuration time.Duration,
	counterKey int32,
) {
	for _, entityMetricBase := range results {
		pem, ok := entityMetricBase.(*types.PerfEntityMetric)
		if !ok {
			continue
		}

		vmRef := pem.Entity.String()

		for _, series := range pem.Value {
			intSeries, ok := series.(*types.PerfMetricIntSeries)
			if !ok || intSeries.Id.CounterId != counterKey {
				continue
			}

			groupedValues := groupTimeValues(intSeries, pem.SampleInfo, intervalDuration)

			for blockStart, vals := range groupedValues {
				if _, exists := resultMetrics[vmRef]; !exists {
					resultMetrics[vmRef] = make(map[time.Time]map[MetricName][]int64)
				}
				if _, exists := resultMetrics[vmRef][blockStart]; !exists {
					resultMetrics[vmRef][blockStart] = make(map[MetricName][]int64)
				}

				resultMetrics[vmRef][blockStart][counterName] = vals
			}
		}
	}
}

func groupTimeValues(series *types.PerfMetricIntSeries, sampleInfo []types.PerfSampleInfo, blockDuration time.Duration) map[time.Time][]int64 {
	grouped := make(map[time.Time][]int64)
	for i, value := range series.Value {
		ts := truncateToNextInterval(sampleInfo[i].Timestamp, blockDuration)
		grouped[ts] = append(grouped[ts], value)
	}
	return grouped
}

func truncateToNextInterval(t time.Time, d time.Duration) time.Time {
	return t.Truncate(d).Add(d).Local()
}

func createPerfQuerySpecs(vms []mo.VirtualMachine, counter int32, intervalSeconds int32, startTime, endTime time.Time) []types.PerfQuerySpec {
	var specs []types.PerfQuerySpec
	startTimeUTC := startTime.UTC()
	endTimeUTC := endTime.UTC()
	for _, vm := range vms {
		specs = append(specs, types.PerfQuerySpec{
			Entity: vm.Reference(),
			MetricId: []types.PerfMetricId{
				{CounterId: counter, Instance: "*"},
			},
			IntervalId: intervalSeconds,
			StartTime:  &startTimeUTC,
			EndTime:    &endTimeUTC,
		})
	}
	return specs
}

func calcMetrics(values []int64) IntervalMetrics {
	sort.Slice(values, func(i, j int) bool { return values[i] < values[j] })
	sum := int64(0)
	for _, v := range values {
		sum += v
	}
	n := len(values)
	return IntervalMetrics{
		Min:    float64(values[0]),
		Q1:     getQuantile(values, 0.25),
		Median: getQuantile(values, 0.50),
		Q3:     getQuantile(values, 0.75),
		Max:    float64(values[n-1]),
		Avg:    float64(sum) / float64(n),
	}
}

// getQuantile berechnet das Quantil q (0 <= q <= 1) mit linearer Interpolation
func getQuantile(sortedValues []int64, quantile float64) float64 {
	n := len(sortedValues)
	if n == 0 {
		return 0
	}
	pos := quantile * float64(n-1)
	indexLower := int(math.Floor(pos))
	indexUpper := int(math.Ceil(pos))

	if indexLower == indexUpper {
		return float64(sortedValues[indexLower])
	}
	weight := pos - float64(indexLower)
	lowerVal := float64(sortedValues[indexLower])
	upperVal := float64(sortedValues[indexUpper])
	return lowerVal + weight*(upperVal-lowerVal)
}

// ReadVcenterData retrieves vCenter inventory data including custom field definitions, virtual machines, hosts, clusters, and distributed virtual portgroups.
// Virtual machines whose references appear in migratedVMs are excluded from the returned VM list.
// It returns the custom field ID-to-name mapping and the retrieved inventory objects, or an error on failure.
func (c *Client) ReadVcenterData(migratedVMs map[string]struct{}) (map[int32]string, []mo.VirtualMachine, []mo.HostSystem, []mo.ComputeResource, []mo.DistributedVirtualPortgroup, error) {
	// Custom Fields Definitionen laden (ID -> Name Mapping)
	customFields := make(map[int32]string)
	cfm, err := object.GetCustomFieldsManager(c.client)
	if err == nil {
		// Fehler hier ignorieren wir "soft", falls Manager nicht geladen werden kann, läuft der Rest weiter
		fields, err := cfm.Field(c.context)
		if err == nil {
			for _, f := range fields {
				customFields[f.Key] = f.Name
			}
		}
	} else {
		fmt.Printf("Warnung: Konnte CustomFieldsManager nicht laden: %v\n", err)
	}

	m := view.NewManager(c.client)

	v1, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"VirtualMachine"}, true)
	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("create ContainerView for VirtualMachine", err)
	}
	defer safeDestroyView(c.context, v1)
	vms, err := c.retrieveVMs(v1, []string{"guest", "config", "runtime", "summary", "snapshot", "layoutEx", "overallStatus", "configStatus"})

	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("retrieve VirtualMachine", err)
	}

	// Remove VMs tagged with "Migrated" from the list
	if len(migratedVMs) > 0 {
		filteredVMs := make([]mo.VirtualMachine, 0, len(vms))
		for _, vm := range vms {
			if _, migrated := migratedVMs[vm.Reference().Value]; migrated {
				c.DebugPrintf("VM %s (%s) is ignored because it has the 'Migrated' tag.\n", vm.Summary.Config.Name, vm.Reference().Value)
				continue
			}
			filteredVMs = append(filteredVMs, vm)
		}
		c.DebugPrintf("%d migrated VM(s) filtered out of the VM list.\n", len(vms)-len(filteredVMs))
		vms = filteredVMs
	}

	v2, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"HostSystem"}, true)
	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("create ContainerView for HostSystem", err)
	}
	defer safeDestroyView(c.context, v2)
	var hosts []mo.HostSystem
	err = v2.Retrieve(c.context, []string{"HostSystem"}, []string{"name", "vm"}, &hosts)
	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("retrieve HostSystem", err)
	}

	v3, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"ClusterComputeResource"}, true)
	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("create ContainerView for ClusterComputeResource", err)
	}
	defer safeDestroyView(c.context, v3)
	var cluster []mo.ComputeResource
	err = v3.Retrieve(c.context, []string{"ClusterComputeResource"}, []string{"name", "host"}, &cluster)
	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("retrieve ClusterComputeResource", err)
	}

	v4, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"DistributedVirtualPortgroup"}, true)
	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("create ContainerView for DistributedVirtualPortgroup", err)
	}
	defer safeDestroyView(c.context, v4)
	var dvp []mo.DistributedVirtualPortgroup
	err = v4.Retrieve(c.context, []string{"DistributedVirtualPortgroup"}, []string{"name", "key", "config"}, &dvp)
	if err != nil {
		return nil, nil, nil, nil, nil, wrapVimError("retrieve DistributedVirtualPortgroup", err)
	}

	return customFields, vms, hosts, cluster, dvp, nil
}

func (c *Client) ReadDistributedVirtualPortgroups() ([]mo.DistributedVirtualPortgroup, error) {
	m := view.NewManager(c.client)
	v, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"DistributedVirtualPortgroup"}, true)
	if err != nil {
		return nil, err
	}
	defer safeDestroyView(c.context, v)
	var dvp []mo.DistributedVirtualPortgroup
	err = v.Retrieve(c.context, []string{"DistributedVirtualPortgroup"}, []string{"name", "key", "config"}, &dvp)
	if err != nil {
		return nil, err
	}
	return dvp, nil
}

func (c *Client) ReadHosts() (hostsInMaintenanceMode int, hostsPoweredOn int, hostsPoweredOff int, hostsStandBy int, hostsUnknown int, hostsNumCPUs int, hostsMemoryGB int, err error) {
	m := view.NewManager(c.client)
	v, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"HostSystem"}, true)
	if err != nil {
		return 0, 0, 0, 0, 0, 0, 0, err
	}
	defer safeDestroyView(c.context, v)
	var hss []mo.HostSystem
	err = v.Retrieve(c.context, []string{"HostSystem"}, []string{"runtime", "summary"}, &hss)
	if err != nil {
		return 0, 0, 0, 0, 0, 0, 0, err
	}
	for _, hs := range hss {
		switch hs.Runtime.PowerState {
		case types.HostSystemPowerStatePoweredOn:
			if hs.Runtime.InMaintenanceMode {
				hostsInMaintenanceMode++
			} else {
				hostsPoweredOn++
			}
		case types.HostSystemPowerStatePoweredOff:
			hostsPoweredOff++
		case types.HostSystemPowerStateStandBy:
			hostsStandBy++
		case types.HostSystemPowerStateUnknown:
			hostsUnknown++
		}
		if hs.Summary.Hardware != nil {
			hostsNumCPUs += int(hs.Summary.Hardware.NumCpuCores)
			hostsMemoryGB += int((hs.Summary.Hardware.MemorySize/1024/1024/1024/8 + 1) * 8)
		}
	}
	return hostsInMaintenanceMode, hostsPoweredOn, hostsPoweredOff, hostsStandBy, hostsUnknown, hostsNumCPUs, hostsMemoryGB, nil
}

func (c *Client) ReadClusters() (clusters int, err error) {
	m := view.NewManager(c.client)
	v, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"ClusterComputeResource"}, true)
	if err != nil {
		return 0, err
	}
	defer safeDestroyView(c.context, v)

	var ccrs []mo.ClusterComputeResource
	err = v.Retrieve(c.context, []string{"ClusterComputeResource"}, []string{"summary"}, &ccrs)
	if err != nil {
		return 0, err
	}
	return len(ccrs), nil
}

// ReadTagsBulk efficiently retrieves all tags for all VMs in the vCenter.
// Instead of querying tags for each VM individually (which causes an N+1 performance issue),
// this function fetches all available tags and their attached objects in bulk.
//
// Returns:
//   - map[string][]TagInfo: A map where the key is the VM Managed Object Reference (MoRef) ID (e.g., "vm-123")
//     and the value is a slice of TagInfo structs containing tag details.
//   - error: An error object if the operation fails.
func (c *Client) ReadTagsBulk() (map[string][]TagInfo, error) {
	// 1. Create a REST Client based on the existing SOAP Client.
	// The REST client is required for the Tagging API.
	restClient := rest.NewClient(c.client)

	// Login to the REST API using the credentials from the SOAP client URL.
	err := restClient.Login(c.context, c.url.User)
	if err != nil {
		return nil, fmt.Errorf("REST login failed: %w", err)
	}
	defer restClient.Logout(c.context)

	manager := tags.NewManager(restClient)

	// 2. Fetch all tag category IDs.
	categoryIDs, err := manager.ListCategories(c.context)
	if err != nil {
		return nil, fmt.Errorf("failed to list categories: %w", err)
	}

	// Create a lookup map for categories (ID -> Category Object)
	// We have to fetch each category individually as there is no bulk getter.
	categoryMap := make(map[string]tags.Category)
	for _, id := range categoryIDs {
		cat, err := manager.GetCategory(c.context, id)
		if err != nil {
			c.DebugPrintf("Warning: failed to get category details for %s: %v", id, err)
			continue
		}
		if cat != nil {
			categoryMap[cat.ID] = *cat
		}
	}

	// 3. Fetch all tag IDs available in the vCenter.
	tagIDs, err := manager.ListTags(c.context)
	if err != nil {
		return nil, fmt.Errorf("failed to list tags: %w", err)
	}

	if len(tagIDs) == 0 {
		return make(map[string][]TagInfo), nil
	}

	// 4. Inverted Lookup: For each tag, find which objects it is attached to.
	// We build a map: VM-ID -> List of Tags.
	vmTagsMap := make(map[string][]TagInfo)

	for _, tagID := range tagIDs {
		// Fetch tag details
		tag, err := manager.GetTag(c.context, tagID)
		if err != nil {
			c.DebugPrintf("Warning: failed to get tag details for %s: %v", tagID, err)
			continue
		}

		// List all objects attached to this specific tag.
		attachedObjects, err := manager.ListAttachedObjects(c.context, tag.ID)
		if err != nil {
			// Log a warning if a single tag fails, but continue with others.
			c.DebugPrintf("Warning: Could not list attached objects for tag %s: %v", tag.Name, err)
			continue
		}

		// Resolve category name.
		catName := ""
		if cat, ok := categoryMap[tag.CategoryID]; ok {
			catName = cat.Name
		}

		info := TagInfo{
			Name:         tag.Name,
			CategoryName: catName,
			Description:  tag.Description,
		}

		// Iterate over objects attached to this tag.
		for _, objRef := range attachedObjects {
			// objRef is an interface (mo.Reference), we need to get the underlying struct
			ref := objRef.Reference()

			// We are only interested in VirtualMachines.
			if ref.Type == "VirtualMachine" {
				vmID := ref.Value // e.g., "vm-123"
				vmTagsMap[vmID] = append(vmTagsMap[vmID], info)
			}
		}
	}

	return vmTagsMap, nil
}

// GetVMIDsByTag retrieves a set of VM MoRef IDs (e.g. "vm-1317297") that have the specified tag attached.
// This is done via direct tag name lookup, avoiding expensive iterations over all tags in the vCenter.
func (c *Client) GetVMIDsByTag(tagName string) (map[string]struct{}, error) {
	restClient := rest.NewClient(c.client)

	err := restClient.Login(c.context, c.url.User)
	if err != nil {
		return nil, fmt.Errorf("REST login failed: %w", err)
	}
	defer restClient.Logout(c.context)

	manager := tags.NewManager(restClient)

	// Direct lookup by tag name (1 API call instead of fetching all tags individually)
	tag, err := manager.GetTag(c.context, tagName)
	if err != nil {
		// A tag that does not exist in vCenter is not an error for the caller:
		// it simply means that no VM is tagged with it.
		if isHTTPNotFound(err) {
			c.DebugPrintf("Tag %q does not exist in vCenter; no VMs are tagged with it.", tagName)
			return make(map[string]struct{}), nil
		}
		return nil, fmt.Errorf("failed to get tag %q: %w", tagName, err)
	}
	if tag == nil {
		return make(map[string]struct{}), nil
	}

	attachedObjects, err := manager.ListAttachedObjects(c.context, tag.ID)
	if err != nil {
		return nil, fmt.Errorf("could not list attached objects for tag %q: %w", tagName, err)
	}

	vmIDSet := make(map[string]struct{}, len(attachedObjects))
	for _, objRef := range attachedObjects {
		ref := objRef.Reference()
		if ref.Type == "VirtualMachine" {
			vmIDSet[ref.Value] = struct{}{}
		}
	}

	return vmIDSet, nil
}

// GetVirtualApplianceVMIDs returns a set of VM MoRef IDs tagged "Virtual Appliance", or an error if the lookup fails.
func (c *Client) GetVirtualApplianceVMIDs() (map[string]struct{}, error) {
	return c.GetVMIDsByTag("Virtual Appliance")
}

// GetMigratedVMIDs returns the set of VM MoRef IDs that have the "Migrated" tag attached.
// It returns an error if the tag lookup fails.
func (c *Client) GetMigratedVMIDs() (map[string]struct{}, error) {
	return c.GetVMIDsByTag("Migrated")
}

func safeDestroyView(ctx context.Context, v *view.ContainerView) {
	if err := v.Destroy(ctx); err != nil {
		// Loggen oder anderweitiges Behandeln des Fehlers
		fmt.Printf("Fehler beim Zerstören der View: %v\n", err)
	}
}

// isNoPermission reports whether err indicates a vSphere NoPermission fault, including wrapped faults or fault text.
func isNoPermission(err error) bool {
	if err == nil {
		return false
	}

	// govmomi wraps Vim faults in a wrapper; neither *types.NoPermission nor
	// *soap.Fault implements error. Therefore walk the error chain and check
	// every level via soap.IsVimFault.
	for e := err; e != nil; e = errors.Unwrap(e) {
		if soap.IsVimFault(e) {
			if _, ok := soap.ToVimFault(e).(*types.NoPermission); ok {
				return true
			}
		}
	}

	// Fallback for errors that only carry the fault as text.
	return strings.Contains(err.Error(), "NoPermission")
}

// retrieveVMs retrieves virtual machines from the container view v using the requested properties.
// It performs a bulk retrieval and falls back to individual requests when access is denied.
// Virtual machines without read permission are skipped during fallback; other retrieval errors are returned.
// The retrieved virtual machines and any fatal error are returned.
func (c *Client) retrieveVMs(v *view.ContainerView, props []string) ([]mo.VirtualMachine, error) {
	pc := property.DefaultCollector(c.client)

	var cv mo.ContainerView
	if err := pc.RetrieveOne(c.context, v.Reference(), []string{"view"}, &cv); err != nil {
		return nil, fmt.Errorf("ContainerView-Refs konnten nicht gelesen werden: %w", err)
	}
	refs := cv.View
	if len(refs) == 0 {
		return []mo.VirtualMachine{}, nil
	}

	var vms []mo.VirtualMachine
	err := pc.Retrieve(c.context, refs, props, &vms)
	if err == nil {
		return vms, nil
	}
	if !isNoPermission(err) {
		return nil, err
	}

	// Fallback
	log.Printf("Sammel-Retrieve fehlgeschlagen (%s) - wechsle auf Einzelabfragen.", formatVimError(err))

	skipped := 0
	vms = make([]mo.VirtualMachine, 0, len(refs))
	for _, ref := range refs {
		var vm mo.VirtualMachine
		if err := pc.RetrieveOne(c.context, ref, props, &vm); err != nil {
			if isNoPermission(err) {
				skipped++
				log.Printf("VM %s wird übersprungen (keine Berechtigung): %s", ref.Value, formatVimError(err))
				continue
			}
			return nil, err
		}
		vms = append(vms, vm)
	}
	log.Printf("%d VM(s) ohne Leserechte übersprungen.", skipped)
	return vms, nil
}

func (e *vimError) Error() string {
	return fmt.Sprintf("%s: %s", e.operation, formatVimError(e.err))
}

func (e *vimError) Unwrap() error {
	return e.err
}

func wrapVimError(operation string, err error) error {
	if err == nil {
		return nil
	}
	return &vimError{operation: operation, err: err}
}

func formatVimError(err error) string {
	if err == nil {
		return ""
	}

	if soap.IsVimFault(err) {
		fault := soap.ToVimFault(err)
		switch f := fault.(type) {
		case *types.NoPermission:
			objStr := "unknown"
			if f.Object != nil {
				objStr = fmt.Sprintf("%s:%s", f.Object.Type, f.Object.Value)
			}
			return fmt.Sprintf("NoPermission (Privilege: %q, Object: %s)", f.PrivilegeId, objStr)
		default:
			return fmt.Sprintf("VimFault (%T): %+v", f, f)
		}
	}

	return err.Error()
}

// isHTTPNotFound reports whether err is a "404 Not Found" response of the vCenter REST API.
// The govmomi vapi client does not return a typed error for non-200 responses; it only embeds
// the HTTP status line into the error message (e.g. "GET https://...: 404 Not Found").
func isHTTPNotFound(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "404")
}
