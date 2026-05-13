package de.muenchen.mcmp.greenit.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ServerMetricsRepository extends JpaRepository<ServerMetrics, Long>, ServerMetricsRepositoryCustom {

    List<ServerMetrics> findByServerIdOrderByIdAsc(Long serverId);

    @Query(value = """
        SELECT s.id
        FROM cmp.cloud c
        JOIN cmp.server s ON c.id = s.cloud_id
        WHERE c.green_it_enabled = true
        AND EXISTS (
            SELECT 1
            FROM cmp.server_metrics sm
            WHERE sm.server_id = s.id
            AND sm.created_at >= now() - interval '30 days'
            LIMIT 1
        )
        ORDER BY s.id ASC;
    """, nativeQuery = true)
    List<Long> findServerIdsWithMetricsAndGreenItEnabled();
}