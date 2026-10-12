package de.muenchen.mcmp.clients.report;

import java.util.List;

public record ReportNicDTO(
        String device,
        String macAddress,
        Boolean connected,
        Boolean toolsConnected,
        String toolsIpAddress,
        String portGroupName,
        String portGroupVlan,
        List<String> ips
) {
}