package de.muenchen.mcmp.kubernetes;

import org.springframework.stereotype.Component;

public interface KubernetesNamespaceRefProjection {
    Long getId();
    String getName();
    String getClusterName();
    String getClusterEnvironment();
}
