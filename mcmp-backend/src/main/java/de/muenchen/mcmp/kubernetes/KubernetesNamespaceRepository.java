package de.muenchen.mcmp.kubernetes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KubernetesNamespaceRepository extends JpaRepository<KubernetesNamespace, Long> {

    @Query("SELECT DISTINCT n FROM KubernetesNamespace n LEFT JOIN FETCH n.appservices WHERE n.cluster.id = :clusterId")
    List<KubernetesNamespace> findAllByClusterIdWithAppservices(@Param("clusterId") Long clusterId);

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