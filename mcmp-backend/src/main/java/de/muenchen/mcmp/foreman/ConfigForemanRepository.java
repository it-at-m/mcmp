package de.muenchen.mcmp.foreman;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConfigForemanRepository extends JpaRepository<ConfigForeman, Long> {

    Optional<ConfigForeman> findByFqdn(String fqdn);
    /**
     * Loads the {@code config_foreman} entries for the given FQDNs (case-insensitive).
     */
    List<ConfigForeman> findAllByFqdnInIgnoreCase(Collection<String> fqdns);
}