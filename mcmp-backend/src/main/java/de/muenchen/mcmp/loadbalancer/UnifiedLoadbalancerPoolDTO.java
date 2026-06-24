package de.muenchen.mcmp.loadbalancer;

import lombok.Builder;

import java.util.List;

@Builder
public record UnifiedLoadbalancerPoolDTO(
        String name,
        String lbMethod,
        String monitorCondition,
        List<LbMonitor> monitors,
        LbPoolRef poolRef,
        List<UnifiedLoadbalancerMemberDTO> members
) {}
