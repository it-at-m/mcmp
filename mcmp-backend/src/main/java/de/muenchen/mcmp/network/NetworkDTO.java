package de.muenchen.mcmp.network;

import lombok.Builder;

@Builder
public record NetworkDTO(
        Long id,
        String broadcast,
        String cidr,
        String comment,
        String dnsPrimary,
        String dnsSecondary,
        String environment,
        String gateway,
        Long infobloxId,
        String ipAddress,
        String name,
        String netmask,
        Long networkGroupId,
        String networktyp,
        String referat,
        String vlan,
        Boolean mcmpStatus,
        String mcmpNetworkTyp,
        String mcmpNetworkGroup,
        String infoblox) {}
