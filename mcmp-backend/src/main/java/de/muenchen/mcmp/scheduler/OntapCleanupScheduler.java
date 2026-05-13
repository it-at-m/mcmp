
package de.muenchen.mcmp.scheduler;

import de.muenchen.mcmp.clients.netapp.ontap.OntapImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler component for automated ONTAP cluster data cleanup operations.
 * <p>
 * This class manages the scheduled deletion of stale and outdated cluster data from the ONTAP
 * storage system. It provides automated maintenance capabilities to ensure that the ONTAP
 * data repository remains clean and optimized by removing obsolete cluster information on a
 * recurring basis.
 * <p>
 * <b>Scheduling Details:</b>
 * <ul>
 *   <li>Execution Time: Daily at 02:00 AM (UTC)</li>
 *   <li>Cron Expression: "0 0 2 * * *" (every day at 2 AM)</li>
 *   <li>Timezone: Server default (configurable via application properties)</li>
 * </ul>
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li>Triggers the cleanup process of deprecated ONTAP cluster data</li>
 *   <li>Handles exceptions gracefully without interrupting the scheduled task</li>
 *   <li>Logs the execution status and any errors for monitoring and troubleshooting</li>
 * </ul>
 * <p>
 * <b>Dependencies:</b>
 * <ul>
 *   <li>{@link OntapImportService} - Service responsible for executing the actual cleanup operation</li>
 * </ul>
 * <p>
 * <b>Error Handling:</b>
 * In case of errors during the cleanup process, exceptions are caught and logged without
 * re-throwing, ensuring the scheduler remains functional for future execution cycles.
 * <p>
 * <b>Monitoring:</b>
 * All execution steps (start, completion, and errors) are logged at appropriate levels
 * (INFO for normal operations, ERROR for failures) to support monitoring and alerting systems.
 *
 * @see OntapImportService
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OntapCleanupScheduler {

    private final OntapImportService ontapImportService;

    /**
     * Executes the scheduled cleanup of stale ONTAP cluster data.
     * <p>
     * This method is automatically invoked daily at 02:00 AM by the Spring Task Scheduler.
     * It performs the following operations in sequence:
     * <ol>
     *   <li>Logs the initiation of the cleanup process</li>
     *   <li>Invokes the ONTAP import service to delete stale cluster data</li>
     *   <li>Logs successful completion of the cleanup process</li>
     *   <li>Catches and logs any exceptions that occur during execution</li>
     * </ol>
     * <p>
     * <b>Execution Schedule:</b> Daily at 02:00 AM
     * <p>
     * <b>Exception Handling:</b> All exceptions are caught and logged but not re-thrown,
     * allowing the scheduler to continue functioning for the next scheduled execution.
     * <p>
     * <b>Logging Output:</b>
     * <ul>
     *   <li>INFO: "Starting scheduled cleanup of stale ONTAP data"</li>
     *   <li>INFO: "Cleanup of stale ONTAP data completed successfully"</li>
     *   <li>ERROR: "Error during cleanup of stale ONTAP data: {message}" (if exception occurs)</li>
     * </ul>
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Berlin")
    public void cleanupStaleData() {
        log.info("Starting scheduled cleanup of stale ONTAP data");
        try {
            ontapImportService.deleteStaleClusterData();
            log.info("Cleanup of stale ONTAP data completed successfully");
        } catch (Exception e) {
            log.error("Error during cleanup of stale ONTAP data: {}", e.getMessage(), e);
        }
    }
}