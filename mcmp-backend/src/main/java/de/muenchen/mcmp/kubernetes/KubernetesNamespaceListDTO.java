package de.muenchen.mcmp.kubernetes;

import lombok.Builder;

@Builder
public record KubernetesNamespaceListDTO(
        Long id,
        String name,
        boolean isFavorite
) {}
