package de.muenchen.mcmp.clients.report;

import de.muenchen.mcmp.types.CloudType;

import java.util.List;

public record ReportCloudDTO(
        String fqdn,
        CloudType cloudType
) {
}