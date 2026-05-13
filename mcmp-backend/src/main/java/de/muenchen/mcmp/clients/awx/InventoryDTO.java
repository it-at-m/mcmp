package de.muenchen.mcmp.clients.awx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record InventoryDTO(
        @JsonProperty("linux_hosts")
        List<InventoryHostDTO> linuxHosts,
        @JsonProperty("windows_hosts")
        List<InventoryHostDTO> windowsHosts,
        @JsonProperty("windows_maintenance_mode_hosts")
        List<InventoryHostDTO> windowsMaintenanceModeHosts
) {
    @Builder
    public record InventoryHostDTO(
            Instant created,
            String fqdn,
            String user,
            @JsonProperty("valid_until")
            String validUntil
    ) {}
}
