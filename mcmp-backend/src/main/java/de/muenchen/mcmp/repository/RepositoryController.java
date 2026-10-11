package de.muenchen.mcmp.repository;

import de.muenchen.mcmp.server.ServerFqdnDTO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/repository")
public class RepositoryController {
    private final RepositoryService repositoryService;

    @GetMapping("/server/{serverId}")
    public List<RepositoryDTO> getRepositoriesByServerId(@PathVariable("serverId") final Long serverId) {
        return repositoryService.findByServerId(serverId);
    }

    @GetMapping
    public Page<RepositoryDTO> getVisibleRepositories(@RequestParam(name = "offset") final int offset,
                                                      @RequestParam(name = "limit") final int limit,
                                                      @RequestParam(name = "sortBy") final String sortBy,
                                                      @RequestParam(name = "sortOrder") final String sortOrder,
                                                      @RequestParam(name = "search", required = false) final String search,
                                                      @RequestParam(name = "favorites", required = false, defaultValue = "false") final boolean favorites,
                                                      @RequestParam(name = "editableOnly", required = false, defaultValue = "false") final boolean editableOnly) {
        return repositoryService.getVisibleRepositories(offset, limit, sortBy, sortOrder, search, favorites, editableOnly);
    }

    @GetMapping("/appservice/{appserviceId}")
    public List<RepositoryDTO> getRepositoriesByAppserviceId(@PathVariable("appserviceId") final Long appserviceId) {
        return repositoryService.getRepositoriesByAppserviceId(appserviceId);
    }

    @GetMapping("/{id}")
    public RepositoryDetailDTO getRepositoryById(@PathVariable("id") final Long id) {
        return repositoryService.getRepositoryById(id);
    }

    @GetMapping("/{id}/servers")
    public List<ServerFqdnDTO> getServersByRepositoryId(@PathVariable("id") final Long id) {
        return repositoryService.getServersByRepositoryId(id);
    }

    @PutMapping("/{id}/favorite")
    public void addRepositoryToFavorites(@PathVariable("id") final Long id) {
        repositoryService.addRepositoryToFavorites(id);
    }

    @DeleteMapping("/{id}/favorite")
    public void removeRepositoryFromFavorites(@PathVariable("id") final Long id) {
        repositoryService.removeRepositoryFromFavorites(id);
    }
}
