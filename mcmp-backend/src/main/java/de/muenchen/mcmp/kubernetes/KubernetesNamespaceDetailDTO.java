package de.muenchen.mcmp.kubernetes;

import lombok.Builder;

import java.util.Date;
import java.util.List;

@Builder
public record KubernetesNamespaceDetailDTO(
        Long id,
        String name,
        String sysId,
        String sysClass,
        Date lastDiscovered,
        String k8sUid,
        String environment,
        String clusterName,
        List<KubernetesAppserviceRefDTO> appservices,
        boolean canEdit
) {}
