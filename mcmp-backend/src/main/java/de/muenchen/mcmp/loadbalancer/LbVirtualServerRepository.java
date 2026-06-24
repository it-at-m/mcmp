package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LbVirtualServerRepository extends JpaRepository<LbVirtualServer, Long> {

    @Query(value = """
    SELECT id, name, listen, port, appserviceName
    FROM (
        SELECT DISTINCT
            lvs.id          AS id,
            lvs.name        AS name,
            lvs.listen      AS listen,
            lvs.port        AS port,
            (
                SELECT a.name
                FROM cmp.lb_virtual_server_has_appservices lbha2
                JOIN cmp.appservice a ON lbha2.appservice_id = a.id
                WHERE lbha2.lb_virtual_server_id = lvs.id
                ORDER BY a.name
                LIMIT 1
            ) AS appserviceName
        FROM cmp.lb_virtual_server lvs
        WHERE (
            :isAdmin
            OR :isReadonly
            OR :isSecurity
            OR :isOperator
            OR :isNetwork
            OR EXISTS (
                SELECT 1
                FROM cmp.lb_virtual_server_has_appservices lbha
                JOIN cmp.appservice a ON lbha.appservice_id = a.id
                JOIN cmp."group" g ON a.change_group_id = g.id
                JOIN cmp.group_membership gm ON g.id = gm.group_id
                JOIN cmp.user u ON gm.user_id = u.id
                WHERE lbha.lb_virtual_server_id = lvs.id
                  AND u.username = :username
            )
        )
        AND (:search IS NULL OR :search = '' OR lvs.name ILIKE CONCAT('%', :search, '%'))
    ) AS filtered
    ORDER BY
        CASE WHEN :sortOrder = 'desc' THEN name END DESC,
        CASE WHEN :sortOrder = 'asc'  THEN name END ASC
    """,
            countQuery = """
    SELECT COUNT(DISTINCT lvs.id)
    FROM cmp.lb_virtual_server lvs
    WHERE (
        :isAdmin
        OR :isReadonly
        OR :isSecurity
        OR :isOperator
        OR :isNetwork
        OR EXISTS (
            SELECT 1
            FROM cmp.lb_virtual_server_has_appservices lbha
            JOIN cmp.appservice a ON lbha.appservice_id = a.id
            JOIN cmp."group" g ON a.change_group_id = g.id
            JOIN cmp.group_membership gm ON g.id = gm.group_id
            JOIN cmp.user u ON gm.user_id = u.id
            WHERE lbha.lb_virtual_server_id = lvs.id
              AND u.username = :username
        )
    )
    AND (:search IS NULL OR :search = '' OR lvs.name ILIKE CONCAT('%', :search, '%'))
    """, nativeQuery = true)
    Page<LbVirtualServerList> findVisibleLoadbalancers(
            @Param("username") String username,
            @Param("isAdmin") boolean isAdmin,
            @Param("isReadonly") boolean isReadonly,
            @Param("isSecurity") boolean isSecurity,
            @Param("isOperator") boolean isOperator,
            @Param("isNetwork") boolean isNetwork,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            Pageable pageable);
}
