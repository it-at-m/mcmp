package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;

public interface LbPoolMonitorRepository extends JpaRepository<LbPoolMonitor, Long> {

    @Modifying
    void deleteAllByPoolIdIn(Collection<Long> poolIds);
}
