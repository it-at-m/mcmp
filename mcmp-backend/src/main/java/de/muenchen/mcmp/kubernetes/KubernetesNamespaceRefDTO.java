package de.muenchen.mcmp.kubernetes;

import lombok.Builder;

@Builder
public record KubernetesNamespaceRefDTO(
        Long id,
        String name,
        String clusterName,
        String clusterEnvironment
) {}
