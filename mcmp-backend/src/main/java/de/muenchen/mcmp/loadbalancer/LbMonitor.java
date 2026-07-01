package de.muenchen.mcmp.loadbalancer;

/**
 * Read-model carrier for a health monitor, assembled from {@link LbPoolMonitor} for API responses.
 */
public record LbMonitor(
        String type,
        Integer interval,
        String port,
        String method,
        String path,
        String host,
        String version,
        String expect
) {}
