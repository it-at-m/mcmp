package de.muenchen.mcmp.loadbalancer;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/loadbalancer")
public class LoadbalancerController {

    private final LoadbalancerService service;

    @GetMapping
    public Page<LbVirtualServerListDTO> getVisibleLoadbalancers(
            @RequestParam(name = "offset") final int offset,
            @RequestParam(name = "limit") final int limit,
            @RequestParam(name = "sortBy") final String sortBy,
            @RequestParam(name = "sortOrder") final String sortOrder,
            @RequestParam(name = "search", required = false) final String search) {
        return service.getVisibleLoadbalancers(offset, limit, sortBy, sortOrder, search);
    }

    @GetMapping("/{id}")
    public UnifiedLoadbalancer getLoadbalancerById(@PathVariable("id") final Long id) {
        return service.getLoadbalancerById(id);
    }
}
