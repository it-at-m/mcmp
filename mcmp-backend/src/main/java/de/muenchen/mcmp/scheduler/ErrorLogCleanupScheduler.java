package de.muenchen.mcmp.scheduler;

import de.muenchen.mcmp.errorlog.ErrorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler component for automated cleanup of old {@code error_log} entries.
 * <p>
 * Runs daily and deletes all entries older than the configured retention period
 * ({@code error-log.retention-days}), keeping the table from growing unbounded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorLogCleanupScheduler {

    private final ErrorLogService errorLogService;

    @Scheduled(cron = "0 30 2 * * *", zone = "Europe/Berlin")
    public void cleanupOldErrorLogs() {
        log.info("Starting scheduled cleanup of old error log entries");
        try {
            errorLogService.cleanupOldEntries();
            log.info("Cleanup of old error log entries completed successfully");
        } catch (Exception e) {
            log.error("Error during cleanup of old error log entries: {}", e.getMessage(), e);
        }
    }
}
