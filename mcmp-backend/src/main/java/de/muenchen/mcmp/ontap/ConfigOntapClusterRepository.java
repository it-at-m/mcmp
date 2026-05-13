package de.muenchen.mcmp.ontap;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigOntapClusterRepository extends JpaRepository<ConfigOntapCluster, Long> {
    Optional<ConfigOntapCluster> findByApiEndpoint(String apiEndpoint);
}