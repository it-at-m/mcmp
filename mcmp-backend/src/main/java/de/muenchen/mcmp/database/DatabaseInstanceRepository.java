package de.muenchen.mcmp.database;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DatabaseInstanceRepository extends JpaRepository<DatabaseInstance, Long> {

    @Query("SELECT DISTINCT d FROM DatabaseInstance d LEFT JOIN FETCH d.appservices LEFT JOIN FETCH d.servers")
    List<DatabaseInstance> findAllWithAppservicesAndServers();

    @Query("""
            SELECT DISTINCT d
            FROM DatabaseInstance d
            LEFT JOIN FETCH d.appservices
            LEFT JOIN FETCH d.servers
            WHERE d.snowSysId = :snowSysId
            """)
    Optional<DatabaseInstance> findBySnowSysIdWithAppservicesAndServers(@Param("snowSysId") String snowSysId);

    @Query(value = "SELECT id FROM cmp.database_instance WHERE snow_sys_id = :snowSysId", nativeQuery = true)
    Optional<Long> findIdBySnowSysId(@Param("snowSysId") String snowSysId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.database_instance (
                snow_name,
                snow_sys_id,
                snow_sys_class,
                snow_last_discovered,
                snow_version
            )
            VALUES (
                :snowName,
                :snowSysId,
                :snowSysClass,
                :snowLastDiscovered,
                :snowVersion
            )
            ON CONFLICT (snow_sys_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfNotExists(@Param("snowName") String snowName,
                           @Param("snowSysId") String snowSysId,
                           @Param("snowSysClass") String snowSysClass,
                           @Param("snowLastDiscovered") OffsetDateTime snowLastDiscovered,
                           @Param("snowVersion") String snowVersion);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE cmp.database_instance
            SET snow_name = :snowName,
                snow_sys_class = :snowSysClass,
                snow_last_discovered = :snowLastDiscovered,
                snow_version = :snowVersion,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """, nativeQuery = true)
    void updateSnowFields(@Param("id") Long id,
                          @Param("snowName") String snowName,
                          @Param("snowSysClass") String snowSysClass,
                          @Param("snowLastDiscovered") OffsetDateTime snowLastDiscovered,
                          @Param("snowVersion") String snowVersion);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cmp.database_instance_has_appservices WHERE database_instance_id = :databaseInstanceId", nativeQuery = true)
    void deleteAppServiceAssociations(@Param("databaseInstanceId") Long databaseInstanceId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.database_instance_has_appservices
            WHERE database_instance_id = :databaseInstanceId
              AND appservice_id NOT IN (
                  SELECT id FROM cmp.appservice WHERE number IN :numbers
              )
            """, nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("databaseInstanceId") Long databaseInstanceId,
                                              @Param("numbers") List<String> numbers);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.database_instance_has_appservices (database_instance_id, appservice_id)
            SELECT :databaseInstanceId, id
            FROM cmp.appservice
            WHERE number IN :numbers
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addAppServiceAssociations(@Param("databaseInstanceId") Long databaseInstanceId,
                                   @Param("numbers") List<String> numbers);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cmp.server_has_database_instances WHERE database_instance_id = :databaseInstanceId", nativeQuery = true)
    void deleteServerAssociations(@Param("databaseInstanceId") Long databaseInstanceId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.server_has_database_instances
            WHERE database_instance_id = :databaseInstanceId
              AND server_id NOT IN (
                  SELECT id
                  FROM cmp.server
                  WHERE snow_server_sys_id IN :serverSysIds
              )
            """, nativeQuery = true)
    void deleteObsoleteServerAssociations(@Param("databaseInstanceId") Long databaseInstanceId,
                                          @Param("serverSysIds") List<String> serverSysIds);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.server_has_database_instances (database_instance_id, server_id)
            SELECT :databaseInstanceId, id
            FROM cmp.server
            WHERE snow_server_sys_id IN :serverSysIds
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addServerAssociations(@Param("databaseInstanceId") Long databaseInstanceId,
                               @Param("serverSysIds") List<String> serverSysIds);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.database_instance_has_appservices
            WHERE database_instance_id = :databaseInstanceId
            """, nativeQuery = true)
    void deleteAssociationsBeforeDelete(@Param("databaseInstanceId") Long databaseInstanceId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.server_has_database_instances
            WHERE database_instance_id = :databaseInstanceId
            """, nativeQuery = true)
    void deleteServerAssociationsBeforeDelete(@Param("databaseInstanceId") Long databaseInstanceId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM cmp.database_instance WHERE id = :id", nativeQuery = true)
    void deleteDatabaseInstanceById(@Param("id") Long id);
}
