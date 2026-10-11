package de.muenchen.mcmp.server;

public record ServerFqdnDTO(
        Long id,
        String name,
        String fqdn
) {}
