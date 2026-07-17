package de.muenchen.mcmp.loadbalancer;

import lombok.Builder;

@Builder
public record LbVirtualServerListDTO(
        Long id,
        String name,
        String domain,
        String listen,
        int port,
        String appserviceName,
        boolean isFavorite
) {}
