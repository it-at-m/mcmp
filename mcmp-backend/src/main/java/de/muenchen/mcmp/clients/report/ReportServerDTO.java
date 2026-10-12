package de.muenchen.mcmp.clients.report;

import java.util.List;

public record ReportServerDTO(
        ReportCloudDTO cloud,
        String name,
        String fqdn,
        String guestToolsHostname,
        String uuid,
        String instanceUuid,
        String serverType,
        String powerState,
        String vmId,
        String cluster,
        String operatingsystem,
        String guestConfigFullName,
        String guestToolsFullName,
        Long foremanId,
        String foremanSource,
        List<ReportNicDTO> nics
) {
}