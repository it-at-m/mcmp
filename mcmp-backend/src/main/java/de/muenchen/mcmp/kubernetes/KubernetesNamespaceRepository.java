package de.muenchen.mcmp.kubernetes;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KubernetesNamespaceRepository extends JpaRepository<KubernetesNamespace, Long> {

    @Query("SELECT CASE WHEN :isAdmin = TRUE OR EXISTS (" +
            "SELECT 1 FROM KubernetesNamespace n " +
            "JOIN n.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE n.id = :id AND u.username = :username" +
            ") THEN TRUE ELSE FALSE END")
    Boolean canUserEditNamespace(@Param("id") Long id, @Param("username") String username,
                                 @Param("isAdmin") boolean isAdmin);

    @Query("SELECT DISTINCT n FROM KubernetesNamespace n LEFT JOIN FETCH n.appservices WHERE n.cluster.id = :clusterId")
    List<KubernetesNamespace> findAllByClusterIdWithAppservices(@Param("clusterId") Long clusterId);

    @Query("SELECT DISTINCT n FROM KubernetesNamespace n " +
            "LEFT JOIN FETCH n.appservices " +
            "LEFT JOIN FETCH n.cluster " +
            "WHERE n.id = :id")
    Optional<KubernetesNamespace> findByIdWithDetails(@Param("id") Long id);

    @Query(value = """
    SELECT id, name, "clusterName", environment, appserviceName, "isFavorite"
    FROM (
        SELECT DISTINCT
            kn.id                AS id,
            kn.name              AS name,
            kc.name              AS "clusterName",
            kn.environment       AS environment,
            (
                SELECT a.name
                FROM cmp.kubernetes_namespace_has_appservices knha2
                JOIN cmp.appservice a ON knha2.appservice_id = a.id
                WHERE knha2.kubernetes_namespace_id = kn.id
                ORDER BY a.name
                LIMIT 1
            ) AS appserviceName,
            EXISTS (
                SELECT 1 FROM cmp.user_favorite_kubernetes_namespace ufkn
                JOIN cmp.user u_fav ON ufkn.user_id = u_fav.id
                WHERE ufkn.kubernetes_namespace_id = kn.id AND u_fav.username = :username
            ) AS "isFavorite"
        FROM cmp.kubernetes_namespace kn
        JOIN cmp.kubernetes_cluster kc ON kn.cluster_id = kc.id
        WHERE (
            :isAdmin
            OR :isReadonly
            OR :isSecurity
            OR :isOperator
            OR EXISTS (
                SELECT 1
                FROM cmp.kubernetes_namespace_has_appservices knha
                JOIN cmp.appservice a ON knha.appservice_id = a.id
                JOIN cmp."group" g ON a.change_group_id = g.id
                JOIN cmp.group_membership gm ON g.id = gm.group_id
                JOIN cmp.user u ON gm.user_id = u.id
                WHERE knha.kubernetes_namespace_id = kn.id
                  AND u.username = :username
            )
        )
        AND (
            :search IS NULL OR :search = ''
            OR kn.name ILIKE CONCAT('%', :search, '%')
            OR kc.name ILIKE CONCAT('%', :search, '%')
        )
        AND (:favorites = FALSE OR EXISTS (
            SELECT 1 FROM cmp.user_favorite_kubernetes_namespace ufkn
            JOIN cmp.user u_fav ON ufkn.user_id = u_fav.id
            WHERE ufkn.kubernetes_namespace_id = kn.id AND u_fav.username = :username
        ))
    ) AS filtered
    ORDER BY
        CASE WHEN "isFavorite" THEN 0 ELSE 1 END ASC,
        CASE WHEN :sortOrder = 'desc' AND :sortBy = 'name' THEN name END DESC,
        CASE WHEN :sortOrder = 'asc'  AND :sortBy = 'name' THEN name END ASC,
        CASE WHEN :sortOrder = 'desc' AND :sortBy = 'clusterName' THEN "clusterName" END DESC NULLS LAST,
        CASE WHEN :sortOrder = 'asc'  AND :sortBy = 'clusterName' THEN "clusterName" END ASC NULLS LAST
    """,
            countQuery = """
    SELECT COUNT(DISTINCT kn.id)
    FROM cmp.kubernetes_namespace kn
    JOIN cmp.kubernetes_cluster kc ON kn.cluster_id = kc.id
    WHERE (
        :isAdmin
        OR :isReadonly
        OR :isSecurity
        OR :isOperator
        OR EXISTS (
            SELECT 1
            FROM cmp.kubernetes_namespace_has_appservices knha
            JOIN cmp.appservice a ON knha.appservice_id = a.id
            JOIN cmp."group" g ON a.change_group_id = g.id
            JOIN cmp.group_membership gm ON g.id = gm.group_id
            JOIN cmp.user u ON gm.user_id = u.id
            WHERE knha.kubernetes_namespace_id = kn.id
              AND u.username = :username
        )
    )
    AND (
        :search IS NULL OR :search = ''
        OR kn.name ILIKE CONCAT('%', :search, '%')
        OR kc.name ILIKE CONCAT('%', :search, '%')
    )
    AND (:favorites = FALSE OR EXISTS (
        SELECT 1 FROM cmp.user_favorite_kubernetes_namespace ufkn
        JOIN cmp.user u_fav ON ufkn.user_id = u_fav.id
        WHERE ufkn.kubernetes_namespace_id = kn.id AND u_fav.username = :username
    ))
    """, nativeQuery = true)
    Page<KubernetesNamespaceListProjection> findVisibleNamespaces(
            @Param("username") String username,
            @Param("isAdmin") boolean isAdmin,
            @Param("isReadonly") boolean isReadonly,
            @Param("isSecurity") boolean isSecurity,
            @Param("isOperator") boolean isOperator,
            @Param("search") String search,
            @Param("favorites") boolean favorites,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            Pageable pageable);

    @Query(value = """
    SELECT DISTINCT
        kn.id       AS id,
        kn.name     AS name,
        kc.name     AS "clusterName"
    FROM cmp.kubernetes_namespace kn
    JOIN cmp.kubernetes_cluster kc ON kn.cluster_id = kc.id
    JOIN cmp.kubernetes_namespace_has_appservices knha ON knha.kubernetes_namespace_id = kn.id
    WHERE knha.appservice_id = :appserviceId
    AND (
        :isAdmin
        OR :isReadonly
        OR :isSecurity
        OR :isOperator
        OR EXISTS (
            SELECT 1
            FROM cmp.kubernetes_namespace_has_appservices knha2
            JOIN cmp.appservice a ON knha2.appservice_id = a.id
            JOIN cmp."group" g ON a.change_group_id = g.id
            JOIN cmp.group_membership gm ON g.id = gm.group_id
            JOIN cmp.user u ON gm.user_id = u.id
            WHERE knha2.kubernetes_namespace_id = kn.id
              AND u.username = :username
        )
    )
    ORDER BY kn.name
    """, nativeQuery = true)
    List<KubernetesNamespaceRefProjection> findByAppserviceId(
            @Param("appserviceId") Long appserviceId,
            @Param("username") String username,
            @Param("isAdmin") boolean isAdmin,
            @Param("isReadonly") boolean isReadonly,
            @Param("isSecurity") boolean isSecurity,
            @Param("isOperator") boolean isOperator);

    @Modifying
    @Query(value = """
        INSERT INTO cmp.user_favorite_kubernetes_namespace (user_id, kubernetes_namespace_id)
        SELECT u.id, :namespaceId FROM cmp.user u WHERE u.username = :username
        ON CONFLICT DO NOTHING
    """, nativeQuery = true)
    void addNamespaceToFavorites(@Param("namespaceId") Long namespaceId, @Param("username") String username);

    @Modifying
    @Query(value = """
        DELETE FROM cmp.user_favorite_kubernetes_namespace ufkn
        WHERE ufkn.kubernetes_namespace_id = :namespaceId
          AND ufkn.user_id = (SELECT u.id FROM cmp.user u WHERE u.username = :username)
    """, nativeQuery = true)
    void removeNamespaceFromFavorites(@Param("namespaceId") Long namespaceId, @Param("username") String username);

    @Modifying
    @Query(value = "DELETE FROM kubernetes_namespace_has_appservices WHERE kubernetes_namespace_id = :namespaceId", nativeQuery = true)
    void deleteAppServiceAssociations(@Param("namespaceId") Long namespaceId);

    @Modifying
    @Query(value = "INSERT INTO kubernetes_namespace_has_appservices (kubernetes_namespace_id, appservice_id) " +
            "SELECT :namespaceId, a.id FROM appservice a WHERE a.number IN :appServiceNumbers " +
            "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addAppServiceAssociations(@Param("namespaceId") Long namespaceId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Modifying
    @Query(value = "DELETE FROM kubernetes_namespace_has_appservices knha " +
            "WHERE knha.kubernetes_namespace_id = :namespaceId " +
            "AND knha.appservice_id NOT IN (" +
            "    SELECT a.id FROM appservice a " +
            "    WHERE a.number IN :appServiceNumbers" +
            ")", nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("namespaceId") Long namespaceId, @Param("appServiceNumbers") List<String> appServiceNumbers);
}