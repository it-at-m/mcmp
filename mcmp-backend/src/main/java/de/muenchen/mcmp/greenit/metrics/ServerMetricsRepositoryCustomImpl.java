package de.muenchen.mcmp.greenit.metrics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implementation of the {@link ServerMetricsRepositoryCustom} interface
 * for managing partitioning operations in the "server_metrics" table.
 * <p>
 * This class provides concrete implementations for methods to create
 * and drop table partitions for the "server_metrics" table, enabling
 * efficient partitioned storage of server metric data. The partitions
 * are created based on specific date ranges, allowing for improved
 * performance and easier data management.
 * <p>
 * Operations:
 * - Creation of table partitions using the given date range.
 * - Dropping of table partitions based on the specified date.
 * <p>
 * Dependencies:
 * - Uses {@link EntityManager} for executing native SQL queries to
 * manage database partitions.
 */
@Repository
@AllArgsConstructor
public class ServerMetricsRepositoryCustomImpl implements ServerMetricsRepositoryCustom {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private static final String INSERT_IGNORE_DUPLICATES_AND_MISSING_SERVERS_SQL =
            "INSERT INTO cmp.server_metrics (server_id, created_at, cpu_util, mem_used_percent) " +
                    "SELECT ?, ?, ?, ? " +
                    "WHERE EXISTS (SELECT 1 FROM cmp.server s WHERE s.id = ?) " +
                    "ON CONFLICT (server_id, created_at) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager em;

    /**
     * Drops a table partition for the "server_metrics" table based on the specified date.
     * This operation removes the partitioned table corresponding to the given date
     * if it exists in the database.
     *
     * @param date the date for which the corresponding partition table should be dropped.
     *             The partition table name is constructed using the format "yyyy_MM_dd".
     */
    @Override
    public void dropPartition(LocalDate date) {
        final String sql = "DROP TABLE IF EXISTS cmp.server_metrics_" + FMT.format(date);
        //noinspection SqlSourceToSinkFlow
        em.createNativeQuery(sql).executeUpdate();
    }

    /**
     * Creates a table partition in the "server_metrics" table for the specified date.
     * This method generates a new partition based on the date provided, spanning from
     * the provided date (inclusive) to the next day (exclusive). If the partition already
     * exists, no new partition will be created.
     *
     * @param date the starting date of the partition. The partition covers data
     *             corresponding to the range from this date (inclusive) to the
     *             following day (exclusive). The partition table name is generated
     *             using the date format "yyyy_MM_dd".
     */
    @Override
    public void createPartition(LocalDate date) {
        final LocalDate next = date.plusDays(1);
        final String sql = String.format("CREATE TABLE IF NOT EXISTS cmp.server_metrics_%s PARTITION OF cmp.server_metrics FOR VALUES FROM ('%s') TO ('%s')", FMT.format(date), date, next);
        //noinspection SqlSourceToSinkFlow
        em.createNativeQuery(sql).executeUpdate();
    }

    /**
     * Insert a single metrics row while ignoring duplicates (server_id, created_at)
     * and skipping rows for non-existing servers to avoid FK violations.
     */
    @Override
    public void insertIgnoreDuplicatesAndMissingServers(final ServerMetrics entry) {
        if (entry == null) {
            return;
        }

        jdbcTemplate.update(INSERT_IGNORE_DUPLICATES_AND_MISSING_SERVERS_SQL, ps -> bindInsertParams(ps, entry));
    }

    /**
     * Batch insert metrics rows while ignoring duplicates (server_id, created_at)
     * and skipping rows for non-existing servers to avoid FK violations.
     */
    @Override
    public void batchInsertIgnoreDuplicatesAndMissingServers(final List<ServerMetrics> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                INSERT_IGNORE_DUPLICATES_AND_MISSING_SERVERS_SQL,
                entries,
                entries.size(),
                ServerMetricsRepositoryCustomImpl::bindInsertParams
        );
    }

    private static void bindInsertParams(final PreparedStatement ps, final ServerMetrics metrics) throws SQLException {
        ps.setLong(1, metrics.getServerId());
        ps.setObject(2, metrics.getCreatedAt());
        ps.setObject(3, metrics.getCpuUtil());
        ps.setObject(4, metrics.getMemUsedPercent());
        ps.setLong(5, metrics.getServerId());
    }

}