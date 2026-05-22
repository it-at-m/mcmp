package de.muenchen.mcmp.appservice;

import de.muenchen.mcmp.server.ServerListExtendedDTO;

import java.util.List;

public record AppserviceDTO(
        Long id,
        String name,
        String number,
        String sysId,
        String usedFor,
        String environment,
        String ownedByUsername,
        String ownedByName,
        String serviceOwnerDelegateUsername,
        String serviceOwnerDelegateName,
        Long changeGroupId,
        String changeGroupName,
        String changeGroupSysId,
        Boolean cswEnforced,
        List<ServerListExtendedDTO> servers
) {
}
