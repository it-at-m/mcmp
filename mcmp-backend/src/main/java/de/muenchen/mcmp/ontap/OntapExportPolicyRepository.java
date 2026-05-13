package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntapExportPolicyRepository extends JpaRepository<OntapExportPolicy, Long> {
    List<OntapExportPolicy> findAllByClusterId(Long clusterId);

    void deleteAllByClusterId(Long clusterId);
}