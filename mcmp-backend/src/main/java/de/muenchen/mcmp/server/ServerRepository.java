package de.muenchen.mcmp.server;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {

    Server findByName(final String name);

    Page<Server> findAll(final Specification<Server> spec, final Pageable pageable);

    @Query(value = """
    SELECT s.id as id,
           s.name as name,
           s.power_state as powerState,
          CASE
              WHEN s.operatingsystem IS NOT NULL AND s.operatingsystem <> '' THEN s.operatingsystem
              WHEN s.guest_tools_full_name IS NOT NULL AND s.guest_tools_full_name <> '' THEN s.guest_tools_full_name
              ELSE s.guest_config_full_name
          END as os,
          s.server_kind,
          s.server_type,
          (s.num_cpu_rightsizing <> 'ok' OR s.memory_mb_rightsizing <> 'ok' OR s.patchnight_exitcode <> 0) as hasWarnings
    FROM cmp.server s
    WHERE (
        EXISTS (
            SELECT 1
            FROM cmp.server_assignment sa2
                     JOIN cmp.appservice a2 ON sa2.appservice_id = a2.id
                     JOIN cmp."group" g ON a2.change_group_id = g.id
                     JOIN cmp.group_membership gm ON g.id = gm.group_id
                     JOIN cmp.user u on gm.user_id = u.id
            WHERE sa2.server_id = s.id
              AND u.username = :username
        )
           OR :isAdmin
           OR :isReadonly
           OR (:hasLinuxRole AND s.role_linux)
           OR (:hasWindowsRole AND s.role_windows)
           OR (:hasOracleRole AND s.role_oracle)
           OR (:hasNonOracleRole AND s.role_non_oracle)
           OR :hasSecurityRole
           OR :hasOperatorRole
           OR :hasNetworkRole
    )
        AND (:search IS NULL OR :search = '' OR s.name ILIKE CONCAT('%', :search, '%'))
        AND (:statusFilter IS NULL OR s.power_state IN (:statusFilter))
        AND (
            (:linux = FALSE OR s.role_linux OR s.guest_config_full_name LIKE '%Linux%')
            AND (:mngLinux = FALSE OR s.role_linux)
            AND (:windows = FALSE OR s.role_windows OR s.guest_config_full_name LIKE '%Windows Server%')
            AND (:mngWindows = FALSE OR s.role_windows)
            AND (:windowsClient = FALSE OR (s.guest_config_full_name LIKE '%Windows%' AND s.guest_config_full_name NOT LIKE '%Windows Server%'))
            AND (:oracle = FALSE OR s.role_oracle)
            AND (:nonOracle = FALSE OR s.role_non_oracle)
            AND (:unmanaged = FALSE OR s.managed = FALSE)
        )
        ORDER BY
            CASE WHEN :sortOrder = 'desc' THEN s.name END DESC,
            CASE WHEN :sortOrder = 'asc' THEN s.name END ASC
    """,
            countQuery = """
    SELECT COUNT(DISTINCT s.id)
    FROM cmp.server s
    WHERE (
        EXISTS (
            SELECT 1
            FROM cmp.server_assignment sa2
                     JOIN cmp.appservice a2 ON sa2.appservice_id = a2.id
                     JOIN cmp."group" g ON a2.change_group_id = g.id
                     JOIN cmp.group_membership gm ON g.id = gm.group_id
                     JOIN cmp.user u on gm.user_id = u.id
            WHERE sa2.server_id = s.id
              AND u.username = :username
        )
           OR :isAdmin
           OR :isReadonly
           OR (:hasLinuxRole AND s.role_linux)
           OR (:hasWindowsRole AND s.role_windows)
           OR (:hasOracleRole AND s.role_oracle)
           OR (:hasNonOracleRole AND s.role_non_oracle)
           OR :hasSecurityRole
           OR :hasOperatorRole
           OR :hasNetworkRole
    )
    AND (:search IS NULL OR :search = '' OR s.name ILIKE CONCAT('%', :search, '%'))
    AND (:statusFilter IS NULL OR s.power_state IN (:statusFilter))
    AND (
        (:linux = FALSE OR s.role_linux OR s.guest_config_full_name LIKE '%Linux%')
            AND (:mngLinux = FALSE OR s.role_linux)
            AND (:windows = FALSE OR s.role_windows OR s.guest_config_full_name LIKE '%Windows Server%')
            AND (:mngWindows = FALSE OR s.role_windows)
            AND (:windowsClient = FALSE OR (s.guest_config_full_name LIKE '%Windows%' AND s.guest_config_full_name NOT LIKE '%Windows Server%'))
            AND (:oracle = FALSE OR s.role_oracle)
            AND (:nonOracle = FALSE OR s.role_non_oracle)
            AND (:unmanaged = FALSE OR s.managed = FALSE)
    )
    """, nativeQuery = true)
    Page<ServerList> findVisibleServers(@Param("username") String username,
                                        @Param("isAdmin") boolean isAdmin,
                                        @Param("isReadonly") boolean isReadonly,
                                        @Param("hasLinuxRole") boolean hasLinuxRole,
                                        @Param("hasWindowsRole") boolean hasWindowsRole,
                                        @Param("hasOracleRole") boolean hasOracleRole,
                                        @Param("hasNonOracleRole") boolean hasNonOracleRole,
                                        @Param("hasSecurityRole") boolean hasSecurityRole,
                                        @Param("hasOperatorRole") boolean hasOperatorRole,
                                        @Param("hasNetworkRole") boolean hasNetworkRole,
                                        @Param("search") String search,
                                        @Param("statusFilter") List<String> statusFilter,
                                        @Param("linux") boolean linux,
                                        @Param("mngLinux") boolean mngLinux,
                                        @Param("windows") boolean windows,
                                        @Param("mngWindows") boolean mngWindows,
                                        @Param("windowsClient") boolean windowsClient,
                                        @Param("oracle") boolean oracle,
                                        @Param("nonOracle") boolean nonOracle,
                                        @Param("unmanaged") boolean unmanaged,
                                        @Param("sortBy") String sortBy,
                                        @Param("sortOrder") String sortOrder,
                                        Pageable pageable);

    @Query(value = """
    WITH user_in_group AS (
        SELECT 1
        FROM cmp.server_assignment sa
        JOIN cmp.appservice a ON sa.appservice_id = a.id
        JOIN cmp."group" g ON a.change_group_id = g.id
        JOIN cmp.group_membership gm ON g.id = gm.group_id
        JOIN cmp.user u ON gm.user_id = u.id
        WHERE sa.server_id = :serverId
        AND u.username = :username
        LIMIT 1
    ),
    temp_privileges AS (
        SELECT tp.privilege_type, tp.expires_at
        FROM cmp.temporary_privileges tp
        JOIN cmp.user u ON tp.user_id = u.id
        WHERE tp.server_id = :serverId
        AND u.username = :username
        AND CURRENT_TIMESTAMP BETWEEN tp.granted_at AND tp.expires_at
        ORDER BY tp.expires_at DESC
        LIMIT 1
    ),
    number_of_assigned_appservices AS (
         SELECT count(*) as count
         FROM cmp.server_assignment sa
         WHERE sa.server_id = :serverId
    ),
    running_job_counts AS (
        SELECT server_id,
           COUNT(CASE WHEN action_identifier LIKE '%GREEN_IT%' THEN 1 END) AS running_green_it_count,
           COUNT(CASE WHEN action_identifier NOT LIKE '%GREEN_IT%' THEN 1 END) AS running_jobs_count
        FROM cmp.job
        WHERE status NOT IN ('successful', 'failed', 'error', 'canceled', 'rejected')
        GROUP BY server_id
    ),
    server_custom_attribute AS (
        SELECT server_id, name, value
        FROM cmp.server_custom_attribute sca
        WHERE sca.server_id = :serverId
    )
    SELECT
        s.*,
        c.name AS cloudName,
        c.fqdn AS cloudFqdn,
        c.server_gui AS cloudServerGui,
        c.cloud_type AS cloudType,
        m.cpu_util,
        m.mem_used_percent,
        CASE
              WHEN s.operatingsystem IS NOT NULL AND s.operatingsystem <> '' THEN s.operatingsystem
              WHEN s.guest_tools_full_name IS NOT NULL AND s.guest_tools_full_name <> '' THEN s.guest_tools_full_name
              ELSE s.guest_config_full_name
        END as os,
        (
            SELECT jsonb_object_agg(sca.name, sca.value)
            FROM server_custom_attribute sca
            WHERE sca.server_id = s.id
        ) AS server_custom_attributes,
        (
            :isMaintenanceMode = false AND
            s.locked = false AND (
                noa.count = 1
                OR (
                    noa.count <> 1 AND (
                        (:hasLinuxRole AND s.role_linux)
                        OR
                        (:hasWindowsRole AND s.role_windows)
                    )
                )
            ) AND (
                :isAdmin
                OR (:hasLinuxRole    AND s.role_linux)
                OR (:hasWindowsRole  AND s.role_windows)
                OR (:hasOracleRole   AND s.role_oracle)
                OR (:hasNonOracleRole AND s.role_non_oracle)
                OR EXISTS (SELECT 1 FROM user_in_group)
            )
        ) AS can_edit,
        (EXISTS (SELECT 1 FROM temp_privileges WHERE privilege_type = 'ADMIN')) AS has_temp_admin_privileges,
        (EXISTS (SELECT 1 FROM temp_privileges WHERE privilege_type = 'ROOT')) AS has_temp_root_privileges,
        (SELECT expires_at FROM temp_privileges) AS temp_privileges_expires_at,
        COALESCE(rjc.running_green_it_count, 0) AS running_green_it_count,
        COALESCE(rjc.running_jobs_count, 0) AS running_jobs_count,
        noa.count as number_of_assigned_appservices
    FROM cmp.server s
    JOIN cmp.cloud c ON s.cloud_id = c.id
    CROSS JOIN number_of_assigned_appservices noa
    LEFT JOIN running_job_counts rjc ON s.id = rjc.server_id
    LEFT JOIN LATERAL (
                SELECT m.cpu_util, m.mem_used_percent
                FROM cmp.server_metrics m
                WHERE m.server_id = s.id
                  AND m.created_at >= :metricsFrom
                  AND m.created_at <= :metricsTo
                ORDER BY m.created_at DESC
                LIMIT 1
    ) m ON TRUE
    WHERE s.id = :serverId
    AND (
    :isAdmin
    OR :isReadonly
    OR :hasSecurityRole
    OR :hasOperatorRole
    OR :hasNetworkRole
    OR (:hasLinuxRole    AND s.role_linux)
    OR (:hasWindowsRole  AND s.role_windows)
    OR (:hasOracleRole   AND s.role_oracle)
    OR (:hasNonOracleRole AND s.role_non_oracle)
    OR EXISTS (SELECT 1 FROM user_in_group)
    )
""", nativeQuery = true)
    Optional<ServerWithPermissions> findServerWithPermissions(@Param("serverId") Long serverId,
                                                              @Param("username") String username,
                                                              @Param("isAdmin") boolean isAdmin,
                                                              @Param("isReadonly") boolean isReadonly,
                                                              @Param("hasLinuxRole") boolean hasLinuxRole,
                                                              @Param("hasWindowsRole") boolean hasWindowsRole,
                                                              @Param("hasOracleRole") boolean hasOracleRole,
                                                              @Param("hasNonOracleRole") boolean hasNonOracleRole,
                                                              @Param("hasSecurityRole") boolean hasSecurityRole,
                                                              @Param("hasOperatorRole") boolean hasOperatorRole,
                                                              @Param("hasNetworkRole") boolean hasNetworkRole,
                                                              @Param("isMaintenanceMode") boolean isMaintenanceMode,
                                                              @Param("metricsFrom") OffsetDateTime metricsFrom,
                                                              @Param("metricsTo") OffsetDateTime metricsTo);

    @Query(value = """
    WITH srv AS (
        SELECT
            s.id,
            s.role_linux,
            s.role_windows,
            s.role_oracle,
            s.role_non_oracle
        FROM cmp.server s
        WHERE s.id = :serverId AND s.locked = false
    )
    SELECT (
        :isMaintenanceMode = false AND
        EXISTS (
            SELECT 1 FROM srv
        ) AND
        EXISTS (
            SELECT 1
            FROM srv s
            LEFT JOIN cmp.server_assignment sa ON sa.server_id = s.id
            GROUP BY s.id , s.role_linux, s.role_windows
            HAVING
                COUNT(sa.server_id) = 1
                OR (
                    COUNT(sa.server_id) <> 1
                    AND (
                        (:hasLinuxRole AND s.role_linux)
                        OR
                        (:hasWindowsRole AND s.role_windows)
                    )
                )
            )
            AND (
                :isAdmin
                OR EXISTS (
                  SELECT 1
                  FROM srv s
                  WHERE
                     (:hasLinuxRole AND s.role_linux)
                     OR (:hasWindowsRole AND s.role_windows)
                     OR (:hasOracleRole AND s.role_oracle)
                     OR (:hasNonOracleRole AND s.role_non_oracle)
                )
                OR EXISTS (
                  SELECT 1
                  FROM cmp.server_assignment sa
                  JOIN cmp.appservice a ON sa.appservice_id = a.id
                  JOIN cmp."group" g ON a.change_group_id = g.id
                  JOIN cmp.group_membership gm ON g.id = gm.group_id
                  JOIN cmp."user" u ON gm.user_id = u.id
                  WHERE sa.server_id = :serverId
                    AND u.username = :username
              )
            )
    ) AS has_permission
""", nativeQuery = true)
    boolean canUserEditServer(@Param("serverId") Long serverId,
                              @Param("username") String username,
                              @Param("isAdmin") boolean isAdmin,
                              @Param("hasLinuxRole") boolean hasLinuxRole,
                              @Param("hasWindowsRole") boolean hasWindowsRole,
                              @Param("hasOracleRole") boolean hasOracleRole,
                              @Param("hasNonOracleRole") boolean hasNonOracleRole,
                              @Param("isMaintenanceMode") boolean isMaintenanceMode);


    @Query(value = """
    SELECT DISTINCT s.id as id,
           s.name as name,
           s.power_state as powerState,
           s.managed as managed,
           s.num_cpu as numCpu,
           s.memory_mb as memoryMb,
           s.vdisks_capacity_in_bytes as vdisksCapacityInBytes,
           CASE
              WHEN s.operatingsystem IS NOT NULL AND s.operatingsystem <> '' THEN s.operatingsystem
              WHEN s.guest_tools_full_name IS NOT NULL AND s.guest_tools_full_name <> '' THEN s.guest_tools_full_name
              ELSE s.guest_config_full_name
           END as os,
           (
             -- canEdit: unlocked and either single assignment or roles allow edit, and user has appropriate permissions
             s.locked = false
             AND (
               (SELECT count(*) FROM cmp.server_assignment sa_count WHERE sa_count.server_id = s.id) = 1
               OR (
                 (SELECT count(*) FROM cmp.server_assignment sa_count WHERE sa_count.server_id = s.id) <> 1
                 AND (
                   (:hasLinuxRole AND s.role_linux)
                   OR (:hasWindowsRole AND s.role_windows)
                 )
               )
             )
             AND (
               :isAdmin
               OR (:hasLinuxRole AND s.role_linux)
               OR (:hasWindowsRole AND s.role_windows)
               OR (:hasOracleRole AND s.role_oracle)
               OR (:hasNonOracleRole AND s.role_non_oracle)
               OR EXISTS (
                 SELECT 1
                 FROM cmp.server_assignment sa2
                   JOIN cmp.appservice a2 ON sa2.appservice_id = a2.id
                   JOIN cmp."group" g2 ON a2.change_group_id = g2.id
                   JOIN cmp.group_membership gm2 ON g2.id = gm2.group_id
                   JOIN cmp."user" u2 ON gm2.user_id = u2.id
                 WHERE sa2.server_id = s.id
                   AND u2.username = :username
               )
             )
           ) as canEdit,
           STRING_AGG(DISTINCT a.name, '|' ORDER BY a.name) as appserviceNames,
           s.server_kind,
           s.server_type
    FROM cmp.server s
    INNER JOIN cmp.server_assignment sa ON s.id = sa.server_id
    LEFT JOIN cmp.server_assignment sa_all ON s.id = sa_all.server_id
    LEFT JOIN cmp.appservice a ON sa_all.appservice_id = a.id
    WHERE sa.appservice_id = :appserviceId
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
          FROM cmp.server_assignment sa2
            JOIN cmp.appservice a2 ON sa2.appservice_id = a2.id
            JOIN cmp."group" g2 ON a2.change_group_id = g2.id
            JOIN cmp.group_membership gm2 ON g2.id = gm2.group_id
            JOIN cmp."user" u2 ON gm2.user_id = u2.id
          WHERE sa2.server_id = s.id
            AND u2.username = :username
        )
      )
    GROUP BY s.id, s.name, s.power_state, s.operatingsystem, s.guest_tools_full_name, s.guest_config_full_name, s.num_cpu, s.memory_mb, s.vdisks_capacity_in_bytes, s.server_kind, s.server_type, s.managed
    ORDER BY s.name ASC
    """, nativeQuery = true)
    List<ServerListExtended> findServersByAppserviceId(@Param("appserviceId") Long appserviceId,
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

    @Query(value = """
            SELECT s.*,
                   c.name AS cloudName,
                   c.fqdn AS cloudFqdn,
                   c.server_gui AS cloudServerGui,
                   c.cloud_type AS cloudType
            FROM cmp.server s
            JOIN cmp.cloud c ON s.cloud_id = c.id
            INNER JOIN cmp.server_assignment sa ON s.id = sa.server_id
            WHERE sa.appservice_id = :appserviceId
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
                    FROM cmp.server_assignment sa2
                             JOIN cmp.appservice a2 ON sa2.appservice_id = a2.id
                             JOIN cmp."group" g2 ON a2.change_group_id = g2.id
                             JOIN cmp.group_membership gm2 ON g2.id = gm2.group_id
                             JOIN cmp."user" u2 ON gm2.user_id = u2.id
                    WHERE sa2.server_id = s.id
                      AND u2.username = :username
                )
            )
            ORDER BY s.name ASC
            """, nativeQuery = true)
    List<Server> findFullServersByAppserviceId(@Param("appserviceId") Long appserviceId,
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

    @Query(value = """
    SELECT s.*
    FROM cmp.server s
    JOIN cmp.nic n ON s.id = n.server_id
    WHERE LOWER(n.mac_address) = LOWER(:macAddress)
""", nativeQuery = true)
    List<Server> findServersByMacAddress(@Param("macAddress") String macAddress);

    @Query(value = """
    SELECT DISTINCT s.*
    FROM cmp.server s
    WHERE EXISTS (
        SELECT 1
        FROM cmp.nic n
        JOIN cmp.ip_assignment a ON n.id = a.nic_id
        JOIN cmp.ip i ON a.ip_id = i.id
        WHERE s.id = n.server_id
          AND i.ip = :ipAddress
    )""", nativeQuery = true)
    List<Server> findServersByIpAddress(@Param("ipAddress") String ipAddress);

    @Query(value = """
    SELECT s.*
    FROM cmp.server s
    WHERE s.uuid = :uuid
""", nativeQuery = true)
    List<Server> findServersByUuid(@Param("uuid") String uuid);

    @Query(value = """
    SELECT s.*
    FROM cmp.server s
    WHERE s.instance_uuid = :instanceUuid
""", nativeQuery = true)
    List<Server> findServersByInstanceUuid(@Param("instanceUuid") String instanceUuid);

    @Query(value = """
    SELECT s.*
    FROM cmp.server s
    WHERE s.foreman_id = :foremanId
""", nativeQuery = true)
    List<Server> findServersByForemanId(@Param("foremanId") String foremanId);

    @Query(value = """
    SELECT s.*
    FROM cmp.server s
    WHERE s.snow_server_sys_id = :serverSysId
""", nativeQuery = true)
    List<Server> findServersByServerSysId(@Param("serverSysId") String serverSysId);

    @Query(value = """
    SELECT s.*
    FROM cmp.server s
    WHERE s.snow_instance_sys_id = :instanceSysId
""", nativeQuery = true)
    List<Server> findServersByInstanceSysId(@Param("instanceSysId") String instanceSysId);

    @Query(value = """
    SELECT s.*
    FROM cmp.server s
    WHERE UPPER(s.fqdn) IN (:fqdns)
""", nativeQuery = true)
    List<Server> findByFqdnInIgnoreCase(@Param("fqdns") List<String> fqdns);

    List<Server> findByPatchnightExitcodeNot(Short i);

    @Query(value = "SELECT server_id, created_at FROM cmp.server_assignment " +
                   "WHERE appservice_id = :appserviceId",
            nativeQuery = true)
    List<Object[]> findAllAssignmentCreatedAtByAppserviceId(@Param("appserviceId") Long appserviceId);

    List<Server> findByForemanSourceAndForemanId(String foremanSource, Long foremanId);

    List<Server> findByMaintenanceModeTrue();

    @Modifying
    @Query("UPDATE Server s SET s.maintenanceMode = :maintenanceMode, s.maintenanceModeExpiresAt = :expiresAt WHERE s.id = :serverId")
    void updateMaintenanceMode(@Param("serverId") final Long serverId,
                               @Param("maintenanceMode") final boolean maintenanceMode,
                               @Param("expiresAt") final OffsetDateTime expiresAt);

    @Modifying
    @Query("UPDATE Server s SET s.numCpuRecommended = :numCpu, s.memoryMbRecommended = :memoryMb WHERE s.id = :serverId")
    void updateRessourceRecommendations(@Param("serverId") final Long serverId,
                                        @Param("numCpu") final int numCpu,
                                        @Param("memoryMb") final int memoryMb);

    @Query(value = """
        SELECT s.*
        FROM cmp.server s
        JOIN cmp.cloud c ON s.cloud_id = c.id
        WHERE c.vcenter_short_code = :vcenterShortCode
          AND s.uuid = :uuid
        LIMIT 1
    """, nativeQuery = true)
    Optional<Server> findServerByVcenterShortCodeAndUuidOptional(@Param("vcenterShortCode") String vcenterShortCode,
                                                                 @Param("uuid") String uuid);

    @Query("SELECT s FROM Server s WHERE s.cloud.id = :cloudId")
    List<Server> findAllByCloudId(@Param("cloudId") Long cloudId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Server s WHERE s.cloud.id = :cloudId AND s.uuid IN :uuids")
    void deleteByCloudIdAndUuidIn(@Param("cloudId") Long cloudId, @Param("uuids") Collection<String> uuids);

    @Query("SELECT new de.muenchen.mcmp.server.ServerAutocompleteDTO(s.id, s.name) FROM Server s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY s.name")
    List<ServerAutocompleteDTO> findForAutocomplete(@Param("query") String query);
}
