package de.muenchen.mcmp.clients.loadbalancer;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * DTO representing the full BIG-IP configuration payload sent by the EAI.
 */
public record LoadBalancerDTO(

        @NotNull
        @JsonProperty("virtualServers")
        Map<String, @Valid VirtualServerDTO> virtualServers,

        @NotNull
        @JsonProperty("pools")
        Map<String, @Valid PoolDTO> pools
) {

    public record VirtualServerDTO(
            @NotNull List<String> addresses,
            @NotBlank String listen,
            @NotBlank String forward,
            Map<String, PoolRefDTO> pool,
            @NotNull Integer port,
            @NotNull WafDTO waf,
            @NotBlank String persistence,
            Map<String, String> irules,
            Boolean redirect80
    ) {
        public VirtualServerDTO {
            if (redirect80 == null) redirect80 = false;
        }
    }

    public record PoolRefDTO(
            @JsonProperty("default") Boolean isDefault,
            List<String> hosts,
            List<String> paths
    ) {}

    public record WafDTO(
            Boolean enabled,
            String status
    ) {
        public WafDTO {
            if (enabled == null) enabled = false;
        }
    }

    public record PoolDTO(
            @JsonProperty("pool_member") List<@Valid PoolMemberDTO> poolMembers,
            @JsonProperty("lb_method") @NotBlank String lbMethod,
            @JsonProperty("monitor_condition") Object monitorCondition,
            List<MonitorDTO> monitors
    ) {}

    public record PoolMemberDTO(
            @NotBlank String ip,
            @NotNull Integer port,
            @JsonProperty("monitor_condition") Object monitorCondition,
            List<MonitorDTO> monitors
    ) {}

    public record MonitorDTO(
            @NotBlank String type,
            Integer interval,
            Object port,
            String method,
            String path,
            String host,
            String version,
            String expect
    ) {}
}
