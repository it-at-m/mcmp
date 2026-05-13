package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.common.NotFoundException;
import de.muenchen.mcmp.security.IsAdmin;
import de.muenchen.mcmp.types.SystemMode;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for managing application configuration settings,
 * such as system mode and maintenance message.
 */
@RestController
@AllArgsConstructor
@Slf4j
@RequestMapping("/app-config")
public class AppConfigController {

    private final AppConfigService appConfigService;
    private final AppConfigCacheService appConfigCacheService;

    /**
     * Retrieves the current system status (mode and maintenance message).
     *
     * @return the current system status
     */
    @GetMapping("/system-status")
    public ResponseEntity<SystemStatusDTO> getSystemStatus() {
        return ResponseEntity.ok(new SystemStatusDTO(
                appConfigCacheService.getSystemMode(),
                appConfigCacheService.getMaintenanceMessage()
        ));
    }

    /**
     * Updates the system status (mode and maintenance message).
     * Requires admin privileges.
     *
     * @param status the new system status
     * @return HTTP 204 No Content on success
     */
    @IsAdmin
    @PutMapping("/system-status")
    public ResponseEntity<Void> updateSystemStatus(@RequestBody final SystemStatusDTO status) {
        appConfigService.updateSystemStatus(status);
        return ResponseEntity.noContent().build();
    }
}