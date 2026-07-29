package de.muenchen.mcmp.clients.repo;

import de.muenchen.mcmp.repository.Repository;
import de.muenchen.mcmp.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepositoryImportService {

    private final RepositoryRepository repositoryRepository;

    @Transactional
    public void importData(final RepositoryDTO dto) {
        if (dto == null || dto.repositories() == null) {
            log.warn("Import DTO or repositories list is null, skipping.");
            return;
        }

        final long startTime = System.currentTimeMillis();
        log.info("Starting native repository synchronization for {} entries.", dto.repositories().size());

        // 1) Alle vorhandenen Repositories laden für In-Memory Vergleich (minimiert IO)
        final Map<String, Repository> existingReposByName = repositoryRepository.findAll().stream()
                .collect(Collectors.toMap(Repository::getName, Function.identity()));

        // 2) Namen aus dem Import sammeln für die Sperr-Logik
        final Set<String> importedNames = dto.repositories().stream()
                .map(RepositoryDTO.RepositoryEntryDTO::name)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int updatedCount = 0;
        int createdCount = 0;

        // 3) Import-Liste mit nativen SQL-Upserts verarbeiten
        for (final RepositoryDTO.RepositoryEntryDTO entry : dto.repositories()) {
            final String name = entry.name();
            if (name == null || name.isBlank()) continue;

            final Repository existing = existingReposByName.get(name);

            // Nur persistieren, wenn tatsächlich eine Änderung vorliegt (In-Memory Check)
            if (existing == null || existing.isLocked() || !Objects.equals(existing.getRepositoryUrl(), entry.url())) {
                repositoryRepository.upsertRepository(name, entry.url());
                if (existing == null) createdCount++; else updatedCount++;
            }
        }

        // 4) Repositories sperren, die nicht im Import enthalten sind (via native Update)
        int lockedCount = 0;
        for (Repository repo : existingReposByName.values()) {
            if (!importedNames.contains(repo.getName())) {
                // Nur updaten, wenn nicht schon locked/url null (In-Memory Check)
                if (!repo.isLocked() || repo.getRepositoryUrl() != null) {
                    repositoryRepository.lockRepository(repo.getName());
                    lockedCount++;
                }
            }
        }

        log.info("Native import finished in {}ms. Created: {}, Updated: {}, Locked: {}",
                System.currentTimeMillis() - startTime, createdCount, updatedCount, lockedCount);
    }
}
