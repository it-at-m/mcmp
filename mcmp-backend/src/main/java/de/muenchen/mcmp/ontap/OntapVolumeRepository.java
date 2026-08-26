package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OntapVolumeRepository extends JpaRepository<OntapVolume, Long> {

    @Query("SELECT v FROM OntapVolume v " +
            "LEFT JOIN FETCH v.ontapAggregates " +
            "LEFT JOIN FETCH v.ontapSnapshots " +
            "WHERE v.cluster.id = :clusterId")
    List<OntapVolume> findAllByClusterId(@Param("clusterId") Long clusterId);

    void deleteAllByClusterId(Long clusterId);

    List<OntapVolume> findAllByMountPathNfsIn(List<String> mountPathsNfs);

    Optional<OntapVolume> findByVolumeUuid(UUID volumeUuid);

    @Query("SELECT v FROM OntapVolume v " +
            "LEFT JOIN FETCH v.svm s " +
            "LEFT JOIN FETCH v.exportPolicy ep " +
            "LEFT JOIN FETCH v.ontapAggregates " +
            "LEFT JOIN FETCH v.ontapCifsShares " +
            "WHERE v.volumeUuid = :uuid " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    Optional<OntapVolume> findByVolumeUuidWithPermissions(@Param("uuid") UUID uuid,
                                                          @Param("username") String username,
                                                          @Param("isAdmin") boolean isAdmin,
                                                          @Param("isReadonly") boolean isReadonly,
                                                          @Param("isStorage") boolean isStorage,
                                                          @Param("isOperator") boolean isOperator);

    @Query("SELECT v.volumeUuid, v.name, s.name, v.storageCategory FROM OntapVolume v " +
            "JOIN v.svm s " +
            "WHERE (LOWER(s.name) LIKE '%dcn' OR LOWER(s.name) LIKE '%dcc') " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND v.ontapQtrees IS EMPTY " +
            "AND v.storageCategory IS NOT NULL " +
            "AND (:search IS NULL OR LOWER(v.name) LIKE :search OR LOWER(s.name) LIKE :search OR LOWER(CONCAT(s.name, ':', v.mountPathNfs)) LIKE :search) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<Object[]> findNfsVolumeListItems(@Param("search") String search,
                                          @Param("username") String username,
                                          @Param("isAdmin") boolean isAdmin,
                                          @Param("isReadonly") boolean isReadonly,
                                          @Param("isStorage") boolean isStorage,
                                          @Param("isOperator") boolean isOperator);

    @Query("SELECT v.volumeUuid, v.name, s.name, v.storageCategory FROM OntapVolume v " +
            "JOIN v.svm s " +
            "JOIN v.ontapCifsShares cs " +
            "WHERE v.storageCategory IS NOT NULL " +
            "AND (:search IS NULL OR (LOWER(v.name) LIKE :search OR LOWER(cs.name) LIKE :search OR LOWER(s.name) LIKE :search OR LOWER(CONCAT(s.name, ':', cs.mountPathCifs)) LIKE :search)) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ") " +
            "GROUP BY v.id, v.volumeUuid, v.name, s.name, v.storageCategory")
    List<Object[]> findCifsVolumeListItems(@Param("search") String search,
                                           @Param("username") String username,
                                           @Param("isAdmin") boolean isAdmin,
                                           @Param("isReadonly") boolean isReadonly,
                                           @Param("isStorage") boolean isStorage,
                                           @Param("isOperator") boolean isOperator);

    @Query("SELECT DISTINCT v FROM OntapVolume v " +
            "LEFT JOIN FETCH v.appservices " +
            "WHERE v.volumeUuid IN :uuids")
    List<OntapVolume> findByVolumeUuidsWithAppservices(@Param("uuids") List<UUID> uuids);

    @Query("SELECT v.volumeUuid, v.name, s.name, v.storageCategory FROM OntapVolume v " +
            "JOIN v.svm s " +
            "JOIN v.appservices av " +
            "WHERE av.id = :appserviceId " +
            "AND (LOWER(s.name) LIKE '%dcn' OR LOWER(s.name) LIKE '%dcc') " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND v.ontapQtrees IS EMPTY " +
            "AND v.storageCategory IS NOT NULL " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<Object[]> findNfsVolumeListItemsByAppserviceId(@Param("appserviceId") Long appserviceId,
                                                        @Param("username") String username,
                                                        @Param("isAdmin") boolean isAdmin,
                                                        @Param("isReadonly") boolean isReadonly,
                                                        @Param("isStorage") boolean isStorage,
                                                        @Param("isOperator") boolean isOperator);

    @Query("SELECT v.volumeUuid, v.name, s.name, v.storageCategory FROM OntapVolume v " +
            "JOIN v.svm s " +
            "JOIN v.ontapCifsShares cs " +
            "JOIN v.appservices av " +
            "WHERE av.id = :appserviceId " +
            "AND v.storageCategory IS NOT NULL " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ") " +
            "GROUP BY v.id, v.volumeUuid, v.name, s.name, v.storageCategory")
    List<Object[]> findCifsVolumeListItemsByAppserviceId(@Param("appserviceId") Long appserviceId,
                                                         @Param("username") String username,
                                                         @Param("isAdmin") boolean isAdmin,
                                                         @Param("isReadonly") boolean isReadonly,
                                                         @Param("isStorage") boolean isStorage,
                                                         @Param("isOperator") boolean isOperator);

    @Query("SELECT CASE WHEN EXISTS (" +
            "SELECT 1 FROM OntapVolume v WHERE v.volumeUuid = :uuid AND SIZE(v.appservices) = 1" +
            ") AND (:isAdmin = TRUE OR :isStorage = TRUE OR EXISTS (" +
            "SELECT 1 FROM OntapVolume v " +
            "JOIN v.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE v.volumeUuid = :uuid AND u.username = :username" +
            ")) THEN TRUE ELSE FALSE END")
    Boolean canUserEditVolume(@Param("uuid") UUID uuid, @Param("username") String username,
                              @Param("isAdmin") boolean isAdmin, @Param("isStorage") boolean isStorage);

    @Modifying
    @Query(value = """
            UPDATE cmp.ontap_volume
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
    @Query(value = "DELETE FROM cmp.ontap_volume_has_appservices WHERE ontap_volume_id = :volumeId", nativeQuery = true)
    void deleteAppServiceAssociations(@Param("volumeId") Long volumeId);

    @Modifying
    @Query(value = "INSERT INTO cmp.ontap_volume_has_appservices (ontap_volume_id, appservice_id) " +
            "SELECT :volumeId, a.id FROM appservice a WHERE a.number IN :appServiceNumbers " +
            "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addAppServiceAssociations(@Param("volumeId") Long volumeId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Modifying
    @Query(value = "DELETE FROM cmp.ontap_volume_has_appservices ovha " +
            "WHERE ovha.ontap_volume_id = :volumeId " +
            "AND ovha.appservice_id NOT IN (" +
            "    SELECT a.id FROM appservice a " +
            "    WHERE a.number IN :appServiceNumbers" +
            ")", nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("volumeId") Long volumeId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Query("SELECT DISTINCT v FROM OntapVolume v LEFT JOIN FETCH v.appservices LEFT JOIN FETCH v.svm")
    List<OntapVolume> findAllWithAppservices();

}