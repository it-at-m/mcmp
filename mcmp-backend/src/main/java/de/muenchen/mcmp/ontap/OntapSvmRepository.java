package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntapSvmRepository extends JpaRepository<OntapSvm, Long> {
    List<OntapSvm> findAllByClusterId(Long clusterId);

    void deleteAllByClusterId(Long clusterId);
}