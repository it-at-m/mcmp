package de.muenchen.mcmp.ontap;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT v FROM OntapVolume v " +
            "JOIN FETCH v.svm s " +
            "LEFT JOIN FETCH v.exportPolicy ep " +
            "WHERE (LOWER(s.name) LIKE '%dcn' OR LOWER(s.name) LIKE '%dcc') " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND v.ontapQtrees IS EMPTY")
    List<OntapVolume> findNfsVolumes();

    @Query("SELECT DISTINCT v FROM OntapVolume v " +
            "JOIN FETCH v.ontapCifsShares s " +
            "LEFT JOIN FETCH v.ontapAggregates")
    List<OntapVolume> findCifsVolumes();

    void deleteAllByClusterId(Long clusterId);

    List<OntapVolume> findAllByMountPathNfsIn(List<String> mountPathsNfs);

    @Query("SELECT v.volumeUuid, s.snapshotUuid FROM OntapVolume v JOIN v.ontapSnapshots s WHERE v.cluster.id = :clusterId")
    List<Object[]> findVolumeSnapshotUuids(@Param("clusterId") Long clusterId);

    @Query("SELECT v.id FROM OntapVolume v " +
            "JOIN v.svm s " +
            "WHERE (LOWER(s.name) LIKE '%dcn' OR LOWER(s.name) LIKE '%dcc') " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND v.ontapQtrees IS EMPTY " +
            "AND (:search IS NULL OR LOWER(v.name) LIKE :search) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<Long> findNfsVolumeIds(@Param("search") String search,
                                @Param("username") String username,
                                @Param("isAdmin") boolean isAdmin,
                                @Param("isReadonly") boolean isReadonly,
                                Pageable pageable);

    @Query("SELECT DISTINCT v FROM OntapVolume v " +
            "JOIN FETCH v.svm s " +
            "LEFT JOIN FETCH v.exportPolicy ep " +
            "LEFT JOIN FETCH v.ontapAggregates " +
            "WHERE v.id IN :ids")
    List<OntapVolume> findNfsVolumesByIds(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(v) FROM OntapVolume v " +
            "JOIN v.svm s " +
            "WHERE (LOWER(s.name) LIKE '%dcn' OR LOWER(s.name) LIKE '%dcc') " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND v.ontapQtrees IS EMPTY " +
            "AND (:search IS NULL OR LOWER(v.name) LIKE :search) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    long countNfsVolumes(@Param("search") String search,
                         @Param("username") String username,
                         @Param("isAdmin") boolean isAdmin,
                         @Param("isReadonly") boolean isReadonly);

    @Query("SELECT v.id FROM OntapVolume v " +
            "JOIN v.ontapCifsShares s " +
            "WHERE (:search IS NULL OR (LOWER(v.name) LIKE :search OR LOWER(s.name) LIKE :search)) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ") " +
            "GROUP BY v.id")
    List<Long> findCifsVolumeIds(@Param("search") String search,
                                 @Param("username") String username,
                                 @Param("isAdmin") boolean isAdmin,
                                 @Param("isReadonly") boolean isReadonly,
                                 Pageable pageable);

    @Query("SELECT DISTINCT v FROM OntapVolume v " +
            "JOIN FETCH v.ontapCifsShares s " +
            "LEFT JOIN FETCH v.ontapAggregates " +
            "WHERE v.id IN :ids")
    List<OntapVolume> findCifsVolumesByIds(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(DISTINCT v) FROM OntapVolume v " +
            "JOIN v.ontapCifsShares s " +
            "WHERE (:search IS NULL OR (LOWER(v.name) LIKE :search OR LOWER(s.name) LIKE :search)) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    long countCifsVolumes(@Param("search") String search,
                          @Param("username") String username,
                          @Param("isAdmin") boolean isAdmin,
                          @Param("isReadonly") boolean isReadonly);

    @Query("SELECT v FROM OntapVolume v " +
            "LEFT JOIN FETCH v.svm s " +
            "LEFT JOIN FETCH v.exportPolicy ep " +
            "LEFT JOIN FETCH v.ontapAggregates " +
            "LEFT JOIN FETCH v.ontapCifsShares " +
            "WHERE v.volumeUuid = :uuid " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    Optional<OntapVolume> findByVolumeUuidWithPermissions(@Param("uuid") UUID uuid,
                                                          @Param("username") String username,
                                                          @Param("isAdmin") boolean isAdmin,
                                                          @Param("isReadonly") boolean isReadonly);

    @Query("SELECT v.volumeUuid, v.name, s.name FROM OntapVolume v " +
            "JOIN v.svm s " +
            "WHERE (LOWER(s.name) LIKE '%dcn' OR LOWER(s.name) LIKE '%dcc') " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND v.ontapQtrees IS EMPTY " +
            "AND (:search IS NULL OR LOWER(v.name) LIKE :search) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<Object[]> findNfsVolumeListItems(@Param("search") String search,
                                          @Param("username") String username,
                                          @Param("isAdmin") boolean isAdmin,
                                          @Param("isReadonly") boolean isReadonly);

    @Query("SELECT v.volumeUuid, v.name, s.name FROM OntapVolume v " +
            "JOIN v.svm s " +
            "JOIN v.ontapCifsShares cs " +
            "WHERE (:search IS NULL OR (LOWER(v.name) LIKE :search OR LOWER(cs.name) LIKE :search)) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ") " +
            "GROUP BY v.id, v.volumeUuid, v.name, s.name")
    List<Object[]> findCifsVolumeListItems(@Param("search") String search,
                                           @Param("username") String username,
                                           @Param("isAdmin") boolean isAdmin,
                                           @Param("isReadonly") boolean isReadonly);

    @Query("SELECT DISTINCT v FROM OntapVolume v " +
            "LEFT JOIN FETCH v.appservices " +
            "WHERE v.volumeUuid IN :uuids")
    List<OntapVolume> findByVolumeUuidsWithAppservices(@Param("uuids") List<UUID> uuids);

    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN TRUE ELSE FALSE END " +
            "FROM OntapVolume v " +
            "JOIN v.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE v.volumeUuid = :uuid AND u.username = :username")
    Boolean canUserEditVolume(@Param("uuid") UUID uuid, @Param("username") String username);
}