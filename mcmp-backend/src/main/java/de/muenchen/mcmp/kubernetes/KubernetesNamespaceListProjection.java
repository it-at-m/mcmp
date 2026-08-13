package de.muenchen.mcmp.kubernetes;

public interface KubernetesNamespaceListProjection {
    Long getId();
    String getName();
    String getClusterEnvironment();
    Boolean getIsFavorite();
}
