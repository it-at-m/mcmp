package de.muenchen.mcmp.baasConfig;

import lombok.Builder;

@Builder
public record BaasConfigDTO(
        Long id,
        String apiDescription,
        String apiEndpoint,
        boolean enabled
) {}
