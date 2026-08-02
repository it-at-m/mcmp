package de.muenchen.mcmp.clients.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepositoryImportAsyncService {

    private final RepositoryImportService repositoryImportService;

    @Async
    public void importAsync(final RepositoryDTO repositoryDTO) {
        try {
            repositoryImportService.importData(repositoryDTO);
        } catch (Exception e) {
            log.error("Error during asynchronous repository import: {}", e.getMessage(), e);
        }
    }
}