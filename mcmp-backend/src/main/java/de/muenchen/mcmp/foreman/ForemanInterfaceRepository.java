package de.muenchen.mcmp.foreman;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ForemanInterfaceRepository extends JpaRepository<ForemanInterface, Long> {

    Optional<ForemanInterface> findByForemanIdAndForemanHostId(Long foremanId, Long foremanHostId);

    /**
     * Loads all Foreman interfaces of the given hosts in one query.
     */
    List<ForemanInterface> findAllByForemanHostIdIn(Collection<Long> foremanHostIds);
}