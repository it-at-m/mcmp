package de.muenchen.mcmp.loadbalancer;

import lombok.Builder;

@Builder
public record LbServerMembershipDTO(
        Long vsId,
        String vsDomain,
        String poolName,
        String memberIp,
        Integer memberPort
) {}
