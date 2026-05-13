package de.muenchen.mcmp.greenit.rightsizing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class GreenItRightsizingService {

    private final GreenItRightsizingRepository repository;

    public GreenItRightsizing save(final GreenItRightsizing greenItRightsizing) {
        return repository.save(greenItRightsizing);
    }

    public List<GreenItRightsizing> findByStartDate(final OffsetDateTime startTime) {
        return repository.findByStartDateRange(startTime);
    }
}
