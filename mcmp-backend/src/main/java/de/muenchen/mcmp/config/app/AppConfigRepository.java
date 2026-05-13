package de.muenchen.mcmp.config.app;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {

    Optional<AppConfig> findByConfigKey(String configKey);

    List<AppConfig> findByConfigKeyIn(List<String> configKeys);
}