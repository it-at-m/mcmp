package de.muenchen.mcmp.loadbalancer;

public record LbVirtualServerCiDTO(
        String snowName,
        String snowSysId,
        String snowSysClass
) {}
