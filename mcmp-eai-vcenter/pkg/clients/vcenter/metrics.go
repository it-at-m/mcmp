package vcenter

const (

	// CPU Metriken
	CPUUsageAverage    MetricName = "cpu.usage.average"    // CPU-Auslastung durchschnittlich (%)
	CPUUsageMHzAverage MetricName = "cpu.usagemhz.average" // CPU-Auslastung durchschnittlich (MHz)
	CPUReadySummation  MetricName = "cpu.ready.summation"  // CPU-Ready Zeit summiert (ms)
	CPUCostopSummation MetricName = "cpu.costop.summation" // CPU Co-Stop Zeit summiert (ms)
	CPUWaitSummation   MetricName = "cpu.wait.summation"   // CPU Wait Zeit summiert (ms)
	CPUDemandAverage   MetricName = "cpu.demand.average"   // echte CPU-Nachfrage (MHz)

	// Speicher Metriken
	MemUsageAverage       MetricName = "mem.usage.average"       // Arbeitsspeicher Auslastung durchschnittlich (%)
	MemActiveAverage      MetricName = "mem.active.average"      // Durchschnittlich aktiv genutzter Arbeitsspeicher (KB)
	MemVMMemctlAverage    MetricName = "mem.vmmemctl.average"    // Durchschnittliche Memory Ballooning Rate (KB)
	MemSwapInRateAverage  MetricName = "mem.swapinRate.average"  // Durchschnittliche Swap-In Rate des RAMs (KB/s)
	MemSwapOutRateAverage MetricName = "mem.swapoutRate.average" // Durchschnittliche Swap-Out Rate des RAMs (KB/s)
	MemConsumedAverage    MetricName = "mem.consumed.average"    // Vom VMware Host zugeteilter RAM-Durchschnittsverbrauch (KB)
	MemGrantedAverage     MetricName = "mem.granted.average"     // zugesicherter Speicher (KB)
	MemSwapUsedAverage    MetricName = "mem.swapused.average"    // Durchschnittliche Menge an verwendetem Swap-Speicher (KB)

	// Disk Metriken
	DiskUsageAverage                MetricName = "disk.usage.average"                    // Durchschnittliche Disk-Nutzungsrate (KB/s)
	DiskMaxTotalLatencyLatest       MetricName = "disk.maxTotalLatency.latest"           // max. Storage-Latenz (ms)
	VirtualDiskTotalReadLatencyAvg  MetricName = "virtualDisk.totalReadLatency.average"  // durchschnittliche virtuelle Disk-Leselatenz (ms)
	VirtualDiskTotalWriteLatencyAvg MetricName = "virtualDisk.totalWriteLatency.average" // durchschnittliche virtuelle Disk-Schreiblatenz (ms)

	// Netzwerk Metriken
	NetPacketsRxSummation MetricName = "net.packetsRx.summation" // Netzwerk Pakete Empfang (Anzahl)
	NetPacketsTxSummation MetricName = "net.packetsTx.summation" // Netzwerk Pakete Versand (Anzahl)
	NetUsageAverage       MetricName = "net.usage.average"       // Durchschnittliche Netzwerk-Nutzungsrate (KB/s)
)
