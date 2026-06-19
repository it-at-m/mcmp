package de.muenchen.mcmp.ontap;

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

    void deleteAllByClusterId(Long clusterId);

    List<OntapVolume> findAllByMountPathNfsIn(List<String> mountPathsNfs);

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

    @Query("SELECT v.volumeUuid, v.name, s.name FROM OntapVolume v " +
            "JOIN v.svm s " +
            "WHERE (LOWER(s.name) LIKE '%dcn' OR LOWER(s.name) LIKE '%dcc') " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND v.ontapQtrees IS EMPTY " +
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

    @Query("SELECT v.volumeUuid, v.name, s.name FROM OntapVolume v " +
            "JOIN v.svm s " +
            "JOIN v.ontapCifsShares cs " +
            "WHERE (:search IS NULL OR (LOWER(v.name) LIKE :search OR LOWER(cs.name) LIKE :search OR LOWER(s.name) LIKE :search OR LOWER(CONCAT(s.name, ':', cs.mountPathCifs)) LIKE :search)) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ") " +
            "GROUP BY v.id, v.volumeUuid, v.name, s.name")
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

    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN TRUE ELSE FALSE END " +
            "FROM OntapVolume v " +
            "JOIN v.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE v.volumeUuid = :uuid AND u.username = :username")
    Boolean canUserEditVolume(@Param("uuid") UUID uuid, @Param("username") String username);
}