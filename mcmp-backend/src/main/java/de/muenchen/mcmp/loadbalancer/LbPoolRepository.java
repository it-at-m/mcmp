package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LbPoolRepository extends JpaRepository<LbPool, Long> {}
