package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OntapQtreeServerMountRepository extends JpaRepository<OntapQtreeServerMount, Long> {

    List<OntapQtreeServerMount> findAllByServerId(Long serverId);

    @Query("SELECT m FROM OntapQtreeServerMount m " +
            "JOIN FETCH m.ontapQtree q " +
            "JOIN FETCH q.volume v " + // Fetch volume for protocol info
            "LEFT JOIN FETCH v.svm " +
            "LEFT JOIN FETCH q.appservices " +
            "WHERE m.serverId = :serverId " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM q.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<OntapQtreeServerMount> findAllByServerIdWithPermissions(@Param("serverId") Long serverId,
                                                                 @Param("username") String username,
                                                                 @Param("isAdmin") boolean isAdmin,
                                                                 @Param("isReadonly") boolean isReadonly,
                                                                 @Param("isStorage") boolean isStorage,
                                                                 @Param("isOperator") boolean isOperator);
}
