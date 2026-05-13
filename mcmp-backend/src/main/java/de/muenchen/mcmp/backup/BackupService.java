package de.muenchen.mcmp.backup;

import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class BackupService {
    private final BackupRepository backupRepository;
    private final BackupMapper backupMapper;

    public List<BackupDTO> getBackupsByServerId(final Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return backupMapper.toDTOs(
                backupRepository.findByServerId(serverId,
                        userRoles.getUsername(),
                        userRoles.hasAdminRole(),
                        userRoles.hasReadonlyRole(),
                        userRoles.hasLinuxRole(),
                        userRoles.hasWindowsRole(),
                        userRoles.hasOracleRole(),
                        userRoles.hasNonOracleRole(),
                        userRoles.hasSecurityRole(),
                        userRoles.hasOperatorRole(),
                        userRoles.hasNetworkRole()
                )
        );
    }
}

