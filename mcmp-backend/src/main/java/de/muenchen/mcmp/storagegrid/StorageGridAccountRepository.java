package de.muenchen.mcmp.storagegrid;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StorageGridAccountRepository extends JpaRepository<StorageGridAccount, Long> {

    List<StorageGridAccount> findAllByConfigStorageGridId(Long configStorageGridId);

    void deleteAllByConfigStorageGridId(Long configStorageGridId);

    @Modifying
    @Query(value = "UPDATE storagegrid_accounts SET snow_name = :snowName, snow_sys_id = :snowSysId, snow_sys_class = :snowSysClass, updated_at = CURRENT_TIMESTAMP WHERE id = :id", nativeQuery = true)
    void updateSnowFields(@Param("id") Long id, @Param("snowName") String snowName, @Param("snowSysId") String snowSysId, @Param("snowSysClass") String snowSysClass);

    @Modifying
    @Query(value = "DELETE FROM storagegrid_accounts_has_appservices WHERE storagegrid_accounts_id = :accountId", nativeQuery = true)
    void deleteAppServiceAssociations(@Param("accountId") Long accountId);

    @Modifying
    @Query(value = "INSERT INTO storagegrid_accounts_has_appservices (storagegrid_accounts_id, appservice_id) " +
            "SELECT :accountId, a.id FROM appservice a WHERE a.number IN :appServiceNumbers " +
            "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addAppServiceAssociations(@Param("accountId") Long accountId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Modifying
    @Query(value = "DELETE FROM storagegrid_accounts_has_appservices saha " +
            "WHERE saha.storagegrid_accounts_id = :accountId " +
            "AND saha.appservice_id NOT IN (" +
            "    SELECT a.id FROM appservice a " +
            "    WHERE a.number IN :appServiceNumbers" +
            ")", nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("accountId") Long accountId, @Param("appServiceNumbers") List<String> appServiceNumbers);

    @Query("SELECT DISTINCT a FROM StorageGridAccount a LEFT JOIN FETCH a.appservices")
    List<StorageGridAccount> findAllWithAppservices();

}
