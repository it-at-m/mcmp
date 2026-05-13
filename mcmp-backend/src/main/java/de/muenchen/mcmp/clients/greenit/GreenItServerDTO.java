package de.muenchen.mcmp.clients.greenit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public record GreenItServerDTO(
        @JsonProperty("id") long id,
        @JsonProperty("cloud_name") String cloudName,
        @JsonProperty("vm_name") String vmName,
        @JsonProperty("fqdn") String fqdn,
        @JsonProperty("power_state") String powerState,
        @JsonProperty("memory_mb") int memoryMb,
        @JsonProperty("memory_mb_prev") Integer memoryMbPrev,
        @JsonProperty("memory_mb_change_date") OffsetDateTime memoryMbChangeDate,
        @JsonProperty("memory_mb_change_date_prev") OffsetDateTime memoryMbChangeDatePrev,
        @JsonProperty("num_cpu") int numCpu,
        @JsonProperty("num_cpu_prev") Integer numCpuPrev,
        @JsonProperty("num_cpu_change_date") OffsetDateTime numCpuChangeDate,
        @JsonProperty("num_cpu_change_date_prev") OffsetDateTime numCpuChangeDatePrev,
        @JsonProperty("boot_time") OffsetDateTime bootTime,
        @JsonProperty("green_it_shutdown_change_pending") boolean greenItShutdownChangePending,
        @JsonProperty("green_it_shutdown_change_rejected_date") OffsetDateTime greenItShutdownChangeRejectedDate,
        @JsonProperty("green_it_rightsizing_change_pending") boolean greenItRightsizingChangePending,
        @JsonProperty("green_it_rightsizing_change_rejected_date") OffsetDateTime greenItRightsizingChangeRejectedDate,
        @JsonProperty("metrics") List<GreenItServerMetricsDTO> metrics
) {
}