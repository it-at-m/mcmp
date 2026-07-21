package de.muenchen.mcmp.loadbalancer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;

public interface LbVirtualServerPoolRefRepository extends JpaRepository<LbVirtualServerPoolRef, Long> {

    @Modifying
    void deleteAllByVirtualServerIdIn(Collection<Long> virtualServerIds);
}
