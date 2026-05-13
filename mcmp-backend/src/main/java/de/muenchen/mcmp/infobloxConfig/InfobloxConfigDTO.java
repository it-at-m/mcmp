package de.muenchen.mcmp.infobloxConfig;

import lombok.Builder;

@Builder
public record InfobloxConfigDTO(
        Long id,
        String apiDescription,
        String apiUsername,
        String apiPassword,
        String apiEndpoint
) {}
