package de.muenchen.mcmp.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    @Query(value = """
                SELECT ss.*
                FROM cmp.server s
                JOIN cmp.snapshot ss ON s.id = ss.server_id
                WHERE s.id = :serverId
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
                ORDER BY ss.create_time ASC
            """, nativeQuery = true)
    List<Snapshot> findByServerId(@Param("serverId") Long serverId,
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
}

