package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OntapSvmRepository extends JpaRepository<OntapSvm, Long> {
    List<OntapSvm> findAllByClusterId(Long clusterId);

    void deleteAllByClusterId(Long clusterId);

    Optional<OntapSvm> findBySwmUuid(UUID swmUuid);

    @Modifying
    @Query(value = """
            UPDATE cmp.ontap_svm
            SET snow_name = :name,
                snow_sys_id = :sysId, 
                snow_sys_class = :sysClass, 
                snow_last_discovered = :lastDiscovered
            WHERE id = :id
            """, nativeQuery = true)
    void updateSnowFields(@Param("id") Long id,
                          @Param("name") String name,
                          @Param("sysId") String sysId,
                          @Param("sysClass") String sysClass,
                          @Param("lastDiscovered") OffsetDateTime lastDiscovered);
}