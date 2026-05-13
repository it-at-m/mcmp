package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.types.SystemMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppConfigCacheService {

    private final AppConfigRepository appConfigRepository;

    private final AtomicReference<SystemMode> cachedSystemMode = new AtomicReference<>(SystemMode.NORMAL);
    private final AtomicReference<String> cachedMaintenanceMessage = new AtomicReference<>("");

    @PostConstruct
    public void init() {
        refreshCache();
    }

    @Scheduled(fixedRate = 10000)
    public void refreshCache() {
        try {
            final List<AppConfig> configs = appConfigRepository.findByConfigKeyIn(
                    List.of(AppConfigService.CONFIG_KEY_SYSTEM_MODE, AppConfigService.CONFIG_KEY_MAINTENANCE_MESSAGE));

            final Map<String, String> configMap = configs.stream()
                    .collect(Collectors.toMap(AppConfig::getConfigKey, AppConfig::getConfigValue));

            final String modeStr = configMap.get(AppConfigService.CONFIG_KEY_SYSTEM_MODE);
            final String message = configMap.getOrDefault(AppConfigService.CONFIG_KEY_MAINTENANCE_MESSAGE, "");

            if (modeStr != null) {
                cachedSystemMode.set(SystemMode.valueOf(modeStr));
                cachedMaintenanceMessage.set(message);
                log.trace("AppConfig cache refreshed: Mode={}, Message={}", modeStr, message);
            }
        } catch (Exception e) {
            log.error("Failed to refresh AppConfig cache from database: {}", e.getMessage());
        }
    }

    public SystemMode getSystemMode() {
        return cachedSystemMode.get();
    }

    public String getMaintenanceMessage() {
        return cachedMaintenanceMessage.get();
    }

    public boolean isMaintenanceMode() {
        return cachedSystemMode.get() != SystemMode.NORMAL;
    }
}