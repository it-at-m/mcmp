package de.muenchen.mcmp.cloud;

import lombok.Builder;

@Builder
public record CloudDTO(
        Long id,
        String name,
        String fqdn,
        String serverGui,
        String cloudType,
        String apiDescription,
        String apiUsername,
        String apiPassword,
        String apiEndpoint,
        boolean enabled,
        boolean locked,
        Long configInfobloxId,
        Long configBaasId,
        boolean greenItEnabled,
        Long awxInventoryId
) {}
