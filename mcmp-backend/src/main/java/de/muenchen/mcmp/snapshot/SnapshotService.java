package de.muenchen.mcmp.snapshot;

import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SnapshotService {
    private final SnapshotRepository snapshotRepository;
    private final SnapshotMapper snapshotMapper;

    public List<SnapshotDTO> getSnapshotsByServerId(final Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return snapshotMapper.toDTOs(snapshotRepository.findByServerId(serverId,
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

