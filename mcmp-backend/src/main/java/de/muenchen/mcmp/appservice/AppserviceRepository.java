package de.muenchen.mcmp.appservice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppserviceRepository extends JpaRepository<Appservice, Long> {

    @Query(value = """
    SELECT
        a.id AS id,
        a.name AS name,
        a.environment AS environment,
        EXISTS (
            SELECT 1
            FROM cmp.server_assignment sa
            JOIN cmp.server s ON sa.server_id = s.id
            WHERE sa.appservice_id = a.id
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
                    SELECT 1 FROM cmp.group_membership gm
                    JOIN cmp."user" u ON gm.user_id = u.id
                    WHERE gm.group_id = a.change_group_id AND u.username = :username
                )
            )
        ) AS has_servers
    FROM cmp.appservice a
    WHERE (
        :isAdmin
        OR :isReadonly
        OR :hasLinuxRole
        OR :hasWindowsRole
        OR :hasOracleRole
        OR :hasNonOracleRole
        OR :hasSecurityRole
        OR :hasOperatorRole
        OR :hasNetworkRole
        OR EXISTS (
            SELECT 1
            FROM cmp.appservice a2
            JOIN cmp."group" g2 ON a2.change_group_id = g2.id
            JOIN cmp.group_membership gm2 ON g2.id = gm2.group_id
            JOIN cmp."user" u2 ON gm2.user_id = u2.id
            WHERE a2.id = a.id AND u2.username = :username
        )
    )
    AND (
        :search IS NULL
        OR :search = ''
        OR lower(a.name) LIKE ALL (CAST(:terms AS text[]))
    )
    ORDER BY
        CASE WHEN :sortOrder = 'asc' THEN a.name END ASC,
        CASE WHEN :sortOrder = 'desc' THEN a.name END DESC;
    """, countQuery = """
        SELECT COUNT(a.id)
        FROM cmp.appservice a
        WHERE (
            :isAdmin
            OR :isReadonly
            OR :hasLinuxRole
            OR :hasWindowsRole
            OR :hasOracleRole
            OR :hasNonOracleRole
            OR :hasSecurityRole
            OR :hasOperatorRole
            OR :hasNetworkRole
            OR EXISTS (
                SELECT 1
                FROM cmp.appservice a2
                JOIN cmp."group" g2 ON a2.change_group_id = g2.id
                JOIN cmp.group_membership gm2 ON g2.id = gm2.group_id
                JOIN cmp."user" u2 ON gm2.user_id = u2.id
                WHERE a2.id = a.id AND u2.username = :username
            )
        )
        AND (
            :search IS NULL
            OR :search = ''
            OR lower(a.name) LIKE ALL (CAST(:terms AS text[]))
        )
    """, nativeQuery = true)
    Page<AppserviceList> findVisibleAppservices(@Param("username") String username,
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
                                                @Param("terms") String[] terms,
                                                @Param("sortOrder") String sortOrder,
                                                Pageable pageable);

    @Query(value = """
            SELECT a.*
            FROM cmp.appservice a
            LEFT JOIN cmp."group" g ON a.change_group_id = g.id
            LEFT JOIN cmp.group_membership gm ON g.id = gm.group_id
            LEFT JOIN cmp."user" u ON gm.user_id = u.id
            WHERE a.id = :id
              AND (
                :isAdmin
                OR :isReadonly
                OR :hasSecurityRole
                OR :hasOperatorRole
                OR :hasNetworkRole
                OR u.username = :username
              )
            LIMIT 1
            """, nativeQuery = true)
    Appservice findVisibleAppserviceById(@Param("username") String username,
                                         @Param("isAdmin") boolean isAdmin,
                                         @Param("isReadonly") boolean isReadonly,
                                         @Param("hasSecurityRole") boolean hasSecurityRole,
                                         @Param("hasOperatorRole") boolean hasOperatorRole,
                                         @Param("hasNetworkRole") boolean hasNetworkRole,
                                         @Param("id") Long id);

    @Query(value = """
        SELECT DISTINCT a.id AS id, a.name AS name, a.sys_id AS sys_id
        FROM cmp.appservice a
        INNER JOIN cmp.server_assignment sa ON a.id = sa.appservice_id
        WHERE sa.server_id = :serverId
        ORDER BY a.name
        """, nativeQuery = true)
    List<AppserviceNameAndSysId> findAppservicesByServerId(@Param("serverId") Long serverId);

    Appservice findBySysId(String sysId);

    Appservice findByNumber(String number);
}
