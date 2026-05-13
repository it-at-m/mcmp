package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OntapVolumeServerMountRepository extends JpaRepository<OntapVolumeServerMount, Long> {

    List<OntapVolumeServerMount> findAllByServerId(Long serverId);

    @Query("SELECT m FROM OntapVolumeServerMount m " +
            "JOIN FETCH m.ontapVolume v " + // Fetch volume to avoid N+1 and for permission check context
            "LEFT JOIN FETCH v.svm " +       // Fetch SVM for protocol determination
            "LEFT JOIN FETCH v.appservices " + // Fetch appservices for DTO mapping
            "LEFT JOIN FETCH v.ontapCifsShares " + // Fetch CIFS shares if needed
            "WHERE m.serverId = :serverId " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR " +
            "   EXISTS (SELECT 1 FROM v.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<OntapVolumeServerMount> findAllByServerIdWithPermissions(@Param("serverId") Long serverId,
                                                                  @Param("username") String username,
                                                                  @Param("isAdmin") boolean isAdmin,
                                                                  @Param("isReadonly") boolean isReadonly);
}