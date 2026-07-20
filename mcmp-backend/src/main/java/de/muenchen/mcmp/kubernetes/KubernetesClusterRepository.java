package de.muenchen.mcmp.kubernetes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KubernetesClusterRepository extends JpaRepository<KubernetesCluster, Long> {
    Optional<KubernetesCluster> findBySysId(String sysId);
}