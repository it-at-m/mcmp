package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntapSnapshotRepository extends JpaRepository<OntapSnapshot, Long> {

    List<OntapSnapshot> findAllByOntapClusterId(Long clusterId);
}