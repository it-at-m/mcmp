package de.muenchen.mcmp.server;

public record ServerDbDTO(
        String fqdn,
        String powerState
) {}