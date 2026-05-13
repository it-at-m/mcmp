package de.muenchen.mcmp.nic;

import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class NicService {
    private final NicRepository nicRepository;
    private final NicMapper nicMapper;

    public List<NicDTO> getNicsByServerId(final Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return nicMapper.toDTOs(nicRepository.findByServerId(serverId,
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
