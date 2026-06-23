package de.muenchen.mcmp.clients.loadbalancer;

import de.muenchen.mcmp.security.HasApiRole;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/loadbalancer")
@Slf4j
public class LoadBalancerController {

    private final LoadBalancerService loadBalancerService;

    @HasApiRole
    @GetMapping("/ping")
    public String ping() {
        return "Loadbalancer EAI API is accessible";
    }

    @HasApiRole
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void processLoadBalancerData(@Valid @RequestBody final LoadBalancerDTO loadBalancerDTO) {
        int vsCount = loadBalancerDTO.virtualServers() != null ? loadBalancerDTO.virtualServers().size() : 0;
        int poolCount = loadBalancerDTO.pools() != null ? loadBalancerDTO.pools().size() : 0;
        log.info("Received loadbalancer data: virtualServers={}, pools={}", vsCount, poolCount);

        if (vsCount == 0) {
            log.warn("Loadbalancer import contains no virtual servers — rejecting to prevent data loss.");
            return;
        }

        try {
            loadBalancerService.importAsync(loadBalancerDTO);
        } catch (Exception e) {
            log.error("Failed to submit loadbalancer import task: {}", e.getMessage(), e);
        }
    }
}
