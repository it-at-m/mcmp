package de.muenchen.mcmp.greenit.metrics;

import java.time.LocalDate;
import java.util.List;

/**
 * Custom repository interface for managing partitioning operations
 * in the "server_metrics" table.
 * <p>
 * This interface provides methods for creating and dropping
 * table partitions within the database. These operations are
 * designed to support partitioned storage of server metric data,
 * enhancing query performance and maintenance operations by
 * organizing data into date-based partitions.
 */
public interface ServerMetricsRepositoryCustom {

    void dropPartition(LocalDate date);

    void createPartition(LocalDate date);

    /**
     * Insert a single metrics row while ignoring duplicates (server_id, created_at)
     * and skipping rows for non-existing servers to avoid FK violations.
     */
    void insertIgnoreDuplicatesAndMissingServers(ServerMetrics entry);

    /**
     * Batch insert metrics rows while ignoring duplicates (server_id, created_at)
     * and skipping rows for non-existing servers to avoid FK violations.
     */
    void batchInsertIgnoreDuplicatesAndMissingServers(List<ServerMetrics> entries);
}