package de.muenchen.mcmp.greenit.rightsizing;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface GreenItRightsizingRepository extends JpaRepository<GreenItRightsizing, Long> {

    @Query(nativeQuery = true, value = """
                SELECT * FROM cmp.green_it_rightsizing
                WHERE DATE(start_time AT TIME ZONE 'UTC') 
                    BETWEEN DATE(:startTime AT TIME ZONE 'UTC') - INTERVAL '1 day'
                    AND DATE(:startTime AT TIME ZONE 'UTC') + INTERVAL '1 day'
                ORDER BY start_time ASC
            """)
    List<GreenItRightsizing> findByStartDateRange(@Param("startTime") OffsetDateTime startTime);
}
