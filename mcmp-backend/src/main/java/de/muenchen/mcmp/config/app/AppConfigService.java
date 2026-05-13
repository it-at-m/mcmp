package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.common.NotFoundException; // Assuming this exists in the project
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.types.SystemMode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class AppConfigService {

    public static final String CONFIG_KEY_SYSTEM_MODE = "SYSTEM_MODE";
    public static final String CONFIG_KEY_MAINTENANCE_MESSAGE = "MAINTENANCE_MESSAGE";

    private final AppConfigRepository appConfigRepository;
    private final AppConfigCacheService appConfigCacheService;

    /**
     * Retrieves both system mode and maintenance message in a single database call.
     * @return DTO containing system mode and maintenance message
     */
    public SystemStatusDTO getSystemStatus() {
        final List<AppConfig> configs = appConfigRepository.findByConfigKeyIn(
                List.of(CONFIG_KEY_SYSTEM_MODE, CONFIG_KEY_MAINTENANCE_MESSAGE));

        final Map<String, String> configMap = configs.stream()
                .collect(Collectors.toMap(AppConfig::getConfigKey, AppConfig::getConfigValue));

        final String modeStr = configMap.get(CONFIG_KEY_SYSTEM_MODE);
        final String message = configMap.getOrDefault(CONFIG_KEY_MAINTENANCE_MESSAGE, "");

        if (modeStr == null) {
            throw new NotFoundException("System configuration not found");
        }

        return new SystemStatusDTO(SystemMode.valueOf(modeStr), message);
    }

    /**
     * Updates both system mode and maintenance message.
     * @param status the new system status
     * @throws IllegalArgumentException if input is invalid
     */
    public void updateSystemStatus(final SystemStatusDTO status) {
        if (status.systemMode() == null) {
            throw new IllegalArgumentException("System mode must not be null");
        }
        if (status.maintenanceMessage() == null || status.maintenanceMessage().isBlank()) {
            throw new IllegalArgumentException("Maintenance message must not be empty");
        }

        updateConfig(CONFIG_KEY_SYSTEM_MODE, status.systemMode().name());
        updateConfig(CONFIG_KEY_MAINTENANCE_MESSAGE, status.maintenanceMessage().trim());

        appConfigCacheService.refreshCache();
        log.info("System status updated: Mode={}, Message updated", status.systemMode());
    }

    // Helper method to retrieve a config entry
    private AppConfig getConfig(final String key) {
        return appConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new NotFoundException("Config not found for key: " + key));
    }

    // Helper method to update a config entry
    private void updateConfig(final String key, final String value) {
        final String username = AuthUtils.getUsername();
        final AppConfig config = getConfig(key);
        config.setConfigValue(value);
        config.setUpdatedBy(username);
        config.setUpdatedAt(new Date());
        appConfigRepository.save(config);
        log.info("AppConfig updated: {} = {} by {}", key, value, username);
    }
}