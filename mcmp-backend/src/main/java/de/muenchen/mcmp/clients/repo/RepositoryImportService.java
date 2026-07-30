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

        final Map<String, Repository> existingReposByName = repositoryRepository.findAll().stream()
                .collect(Collectors.toMap(Repository::getName, Function.identity()));

        final Set<String> importedNames = dto.repositories().stream()
                .map(RepositoryDTO.RepositoryEntryDTO::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());

        log.info("Extracted {} valid names from import DTO.", importedNames.size());

        int updatedCount = 0;
        int createdCount = 0;

        for (final RepositoryDTO.RepositoryEntryDTO entry : dto.repositories()) {
            final String name = entry.name() != null ? entry.name().trim() : null;
            if (name == null || name.isBlank()) continue;

            final Repository existing = existingReposByName.get(name);

            if (existing == null || existing.isLocked() || !Objects.equals(existing.getRepositoryUrl(), entry.url())) {
                repositoryRepository.upsertRepository(name, entry.url());
                if (existing == null) createdCount++; else updatedCount++;
            }
        }

        int lockedCount = 0;
        for (Repository repo : existingReposByName.values()) {
            String repoName = repo.getName() != null ? repo.getName().trim() : "";
            if (!importedNames.contains(repoName)) {
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
