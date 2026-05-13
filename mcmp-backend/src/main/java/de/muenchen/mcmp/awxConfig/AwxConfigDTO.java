package de.muenchen.mcmp.awxConfig;

import lombok.Builder;

@Builder
public record AwxConfigDTO(
        Long id,
        String apiDescription,
        String apiUsername,
        String apiPassword,
        String apiEndpoint,
        boolean enabled
) {}
