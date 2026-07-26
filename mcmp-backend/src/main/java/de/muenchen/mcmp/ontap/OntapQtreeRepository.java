package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OntapQtreeRepository extends JpaRepository<OntapQtree, Long> {
    List<OntapQtree> findAllByVolumeIdIn(List<Long> volumeIds);

    List<OntapQtree> findAllByMountPathNfsIn(List<String> mountPathsNfs);

    @Query("SELECT q FROM OntapQtree q " +
            "JOIN FETCH q.volume v " +
            "JOIN v.svm s " +
            "LEFT JOIN FETCH q.exportPolicy ep " +
            "WHERE q.id = :id " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM q.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    Optional<OntapQtree> findByIdWithPermissions(@Param("id") Long id,
                                                 @Param("username") String username,
                                                 @Param("isAdmin") boolean isAdmin,
                                                 @Param("isReadonly") boolean isReadonly,
                                                 @Param("isStorage") boolean isStorage,
                                                 @Param("isOperator") boolean isOperator);

    @Query("SELECT q.id, q.name, s.name, q.path, q.storageCategory FROM OntapQtree q " +
            "JOIN q.volume v " +
            "JOIN v.svm s " +
            "WHERE LOWER(s.name) LIKE '%dcn' " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND q.storageCategory IS NOT NULL " +
            "AND (:search IS NULL OR LOWER(q.name) LIKE :search OR LOWER(s.name) LIKE :search OR LOWER(CONCAT(s.name, ':', v.mountPathNfs, '/', q.name)) LIKE :search) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM q.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<Object[]> findNfsQtreeListItems(@Param("search") String search,
                                         @Param("username") String username,
                                         @Param("isAdmin") boolean isAdmin,
                                         @Param("isReadonly") boolean isReadonly,
                                         @Param("isStorage") boolean isStorage,
                                         @Param("isOperator") boolean isOperator);

    @Query("SELECT q FROM OntapQtree q " +
            "LEFT JOIN FETCH q.appservices a " +
            "WHERE q.id IN :ids")
    List<OntapQtree> findByIdsWithAppservices(@Param("ids") List<Long> ids);

    @Query("SELECT q.id, q.name, s.name, q.path, q.storageCategory FROM OntapQtree q " +
            "JOIN q.volume v " +
            "JOIN v.svm s " +
            "JOIN q.appservices aq " +
            "WHERE aq.id = :appserviceId " +
            "AND LOWER(s.name) LIKE '%dcn' " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND q.storageCategory IS NOT NULL " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM q.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<Object[]> findNfsQtreeListItemsByAppserviceId(@Param("appserviceId") Long appserviceId,
                                                       @Param("username") String username,
                                                       @Param("isAdmin") boolean isAdmin,
                                                       @Param("isReadonly") boolean isReadonly,
                                                       @Param("isStorage") boolean isStorage,
                                                       @Param("isOperator") boolean isOperator);

    @Query("SELECT CASE WHEN :isAdmin = TRUE OR :isStorage = TRUE OR EXISTS (" +
            "SELECT 1 FROM OntapQtree q " +
            "JOIN q.volume v " +
            "JOIN q.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE q.id = :id AND v.ontapCifsShares IS EMPTY AND u.username = :username" +
            ") THEN TRUE ELSE FALSE END")
    Boolean canUserEditQtree(@Param("id") Long id, @Param("username") String username,
                             @Param("isAdmin") boolean isAdmin, @Param("isStorage") boolean isStorage);

    @Query("SELECT DISTINCT q FROM OntapQtree q " +
            "LEFT JOIN FETCH q.appservices " +
            "LEFT JOIN FETCH q.volume v " +
            "LEFT JOIN FETCH v.svm")
    List<OntapQtree> findAllWithAppservices();

    @Modifying
    @Query(value = """
            UPDATE cmp.ontap_qtree
            SET snow_name = :name,
                snow_sys_id = :sysId,
                snow_sys_class = :sysClass,
                snow_last_discovered = :lastDiscovered
            WHERE id = :id
            """, nativeQuery = true)
    void updateSnowFields(@Param("id") Long id,
                          @Param("name") String name,
                          @Param("sysId") String sysId,
                          @Param("sysClass") String sysClass,
                          @Param("lastDiscovered") OffsetDateTime lastDiscovered);

    @Modifying
    @Query(value = "DELETE FROM cmp.ontap_qtree_has_appservices WHERE ontap_qtree_id = :qtreeId", nativeQuery = true)
    void deleteAppServiceAssociations(@Param("qtreeId") Long qtreeId);

    @Modifying
    @Query(value = "INSERT INTO cmp.ontap_qtree_has_appservices (ontap_qtree_id, appservice_id) " +
            "SELECT :qtreeId, a.id FROM appservice a WHERE a.number IN :appServiceNumbers " +
            "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addAppServiceAssociations(@Param("qtreeId") Long qtreeId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Modifying
    @Query(value = "DELETE FROM cmp.ontap_qtree_has_appservices oqha " +
            "WHERE oqha.ontap_qtree_id = :qtreeId " +
            "AND oqha.appservice_id NOT IN (" +
            "    SELECT a.id FROM appservice a " +
            "    WHERE a.number IN :appServiceNumbers" +
            ")", nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("qtreeId") Long qtreeId, @Param("appServiceNumbers") List<String> appServiceNumbers);

}