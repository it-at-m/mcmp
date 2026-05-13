package de.muenchen.mcmp.storagegrid;

import de.muenchen.mcmp.ontap.ConfigOntapCluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigStorageGridRepository extends JpaRepository<ConfigStorageGrid, Long> {
    Optional<ConfigStorageGrid> findByApiEndpoint(String apiEndpoint);
}
