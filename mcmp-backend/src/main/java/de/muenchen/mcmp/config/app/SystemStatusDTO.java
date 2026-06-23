package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.types.SystemMode;

public record SystemStatusDTO(SystemMode systemMode, String maintenanceMessage, String maintenanceMessageMarkdown) {
}