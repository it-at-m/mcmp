package de.muenchen.mcmp.clients.repo;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/repository")
public class RepositoryImportController {

    private final RepositoryImportAsyncService repositoryImportAsyncService;

    @GetMapping("/ping")
    public String ping() {
        return "Repository EAI API is accessible";
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void importRepositories(@Valid @RequestBody final RepositoryDTO repositoryDTO) {
        int importSize = (repositoryDTO != null && repositoryDTO.repositories() != null)
                ? repositoryDTO.repositories().size() : 0;

        log.info("Received repository import request with {} entries. Delegating to async service.", importSize);

        if (importSize == 0) {
            log.warn("Import list is empty, nothing to do.");
            return;
        }

        repositoryImportAsyncService.importAsync(repositoryDTO);
    }
}