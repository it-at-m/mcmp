package de.muenchen.mcmp.clients.loadbalancer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadBalancerService {

    private final LoadBalancerImportService loadBalancerImportService;

    @Async
    public void importAsync(final LoadBalancerDTO loadBalancerDTO) {
        try {
            loadBalancerImportService.importData(loadBalancerDTO);
        } catch (Exception e) {
            log.error("Error during loadbalancer import: {}", e.getMessage(), e);
        }
    }
}
