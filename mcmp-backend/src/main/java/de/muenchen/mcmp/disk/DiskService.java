package de.muenchen.mcmp.disk;

import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class DiskService {
    private final DiskRepository diskRepository;
    private final DiskMapper diskMapper;

    public List<DiskDTO> getDisksByServerId(final Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return diskMapper.toDTOs(diskRepository.findByServerId(serverId,
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasLinuxRole(),
                userRoles.hasWindowsRole(),
                userRoles.hasOracleRole(),
                userRoles.hasNonOracleRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole(),
                userRoles.hasNetworkRole()));
    }
}

