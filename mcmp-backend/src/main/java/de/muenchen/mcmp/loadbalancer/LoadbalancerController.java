package de.muenchen.mcmp.loadbalancer;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(name = "search", required = false) final String search,
            @RequestParam(name = "favorites", required = false, defaultValue = "false") final boolean favorites) {
        return service.getVisibleLoadbalancers(offset, limit, sortBy, sortOrder, search, favorites);
    }

    @GetMapping("/{id}")
    public UnifiedLoadbalancer getLoadbalancerById(@PathVariable("id") final Long id) {
        return service.getLoadbalancerById(id);
    }

    @PutMapping("/{id}/favorite")
    public void addLoadbalancerToFavorites(@PathVariable("id") final Long id) {
        service.addLoadbalancerToFavorites(id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeLoadbalancerFromFavorites(@PathVariable("id") final Long id) {
        service.removeLoadbalancerFromFavorites(id);
    }

    @GetMapping("/server/{serverId}")
    public List<LbServerMembershipDTO> getPoolMembershipsByServerId(@PathVariable("serverId") final Long serverId) {
        return service.getPoolMembershipsByServerId(serverId);
    }

    @GetMapping("/appservice/{appserviceId}")
    public List<LbVirtualServerListDTO> getLoadbalancersByAppserviceId(@PathVariable("appserviceId") final Long appserviceId) {
        return service.getLoadbalancersByAppserviceId(appserviceId);
    }
}
