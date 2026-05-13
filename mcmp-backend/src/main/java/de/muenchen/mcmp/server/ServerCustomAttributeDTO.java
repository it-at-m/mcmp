package de.muenchen.mcmp.server;

import lombok.Builder;

@Builder
public record ServerCustomAttributeDTO(
        String name,
        String value
){};