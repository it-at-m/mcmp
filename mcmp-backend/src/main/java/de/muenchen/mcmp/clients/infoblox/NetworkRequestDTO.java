package de.muenchen.mcmp.clients.infoblox;

import lombok.Builder;

import java.util.List;

@Builder
public record NetworkRequestDTO(
        String apiEndpoint,
        List<Integer> vlans,
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
