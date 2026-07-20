package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LbVirtualServerCiRepository extends JpaRepository<LbVirtualServerCi, Long> {

    Optional<LbVirtualServerCi> findBySnowSysId(String snowSysId);

    @Query("SELECT ci FROM LbVirtualServerCi ci LEFT JOIN FETCH ci.appservices")
    List<LbVirtualServerCi> findAllWithAppservices();

    @Modifying
    @Query(value = "DELETE FROM cmp.lb_virtual_server_ci_has_appservices WHERE lb_virtual_server_ci_id = :ciId", nativeQuery = true)
    void deleteAppServiceAssociations(@Param("ciId") Long ciId);

    @Modifying
    @Query(value = """
        DELETE FROM cmp.lb_virtual_server_ci_has_appservices
        WHERE lb_virtual_server_ci_id = :ciId
        AND appservice_id NOT IN (SELECT id FROM cmp.appservice WHERE number IN (:appServiceNumbers))
    """, nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("ciId") Long ciId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Modifying
    @Query(value = """
        INSERT INTO cmp.lb_virtual_server_ci_has_appservices (lb_virtual_server_ci_id, appservice_id)
        SELECT :ciId, id FROM cmp.appservice WHERE number IN (:appServiceNumbers)
        ON CONFLICT DO NOTHING
    """, nativeQuery = true)
    void addAppServiceAssociations(@Param("ciId") Long ciId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Modifying
    @Query(value = "DELETE FROM cmp.lb_virtual_server_has_appservices WHERE lb_virtual_server_id = :vsId", nativeQuery = true)
    void deleteVsAppServiceAssociations(@Param("vsId") Long vsId);

    @Modifying
    @Query(value = """
            DELETE FROM cmp.lb_virtual_server_has_appservices
            WHERE lb_virtual_server_id = :vsId
            AND appservice_id NOT IN (SELECT id FROM cmp.appservice WHERE number IN (:appServiceNumbers))
        """, nativeQuery = true)
    void deleteObsoleteVsAppServiceAssociations(@Param("vsId") Long vsId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Modifying
    @Query(value = """
        INSERT INTO cmp.lb_virtual_server_has_appservices (lb_virtual_server_id, appservice_id)
        SELECT :vsId, id FROM cmp.appservice WHERE number IN (:appServiceNumbers)
        ON CONFLICT DO NOTHING
    """, nativeQuery = true)
    void addVsAppServiceAssociations(@Param("vsId") Long vsId, @Param("appServiceNumbers") List<String> appServiceNumbers);
}