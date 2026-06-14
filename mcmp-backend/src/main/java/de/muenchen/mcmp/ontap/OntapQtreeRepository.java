package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT q.id, q.name, s.name FROM OntapQtree q " +
            "JOIN q.volume v " +
            "JOIN v.svm s " +
            "WHERE LOWER(s.name) LIKE '%dcn' " +
            "AND v.ontapCifsShares IS EMPTY " +
            "AND (:search IS NULL OR LOWER(q.name) LIKE :search) " +
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

    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN TRUE ELSE FALSE END " +
            "FROM OntapQtree q " +
            "JOIN q.volume v " +
            "JOIN q.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE q.id = :id AND v.ontapCifsShares IS EMPTY AND u.username = :username")
    Boolean canUserEditQtree(@Param("id") Long id, @Param("username") String username);
}