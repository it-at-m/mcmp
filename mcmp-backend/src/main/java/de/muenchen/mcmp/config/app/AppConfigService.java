package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.common.NotFoundException; // Assuming this exists in the project
import de.muenchen.mcmp.markdown.MarkdownService;
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
    public static final String CONFIG_KEY_MAINTENANCE_MESSAGE_MARKDOWN = "MAINTENANCE_MESSAGE_MARKDOWN";

    private final AppConfigRepository appConfigRepository;
    private final AppConfigCacheService appConfigCacheService;
    private final MarkdownService markdownService;

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
        final String htmlMessage = configMap.getOrDefault(CONFIG_KEY_MAINTENANCE_MESSAGE, "");
        final String markdownMessage = configMap.getOrDefault(CONFIG_KEY_MAINTENANCE_MESSAGE_MARKDOWN, "");

        if (modeStr == null) {
            throw new NotFoundException("System configuration not found");
        }

        return new SystemStatusDTO(SystemMode.valueOf(modeStr), htmlMessage, markdownMessage);
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
        // Use markdown from DTO if provided, otherwise fallback to message field
        final String markdown = (status.maintenanceMessageMarkdown() != null && !status.maintenanceMessageMarkdown().isBlank())
                ? status.maintenanceMessageMarkdown().trim()
                : status.maintenanceMessage().trim();

        if (markdown.isBlank()) {
            throw new IllegalArgumentException("Maintenance message must not be empty");
        }

        final String html = markdownService.convertToHtml(markdown);

        updateConfig(CONFIG_KEY_SYSTEM_MODE, status.systemMode().name());
        updateConfig(CONFIG_KEY_MAINTENANCE_MESSAGE, html);
        updateConfig(CONFIG_KEY_MAINTENANCE_MESSAGE_MARKDOWN, markdown);

        appConfigCacheService.refreshCache();
        log.info("System status updated: Mode={}, Markdown and HTML messages updated", status.systemMode());
    }

    // Helper method to retrieve a config entry
    private AppConfig getConfig(final String key) {
        return appConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new NotFoundException("Config not found for key: " + key));
    }

    // Helper method to update a config entry
    private void updateConfig(final String key, final String value) {
        final String username = AuthUtils.getUsername();
        final AppConfig config = appConfigRepository.findByConfigKey(key)
                .orElseGet(() -> {
                    AppConfig newConfig = new AppConfig();
                    newConfig.setConfigKey(key);
                    return newConfig;
                });
        config.setConfigValue(value);
        config.setUpdatedBy(username);
        config.setUpdatedAt(new Date());
        appConfigRepository.save(config);
        log.info("AppConfig updated: {} = {} by {}", key, value, username);
    }
}