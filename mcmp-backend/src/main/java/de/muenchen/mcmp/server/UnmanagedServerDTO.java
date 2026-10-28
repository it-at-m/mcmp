package de.muenchen.mcmp.server;

public record UnmanagedServerDTO(
        Long serverID,
        String serverName,
        String serverFQDN,
        String serverPowerState,
        String serverVMID,
        String serverOS,
        String cluster,
        String cloudName,
        String vCenterServerGUI
) {
}