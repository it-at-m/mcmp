package de.muenchen.mcmp.kubernetes;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record KubernetesNamespaceDetailDTO(
        Long id,
        String name,
        String sysId,
        String sysClass,
        Instant lastDiscovered,
        String k8sUid,
        String environment,
        String clusterName,
        String clusterEnvironment,
        String webconsoleUrl,
        List<KubernetesAppserviceRefDTO> appservices,
        boolean canEdit
) {}
