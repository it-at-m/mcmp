package de.muenchen.mcmp.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFavoriteStorageRepository extends JpaRepository<UserFavoriteStorage, UserFavoriteStorageId> {

    @Query(value = """
        SELECT ufs.storage_type AS storageType, ufs.storage_uuid AS storageUuid
        FROM cmp.user_favorite_storage ufs
        JOIN cmp."user" u ON ufs.user_id = u.id
        WHERE u.username = :username
    """, nativeQuery = true)
    List<FavoriteStorageKey> findFavoritesByUsername(@Param("username") String username);

    @Modifying
    @Query(value = """
        INSERT INTO cmp.user_favorite_storage (user_id, storage_type, storage_uuid)
        SELECT u.id, :storageType, :storageUuid FROM cmp."user" u WHERE u.username = :username
        ON CONFLICT DO NOTHING
    """, nativeQuery = true)
    void addStorageToFavorites(@Param("storageType") String storageType, @Param("storageUuid") String storageUuid, @Param("username") String username);

    @Modifying
    @Query(value = """
        DELETE FROM cmp.user_favorite_storage ufs
        WHERE ufs.storage_type = :storageType
          AND ufs.storage_uuid = :storageUuid
          AND ufs.user_id = (SELECT u.id FROM cmp."user" u WHERE u.username = :username)
    """, nativeQuery = true)
    void removeStorageFromFavorites(@Param("storageType") String storageType, @Param("storageUuid") String storageUuid, @Param("username") String username);

    interface FavoriteStorageKey {
        String getStorageType();
        String getStorageUuid();
    }
}
