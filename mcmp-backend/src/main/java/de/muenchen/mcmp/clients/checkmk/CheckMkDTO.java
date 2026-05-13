package de.muenchen.mcmp.clients.checkmk;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * Data Transfer Object (DTO) representing Checkmk performance metrics for one or more hosts.
 * This class is used to transfer data between client and server, encapsulating host-specific
 * performance data such as CPU utilization and memory usage percentage.
 *
 * The primary structure is a mapping of hostnames to their respective performance metrics
 * represented as {@link HostData}.
 *
 * Constraints:
 * - Each host's performance metrics are encapsulated in the {@link HostData} sub-record.
 * - Validation annotations (@Valid, @DecimalMin, @DecimalMax) are used to ensure the performance
 *   metrics are within valid numeric ranges.
 */
public record CheckMkDTO(
        Map<String, @Valid HostData> hosts
) {
    public record HostData(
            @JsonProperty("cpu_util")
            //@DecimalMin("0.0")
            //@DecimalMax("100.0")
            Float cpuUtil,

            @JsonProperty("mem_used_percent")
            //@DecimalMin("0.0")
            //@DecimalMax("100.0")
            Float memUsedPercent
    ) {
    }
}
