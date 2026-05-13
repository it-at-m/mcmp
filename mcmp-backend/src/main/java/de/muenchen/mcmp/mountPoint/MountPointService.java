package de.muenchen.mcmp.mountPoint;

import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class MountPointService {
    private final MountPointRepository mountPointRepository;
    private final MountPointMapper mountPointMapper;

    public List<MountPointDTO> getMountPointsByServerId(final Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return mountPointMapper.toDTOs(mountPointRepository.findByServerId(serverId,
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

    public MountPointDTO getMountPointByServerIdAndPath(final Long serverId, final String path){
        final List<MountPointDTO> mountPoints = getMountPointsByServerId(serverId);

        return mountPoints.stream().filter(v -> v.diskPath().equals(path)).findFirst().orElse(null);
    }
}
