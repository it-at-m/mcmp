package de.muenchen.mcmp.greenit.metrics;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class ServerMetricsService {

    private final ServerMetricsRepository serverMetricsRepository;

    public  List<ServerMetrics> findByServerIdOrderByIdAsc(Long serverId) {
        return serverMetricsRepository.findByServerIdOrderByIdAsc(serverId);
    }

    public List<Long> findServerIdsWithMetricsAndGreenItEnabled() {
        return serverMetricsRepository.findServerIdsWithMetricsAndGreenItEnabled();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveIgnoreDuplicatesAndMissingServers(ServerMetrics entry) {
        serverMetricsRepository.insertIgnoreDuplicatesAndMissingServers(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAllIgnoreDuplicatesAndMissingServers(List<ServerMetrics> entries) {
        serverMetricsRepository.batchInsertIgnoreDuplicatesAndMissingServers(entries);
    }
}
