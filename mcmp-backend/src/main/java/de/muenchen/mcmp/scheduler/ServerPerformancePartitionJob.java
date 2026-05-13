package de.muenchen.mcmp.scheduler;

import de.muenchen.mcmp.greenit.metrics.ServerMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerPerformancePartitionJob {

    private final ServerMetricsRepository serverMetricsRepository;

    @Lazy
    @Autowired
    private ServerPerformancePartitionJob self;

    /**
     * Performs a scheduled rotation of database partitions for server performance data.
     * <p>
     * This method is invoked daily at 00:01 AM and is responsible for managing the partitions
     * of the `server_performance` table. It executes the following operations:
     * <p>
     * 1. Removes the partition corresponding to the data that is 30 days old, effectively
     *    purging outdated server performance records.
     * 2. Creates new partitions for the upcoming 7 days as a safety buffer to ensure that
     *    partitions are available for future data inserts. This operation is idempotent
     *    and ensures no duplicate partitions are created.
     * <p>
     * The method is annotated with `@Transactional` to ensure that all partition operations
     * are executed within a single transaction, guaranteeing atomicity of the process.
     * <p>
     * Scheduling is handled via the Spring `@Scheduled` annotation, configured to execute
     * at a specific time (00:01 AM) using a Cron expression.
     * <p>
     * Dependencies:
     * - {@code serverPerformanceRepository} is used to perform the partition-related operations:
     *   - `dropPartition(LocalDate date)` removes a specific partition for the given date.
     *   - `createPartition(LocalDate date)` creates a new partition for the specified date.
     * <p>
     * Note:
     * - The method assumes that database partitioning is pre-configured for the
     *   `server_performance` table, and the operations are safe and consistent for the
     *   underlying schema design.
     * - Proper error handling for database operations is expected to be handled within
     *   the repository layer or database configuration.
     */
    @Scheduled(cron = "0 1 0 * * *", zone = "Europe/Berlin")
    @Transactional
    public void rotate() {
        serverMetricsRepository.dropPartition(LocalDate.now().minusDays(30));

        for (int i = 1; i <= 7; i++) {
            serverMetricsRepository.createPartition(LocalDate.now().plusDays(i));
        }
    }

    /**
     * Executes the partition rotation job immediately upon application startup
     * if the active Spring profile matches either "local" or "docker".
     * <p>
     * This method is triggered by the {@code ApplicationReadyEvent}, ensuring
     * that it runs only after the application is fully initialized. It utilizes
     * the {@code Profile} annotation to conditionally execute the logic based on
     * the current runtime environment, specifically when the application is
     * configured to run locally or in a Dockerized environment.
     * <p>
     * The method delegates the partition management logic to the {@code rotate()}
     * method, ensuring that database partitions for server performance data are
     * properly cleaned up and pre-created as per the defined business logic.
     * <p>
     * Note:
     * - This method is invoked only when the profiles "local" or "docker" are active.
     * - Logging is performed to indicate that the process has been triggered
     *   during application startup.
     * - The invocation of {@code rotate()} is done via the {@code self} proxy to
     *   ensure proper handling of transactional and AOP-related behaviors.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Profile("local | docker")
    public void runOnStartupIfLocal() {
        log.info("Local/Docker profile detected – running partition job on startup");
        self.rotate();
    }
}