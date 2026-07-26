package de.muenchen.mcmp.kubernetes;

public interface KubernetesNamespaceRefProjection {
    Long getId();
    String getName();
    String getClusterName();
}
