package de.muenchen.mcmp.loadbalancer;

import lombok.Builder;

@Builder
public record LbIruleDTO(
        String name,
        String content
) {}
