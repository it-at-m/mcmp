package de.muenchen.mcmp.loadbalancer;

import lombok.Builder;

import java.util.List;

@Builder
public record UnifiedLoadbalancer(
        Long id,
        String name,
        String listen,
        String forward,
        int port,
        String persistence,
        boolean wafEnabled,
        String wafStatus,
        boolean redirect80,
        List<String> addresses,
        List<String> domains,
        List<LbAppserviceRefDTO> appservices,
        String tenantRepositoryUrl,
        List<UnifiedLoadbalancerPoolDTO> pools,
        List<LbIruleDTO> irules,
        List<LbVirtualServerCiDTO> snowCis,
        boolean canEdit
) {}
