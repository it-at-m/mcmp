package de.muenchen.mcmp.kubernetes;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/openshift/namespace")
public class KubernetesNamespaceController {

    private final KubernetesNamespaceService service;

    @GetMapping
    public Page<KubernetesNamespaceListDTO> getVisibleNamespaces(
            @RequestParam(name = "offset") final int offset,
            @RequestParam(name = "limit") final int limit,
            @RequestParam(name = "sortBy") final String sortBy,
            @RequestParam(name = "sortOrder") final String sortOrder,
            @RequestParam(name = "search", required = false) final String search,
            @RequestParam(name = "favorites", required = false, defaultValue = "false") final boolean favorites) {
        return service.getVisibleNamespaces(offset, limit, sortBy, sortOrder, search, favorites);
    }

    @GetMapping("/{id}")
    public KubernetesNamespaceDetailDTO getNamespaceById(@PathVariable("id") final Long id) {
        return service.getNamespaceById(id);
    }

    @PutMapping("/{id}/favorite")
    public void addNamespaceToFavorites(@PathVariable("id") final Long id) {
        service.addNamespaceToFavorites(id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeNamespaceFromFavorites(@PathVariable("id") final Long id) {
        service.removeNamespaceFromFavorites(id);
    }

    @GetMapping("/appservice/{appserviceId}")
    public List<KubernetesNamespaceRefDTO> getNamespacesByAppserviceId(@PathVariable("appserviceId") final Long appserviceId) {
        return service.getNamespacesByAppserviceId(appserviceId);
    }
}
