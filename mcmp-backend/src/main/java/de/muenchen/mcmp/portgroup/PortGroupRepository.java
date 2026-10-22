package de.muenchen.mcmp.portgroup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PortGroupRepository extends JpaRepository<PortGroup, Long> {
    @Query(value = """
           SELECT *
           FROM port_group
           WHERE cloud_id = :cloudId
           """, nativeQuery = true)
    List<PortGroup> findAllByCloudId(Long cloudId);
}
