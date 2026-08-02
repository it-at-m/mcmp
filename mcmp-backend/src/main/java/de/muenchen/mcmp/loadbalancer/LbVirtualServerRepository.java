package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LbVirtualServerRepository extends JpaRepository<LbVirtualServer, Long> {

    @Query(value = """
    SELECT id, name, listen, port, appserviceName, firstDomain, "isFavorite"
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
            ) AS appserviceName,
            lvs.domains->>0 AS firstDomain,
            EXISTS (
                SELECT 1 FROM cmp.user_favorite_lb_virtual_server uflvs
                JOIN cmp.user u_fav ON uflvs.user_id = u_fav.id
                WHERE uflvs.lb_virtual_server_id = lvs.id AND u_fav.username = :username
            ) AS "isFavorite"
        FROM cmp.lb_virtual_server lvs
        WHERE (
            :isAdmin
            OR :isReadonly
            OR :isSecurity
            OR :isOperator
            OR :isNetwork
            OR :isLoadbalancer
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
        AND (
            :search IS NULL OR :search = ''
            OR lvs.name ILIKE CONCAT('%', :search, '%')
            OR EXISTS (
                SELECT 1 FROM jsonb_array_elements_text(lvs.domains) d
                WHERE d ILIKE CONCAT('%', :search, '%')
            )
        )
        AND (:favorites = FALSE OR EXISTS (
            SELECT 1 FROM cmp.user_favorite_lb_virtual_server uflvs
            JOIN cmp.user u_fav ON uflvs.user_id = u_fav.id
            WHERE uflvs.lb_virtual_server_id = lvs.id AND u_fav.username = :username
        ))
    ) AS filtered
    ORDER BY
        CASE WHEN "isFavorite" THEN 0 ELSE 1 END ASC,
        CASE WHEN :sortOrder = 'desc' AND :sortBy = 'domain' THEN firstDomain END DESC NULLS LAST,
        CASE WHEN :sortOrder = 'asc'  AND :sortBy = 'domain' THEN firstDomain END ASC NULLS LAST,
        CASE WHEN :sortOrder = 'desc' AND :sortBy = 'name' THEN name END DESC,
        CASE WHEN :sortOrder = 'asc'  AND :sortBy = 'name' THEN name END ASC
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
        OR :isLoadbalancer
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
    AND (
        :search IS NULL OR :search = ''
        OR lvs.name ILIKE CONCAT('%', :search, '%')
        OR EXISTS (
            SELECT 1 FROM jsonb_array_elements_text(lvs.domains) d
            WHERE d ILIKE CONCAT('%', :search, '%')
        )
    )
    AND (:favorites = FALSE OR EXISTS (
        SELECT 1 FROM cmp.user_favorite_lb_virtual_server uflvs
        JOIN cmp.user u_fav ON uflvs.user_id = u_fav.id
        WHERE uflvs.lb_virtual_server_id = lvs.id AND u_fav.username = :username
    ))
    """, nativeQuery = true)
    Page<LbVirtualServerList> findVisibleLoadbalancers(
            @Param("username") String username,
            @Param("isAdmin") boolean isAdmin,
            @Param("isReadonly") boolean isReadonly,
            @Param("isSecurity") boolean isSecurity,
            @Param("isOperator") boolean isOperator,
            @Param("isNetwork") boolean isNetwork,
            @Param("isLoadbalancer") boolean isLoadbalancer,
            @Param("search") String search,
            @Param("favorites") boolean favorites,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            Pageable pageable);

    @Modifying
    @Query(value = """
        INSERT INTO cmp.user_favorite_lb_virtual_server (user_id, lb_virtual_server_id)
        SELECT u.id, :lbVirtualServerId FROM cmp.user u WHERE u.username = :username
        ON CONFLICT DO NOTHING
    """, nativeQuery = true)
    void addLoadbalancerToFavorites(@Param("lbVirtualServerId") Long lbVirtualServerId, @Param("username") String username);

    @Modifying
    @Query(value = """
        DELETE FROM cmp.user_favorite_lb_virtual_server uflvs
        WHERE uflvs.lb_virtual_server_id = :lbVirtualServerId
          AND uflvs.user_id = (SELECT u.id FROM cmp.user u WHERE u.username = :username)
    """, nativeQuery = true)
    void removeLoadbalancerFromFavorites(@Param("lbVirtualServerId") Long lbVirtualServerId, @Param("username") String username);

    @Query(value = """
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
    JOIN cmp.lb_virtual_server_has_appservices lbha ON lbha.lb_virtual_server_id = lvs.id
    WHERE lbha.appservice_id = :appserviceId
    AND (
        :isAdmin
        OR :isReadonly
        OR :isSecurity
        OR :isOperator
        OR :isNetwork
        OR :isLoadbalancer
        OR EXISTS (
            SELECT 1
            FROM cmp.lb_virtual_server_has_appservices lbha3
            JOIN cmp.appservice a ON lbha3.appservice_id = a.id
            JOIN cmp."group" g ON a.change_group_id = g.id
            JOIN cmp.group_membership gm ON g.id = gm.group_id
            JOIN cmp.user u ON gm.user_id = u.id
            WHERE lbha3.lb_virtual_server_id = lvs.id
              AND u.username = :username
        )
    )
    ORDER BY lvs.name
    """, nativeQuery = true)
    List<LbVirtualServerList> findByAppserviceId(
            @Param("appserviceId") Long appserviceId,
            @Param("username") String username,
            @Param("isAdmin") boolean isAdmin,
            @Param("isReadonly") boolean isReadonly,
            @Param("isSecurity") boolean isSecurity,
            @Param("isOperator") boolean isOperator,
            @Param("isNetwork") boolean isNetwork,
            @Param("isLoadbalancer") boolean isLoadbalancer);

    @Query("SELECT CASE WHEN :isAdmin = TRUE OR :isLoadbalancer = TRUE OR EXISTS (" +
            "SELECT 1 FROM LbVirtualServer lvs " +
            "JOIN lvs.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE lvs.id = :id AND u.username = :username" +
            ") THEN TRUE ELSE FALSE END")
    Boolean canUserEditLoadbalancer(@Param("id") Long id, @Param("username") String username,
                                    @Param("isAdmin") boolean isAdmin, @Param("isLoadbalancer") boolean isLoadbalancer);

    Optional<LbVirtualServer> findByName(String name);

    @Query("SELECT lvs FROM LbVirtualServer lvs WHERE :name LIKE CONCAT(lvs.name, '%')")
    List<LbVirtualServer> findByNameStartingWith(@Param("name") String name);

    @Query(value = "SELECT a.number FROM cmp.lb_virtual_server_has_appservices lbha JOIN cmp.appservice a ON lbha.appservice_id = a.id WHERE lbha.lb_virtual_server_id = :vsId", nativeQuery = true)
    Set<String> findAppserviceNumbersByVsId(@Param("vsId") Long vsId);
}
