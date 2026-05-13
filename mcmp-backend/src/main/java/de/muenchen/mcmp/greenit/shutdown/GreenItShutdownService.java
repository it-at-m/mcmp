package de.muenchen.mcmp.greenit.shutdown;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GreenItShutdownService {

    private final GreenItShutdownRepository repository;

    public GreenItShutdown save(final GreenItShutdown greenItShutdown) {
        return repository.save(greenItShutdown);
    }

    public List<GreenItShutdown> findByStartDate(final OffsetDateTime startTime) {
        return repository.findByStartDateRange(startTime);
    }
}
