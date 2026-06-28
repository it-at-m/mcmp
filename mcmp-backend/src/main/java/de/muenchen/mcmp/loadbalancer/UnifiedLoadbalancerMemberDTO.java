package de.muenchen.mcmp.loadbalancer;

import lombok.Builder;

import java.util.List;

@Builder
public record UnifiedLoadbalancerMemberDTO(
        String ip,
        int port,
        Long serverId,
        String serverName,
        String monitorCondition,
        List<LbMonitor> monitors
) {}
