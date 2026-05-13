package de.muenchen.mcmp.nic;

import de.muenchen.mcmp.portgroup.PortGroup;
import lombok.Builder;

@Builder
public record NicDTO(
    Long serverId,
    PortGroup portGroup,
    Integer vnicKey,
    Integer unitNumber,
    String device,
    String macAddress,
    String network,
    Boolean connected,
    String portGroupSummary,
    String portGroupKey,
    String distributedPortKey,
    String addressType,
    String cardType,
    String toolsIpAddress,
    String toolsNetworkName,
    Boolean toolsConnected
) {}
