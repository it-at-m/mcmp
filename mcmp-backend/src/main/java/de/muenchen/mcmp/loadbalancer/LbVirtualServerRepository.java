package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LbVirtualServerRepository extends JpaRepository<LbVirtualServer, Long> {}
