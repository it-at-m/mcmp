package de.muenchen.mcmp.clients.greenit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record GreenItServerMetricsDTO(
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("cpu_util") Float cpuUtil,
        @JsonProperty("mem_used_percent") Float memUsedPercent
) {
}
