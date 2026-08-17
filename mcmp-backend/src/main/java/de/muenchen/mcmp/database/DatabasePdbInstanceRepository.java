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
public interface DatabasePdbInstanceRepository extends JpaRepository<DatabasePdbInstance, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM DatabasePdbInstance p
            LEFT JOIN FETCH p.appservices
            LEFT JOIN FETCH p.databaseInstances
            """)
    List<DatabasePdbInstance> findAllWithAppservicesAndDatabaseInstances();

    @Query("""
            SELECT DISTINCT p
            FROM DatabasePdbInstance p
            LEFT JOIN FETCH p.appservices
            LEFT JOIN FETCH p.databaseInstances
            WHERE p.snowSysId = :snowSysId
            """)
    Optional<DatabasePdbInstance> findBySnowSysIdWithAppservicesAndDatabaseInstances(@Param("snowSysId") String snowSysId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.database_pdb_instance (
                snow_name,
                snow_sys_id,
                snow_sys_class,
                snow_last_discovered,
                snow_pdb
            )
            VALUES (
                :snowName,
                :snowSysId,
                :snowSysClass,
                :snowLastDiscovered,
                :snowPdb
            )
            ON CONFLICT (snow_sys_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfNotExists(@Param("snowName") String snowName,
                           @Param("snowSysId") String snowSysId,
                           @Param("snowSysClass") String snowSysClass,
                           @Param("snowLastDiscovered") OffsetDateTime snowLastDiscovered,
                           @Param("snowPdb") String snowPdb);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE cmp.database_pdb_instance
            SET snow_name = :snowName,
                snow_sys_class = :snowSysClass,
                snow_last_discovered = :snowLastDiscovered,
                snow_pdb = :snowPdb,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """, nativeQuery = true)
    void updateSnowFields(@Param("id") Long id,
                          @Param("snowName") String snowName,
                          @Param("snowSysClass") String snowSysClass,
                          @Param("snowLastDiscovered") OffsetDateTime snowLastDiscovered,
                          @Param("snowPdb") String snowPdb);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.database_pdb_instance_has_appservices
            WHERE database_pdb_instance_id = :databasePdbInstanceId
            """, nativeQuery = true)
    void deleteAppServiceAssociations(@Param("databasePdbInstanceId") Long databasePdbInstanceId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.database_pdb_instance_has_appservices
            WHERE database_pdb_instance_id = :databasePdbInstanceId
              AND appservice_id NOT IN (
                  SELECT id FROM cmp.appservice WHERE number IN :numbers
              )
            """, nativeQuery = true)
    void deleteObsoleteAppServiceAssociations(@Param("databasePdbInstanceId") Long databasePdbInstanceId,
                                              @Param("numbers") List<String> numbers);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.database_pdb_instance_has_appservices (database_pdb_instance_id, appservice_id)
            SELECT :databasePdbInstanceId, id
            FROM cmp.appservice
            WHERE number IN :numbers
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addAppServiceAssociations(@Param("databasePdbInstanceId") Long databasePdbInstanceId,
                                   @Param("numbers") List<String> numbers);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.database_instance_has_database_pdb_instances
            WHERE database_pdb_instance_id = :databasePdbInstanceId
            """, nativeQuery = true)
    void deleteDatabaseInstanceAssociations(@Param("databasePdbInstanceId") Long databasePdbInstanceId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.database_instance_has_database_pdb_instances
            WHERE database_pdb_instance_id = :databasePdbInstanceId
              AND database_instance_id NOT IN (
                  SELECT id FROM cmp.database_instance WHERE snow_sys_id IN :databaseInstanceSysIds
              )
            """, nativeQuery = true)
    void deleteObsoleteDatabaseInstanceAssociations(@Param("databasePdbInstanceId") Long databasePdbInstanceId,
                                                    @Param("databaseInstanceSysIds") List<String> databaseInstanceSysIds);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO cmp.database_instance_has_database_pdb_instances (database_pdb_instance_id, database_instance_id)
            SELECT :databasePdbInstanceId, id
            FROM cmp.database_instance
            WHERE snow_sys_id IN :databaseInstanceSysIds
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addDatabaseInstanceAssociations(@Param("databasePdbInstanceId") Long databasePdbInstanceId,
                                         @Param("databaseInstanceSysIds") List<String> databaseInstanceSysIds);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM cmp.database_pdb_instance
            WHERE id = :id
            """, nativeQuery = true)
    void deleteDatabasePdbInstanceById(@Param("id") Long id);

    @Query(value = """
            SELECT s.fqdn AS fqdn,
                   dipdbi.snow_pdb AS pdb
            FROM cmp.server s
            JOIN cmp.server_has_database_instances sdi
                ON sdi.server_id = s.id
            JOIN cmp.database_instance_has_database_pdb_instances dipdb
                ON dipdb.database_instance_id = sdi.database_instance_id
            JOIN cmp.database_pdb_instance dipdbi
                ON dipdbi.id = dipdb.database_pdb_instance_id
            WHERE s.managed = true
              AND s.db_oracle = true
              AND s.power_state = 'poweredOn'
            """, nativeQuery = true)
    List<DatabasePdbInstanceServerDTO> findManagedPoweredOnOracleServerPdbInstances();

}