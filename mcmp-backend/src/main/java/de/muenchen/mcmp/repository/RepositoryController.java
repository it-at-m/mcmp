package de.muenchen.mcmp.repository;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/repository")
public class RepositoryController {
    private final RepositoryService repositoryService;

    @GetMapping("/{serverId}")
    public List<RepositoryDTO> getRepositoriesByServerId(@PathVariable("serverId") final Long serverId) {
        return repositoryService.findByServerId(serverId);
    }
}
