package de.muenchen.mcmp.clients.foreman;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record InterfaceDTO(
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("domain_name")
        String domainName,
        Boolean execution,
        String fqdn,
        Integer id,
        String identifier,
        String ip,
        String ip6,
        String mac,
        Integer mtu,
        Boolean managed,
        String name,
        Boolean primary,
        Boolean provision,
        @JsonProperty("subnet_name")
        String subnetName,
        String type,
        @JsonProperty("updated_at")
        String updatedAt,
        Boolean virtual
) {}

