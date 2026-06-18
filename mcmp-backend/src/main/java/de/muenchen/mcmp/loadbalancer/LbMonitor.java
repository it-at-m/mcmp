package de.muenchen.mcmp.loadbalancer;

/**
 * Value type stored as JSONB within lb_pool.monitors and lb_pool_member.monitors.
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
