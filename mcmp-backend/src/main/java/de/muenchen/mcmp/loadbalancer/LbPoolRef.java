package de.muenchen.mcmp.loadbalancer;

import java.util.List;

/**
 * Embeddable value type stored as part of the lb_virtual_server.pool_refs JSONB column.
 */
public record LbPoolRef(
        Boolean isDefault,
        List<String> hosts,
        List<String> paths
) {}
