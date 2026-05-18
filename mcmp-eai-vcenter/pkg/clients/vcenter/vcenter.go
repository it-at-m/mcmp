// SPDX-FileCopyrightText: 2023 Landeshauptstadt München | it@M
//
// SPDX-License-Identifier: MIT

package vcenter

import (
	"context"
	"fmt"
	"math"
	"net/url"
	"runtime/debug"
	"sort"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/vmware/govmomi/object"
	"github.com/vmware/govmomi/performance"
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

func (c *Client) ReadVcenterData() (map[int32]string, []mo.VirtualMachine, []mo.HostSystem, []mo.ComputeResource, []mo.DistributedVirtualPortgroup, error) {
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
		fmt.Printf("m.CreateContainerView VirtualMachine / Error : %v\n", err)
		return nil, nil, nil, nil, nil, err
	}
	defer safeDestroyView(c.context, v1)
	var vms []mo.VirtualMachine
	err = v1.Retrieve(c.context, []string{"VirtualMachine"}, []string{"guest", "config", "runtime", "summary", "snapshot", "layoutEx", "overallStatus", "configStatus"}, &vms)
	if err != nil {
		fmt.Printf("m.Retrieve VirtualMachine / Error : %#v\n", err)
		return nil, nil, nil, nil, nil, err
	}

	v2, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"HostSystem"}, true)
	if err != nil {
		fmt.Printf("m.CreateContainerView HostSystem / Error : %v\n", err)
		return nil, nil, nil, nil, nil, err
	}
	defer safeDestroyView(c.context, v2)
	var hosts []mo.HostSystem
	err = v2.Retrieve(c.context, []string{"HostSystem"}, []string{"name", "vm"}, &hosts)
	if err != nil {
		fmt.Printf("m.Retrieve HostSystem / Error : %v\n", err)
		return nil, nil, nil, nil, nil, err
	}

	v3, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"ClusterComputeResource"}, true)
	if err != nil {
		fmt.Printf("m.CreateContainerView ClusterComputeResource / Error : %v\n", err)
		return nil, nil, nil, nil, nil, err
	}
	defer safeDestroyView(c.context, v3)
	var cluster []mo.ComputeResource
	err = v3.Retrieve(c.context, []string{"ClusterComputeResource"}, []string{"name", "host"}, &cluster)
	if err != nil {
		fmt.Printf("m.Retrieve ClusterComputeResource / Error : %v\n", err)
		return nil, nil, nil, nil, nil, err
	}

	v4, err := m.CreateContainerView(c.context, c.client.ServiceContent.RootFolder, []string{"DistributedVirtualPortgroup"}, true)
	if err != nil {
		fmt.Printf("m.CreateContainerView DistributedVirtualPortgroup / Error : %v\n", err)
		return nil, nil, nil, nil, nil, err
	}
	defer safeDestroyView(c.context, v4)
	var dvp []mo.DistributedVirtualPortgroup
	err = v4.Retrieve(c.context, []string{"DistributedVirtualPortgroup"}, []string{"name", "key", "config"}, &dvp)
	if err != nil {
		fmt.Printf("m.Retrieve DistributedVirtualPortgroup / Error : %v\n", err)
		return nil, nil, nil, nil, nil, err
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

func safeDestroyView(ctx context.Context, v *view.ContainerView) {
	if err := v.Destroy(ctx); err != nil {
		// Loggen oder anderweitiges Behandeln des Fehlers
		fmt.Printf("Fehler beim Zerstören der View: %v\n", err)
	}
}
