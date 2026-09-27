package de.muenchen.mcmp.errorlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    @Modifying
    @Query("DELETE FROM ErrorLog e WHERE e.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") Instant threshold);

    Page<ErrorLogSummary> findAllBy(Pageable pageable);

    Optional<ErrorLogSummary> findSummaryById(Long id);
}
