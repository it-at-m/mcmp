package de.muenchen.mcmp.dto;

import lombok.Builder;

@Builder
public record NetworkRequestDTO(
        String apiEndpoint,
        Integer[] vlans,
        String cidr,
        String ipAddress,
        String netmask,
        String gateway,
        String broadcast,
        String dnsPrimary,
        String dnsSecondary,
        String name,
        String referat,
        String environment,
        String networktype,
        String comment,
        Boolean mcmpStatus,
        String mcmpNetworkTyp,
        String mcmpNetworkGroup
) {}
