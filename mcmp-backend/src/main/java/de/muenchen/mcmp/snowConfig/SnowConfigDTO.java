package de.muenchen.mcmp.snowConfig;

import lombok.Builder;

@Builder
public record SnowConfigDTO(
        Long id,
        String apiDescription,
        String apiClientAuthUrl,
        String apiClientId,
        String apiClientSecret,
        String apiEndpoint,
        boolean enabled,
        String proxy,
        boolean useProxy
) {}
