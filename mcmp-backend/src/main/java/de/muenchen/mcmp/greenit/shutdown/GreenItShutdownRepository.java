package de.muenchen.mcmp.greenit.shutdown;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface GreenItShutdownRepository extends JpaRepository<GreenItShutdown, Long> {

    @Query(nativeQuery = true, value = """
                SELECT * FROM cmp.green_it_shutdown
                WHERE DATE(start_time AT TIME ZONE 'UTC') 
                    BETWEEN DATE(:startTime AT TIME ZONE 'UTC') - INTERVAL '1 day'
                    AND DATE(:startTime AT TIME ZONE 'UTC') + INTERVAL '1 day'
                ORDER BY start_time ASC
            """)
    List<GreenItShutdown> findByStartDateRange(@Param("startTime") OffsetDateTime startTime);
}
