package de.muenchen.mcmp.loadbalancer;

import java.util.List;

/**
 * Read-model carrier for a virtual server's pool routing (isDefault/hosts/paths),
 * assembled from {@link LbVirtualServerPoolRef} for API responses.
 */
public record LbPoolRef(
        Boolean isDefault,
        List<String> hosts,
        List<String> paths
) {}
