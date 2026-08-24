package de.muenchen.mcmp.errorlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.Optional;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    void deleteByCreatedAtBefore(Date threshold);

    Page<ErrorLogSummary> findAllBy(Pageable pageable);

    Optional<ErrorLogSummary> findSummaryById(Long id);
}
