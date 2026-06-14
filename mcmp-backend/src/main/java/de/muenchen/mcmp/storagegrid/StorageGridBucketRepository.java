package de.muenchen.mcmp.storagegrid;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorageGridBucketRepository extends JpaRepository<StorageGridBucket, Long> {

    List<StorageGridBucket> findAllByStorageGridAccountIdIn(List<Long> accountIds);

    @Query("SELECT COUNT(b) FROM StorageGridBucket b " +
            "WHERE (:search IS NULL OR LOWER(b.name) LIKE :search) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM b.storageGridAccount.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    long count(@Param("search") String search,
               @Param("username") String username,
               @Param("isAdmin") boolean isAdmin,
               @Param("isReadonly") boolean isReadonly,
               @Param("isStorage") boolean isStorage,
               @Param("isOperator") boolean isOperator);

    @Query("SELECT b FROM StorageGridBucket b " +
            "JOIN FETCH b.storageGridAccount a " +
            "WHERE b.id = :id " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM b.storageGridAccount.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    Optional<StorageGridBucket> findByIdWithPermissions(@Param("id") Long id,
                                                        @Param("username") String username,
                                                        @Param("isAdmin") boolean isAdmin,
                                                        @Param("isReadonly") boolean isReadonly,
                                                        @Param("isStorage") boolean isStorage,
                                                        @Param("isOperator") boolean isOperator);

    @Query("SELECT b.id, b.name FROM StorageGridBucket b " +
            "WHERE (:search IS NULL OR LOWER(b.name) LIKE :search) " +
            "AND (" +
            "   :isAdmin = TRUE OR :isReadonly = TRUE OR :isStorage = TRUE OR :isOperator = TRUE OR " +
            "   EXISTS (SELECT 1 FROM b.storageGridAccount.appservices a JOIN a.changeGroup g JOIN g.users u WHERE u.username = :username)" +
            ")")
    List<Object[]> findBucketListItems(@Param("search") String search,
                                       @Param("username") String username,
                                       @Param("isAdmin") boolean isAdmin,
                                       @Param("isReadonly") boolean isReadonly,
                                       @Param("isStorage") boolean isStorage,
                                       @Param("isOperator") boolean isOperator);

    @Query("SELECT b FROM StorageGridBucket b " +
            "LEFT JOIN FETCH b.storageGridAccount acc " +
            "LEFT JOIN FETCH acc.appservices a " +
            "WHERE b.id IN :ids")
    List<StorageGridBucket> findByIdsWithAppservices(@Param("ids") List<Long> ids);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END " +
            "FROM StorageGridBucket b " +
            "JOIN b.storageGridAccount acc " +
            "JOIN acc.appservices a " +
            "JOIN a.changeGroup g " +
            "JOIN g.users u " +
            "WHERE b.id = :id AND u.username = :username")
    Boolean canUserEditBucket(@Param("id") Long id, @Param("username") String username);
}
