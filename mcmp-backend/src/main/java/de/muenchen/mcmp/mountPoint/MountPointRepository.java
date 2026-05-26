package de.muenchen.mcmp.mountPoint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MountPointRepository extends JpaRepository<MountPoint, Long> {

    @Query(value = """
                SELECT mp.*
                FROM cmp.server s
                JOIN cmp.mount_point mp on s.id = mp.server_id
                WHERE s.id = :serverId
                AND mp.hidden = false
                AND (
                    :isAdmin
                    OR :isReadonly
                    OR (:hasLinuxRole AND s.role_linux)
                    OR (:hasWindowsRole AND s.role_windows)
                    OR (:hasOracleRole AND s.role_oracle)
                    OR (:hasNonOracleRole AND s.role_non_oracle)
                    OR :hasSecurityRole
                    OR :hasOperatorRole
                    OR :hasNetworkRole
                    OR EXISTS (
                        SELECT 1
                        FROM cmp.server_assignment sa
                        JOIN cmp.appservice a ON sa.appservice_id = a.id
                        JOIN cmp."group" g ON a.change_group_id = g.id
                        JOIN cmp.group_membership gm ON g.id = gm.group_id
                        JOIN cmp.user u ON gm.user_id = u.id
                        WHERE sa.server_id = :serverId
                        AND u.username = :username
                    )
                )
            """, nativeQuery = true)
    List<MountPoint> findByServerId(@Param("serverId") Long serverId,
                                    @Param("username") String username,
                                    @Param("isAdmin") boolean isAdmin,
                                    @Param("isReadonly") boolean isReadonly,
                                    @Param("hasLinuxRole") boolean hasLinuxRole,
                                    @Param("hasWindowsRole") boolean hasWindowsRole,
                                    @Param("hasOracleRole") boolean hasOracleRole,
                                    @Param("hasNonOracleRole") boolean hasNonOracleRole,
                                    @Param("hasSecurityRole") boolean hasSecurityRole,
                                    @Param("hasOperatorRole") boolean hasOperatorRole,
                                    @Param("hasNetworkRole") boolean hasNetworkRole);

    /**
     * Finds all mount points for a specific server ID without permission checks.
     * <p>
     * <strong>WARNING: This method is for internal use only!</strong>
     * </p>
     * <p>
     * This method retrieves all mount points associated with the given server ID
     * without performing any permission validation. It bypasses all security checks
     * including role-based access control and group membership verification.
     * </p>
     * <p>
     * Use {@link #findByServerId}
     * for user-facing operations that require proper authorization.
     * </p>
     *
     * @param serverId the ID of the server whose mount points should be retrieved
     * @return a list of all mount points for the specified server, or an empty list if none exist
     * @see #findByServerId
     */
    List<MountPoint> findAllByServerId(Long serverId);
}

