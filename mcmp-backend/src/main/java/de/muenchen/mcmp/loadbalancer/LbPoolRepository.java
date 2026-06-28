package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LbPoolRepository extends JpaRepository<LbPool, Long> {
    List<LbPool> findAllByNameIn(Collection<String> names);
}
