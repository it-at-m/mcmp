package de.muenchen.mcmp.clients.repo;

import de.muenchen.mcmp.repository.Repository;
import de.muenchen.mcmp.repository.RepositoryLockStatus;
import de.muenchen.mcmp.repository.RepositoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryImportServiceTest {

    @Test
    void toLockStatus_mapsEaiStatus() {
        assertEquals(RepositoryLockStatus.LOCKED, RepositoryImportService.toLockStatus("r", "LOCKED", null));
        assertEquals(RepositoryLockStatus.SELF_ONLY, RepositoryImportService.toLockStatus("r", "self_only", null));
        assertEquals(RepositoryLockStatus.OPEN, RepositoryImportService.toLockStatus("r", "OPEN", null));
        // unknown values fall back to the most restrictive state
        assertEquals(RepositoryLockStatus.LOCKED, RepositoryImportService.toLockStatus("r", "bogus", null));
    }

    @Test
    void toLockStatus_withoutStatus_keepsExistingStatus() {
        // e.g. mcmp-eai-snow-repo-discovery, which doesn't read the status file
        final Repository selfOnly = repository("r", "https://repo/r/", RepositoryLockStatus.SELF_ONLY);
        assertEquals(RepositoryLockStatus.SELF_ONLY, RepositoryImportService.toLockStatus("r", null, selfOnly));
        assertEquals(RepositoryLockStatus.SELF_ONLY, RepositoryImportService.toLockStatus("r", " ", selfOnly));
        // new repositories without status are OPEN, like repositories that aren't listed in .repostatus/locked.json
        assertEquals(RepositoryLockStatus.OPEN, RepositoryImportService.toLockStatus("r", null, null));
    }

    @Test
    void importData_upsertsChangedLockStatusAndSkipsUnchangedRepositories() {
        final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
        final RepositoryImportService service = new RepositoryImportService(repositoryRepository);

        final Repository unchanged = repository("unchanged-repo", "https://repo/unchanged-repo/", RepositoryLockStatus.OPEN);
        final Repository nowSelfOnly = repository("self-repo", "https://repo/self-repo/", RepositoryLockStatus.OPEN);
        when(repositoryRepository.findAll()).thenReturn(List.of(unchanged, nowSelfOnly));

        service.importData(RepositoryDTO.builder().repositories(List.of(
                new RepositoryDTO.RepositoryEntryDTO("unchanged-repo", "https://repo/unchanged-repo/", "OPEN"),
                new RepositoryDTO.RepositoryEntryDTO("self-repo", "https://repo/self-repo/", "SELF_ONLY"),
                new RepositoryDTO.RepositoryEntryDTO("new-repo", "https://repo/new-repo/", "LOCKED")
        )).build());

        verify(repositoryRepository, never()).upsertRepository("unchanged-repo", "https://repo/unchanged-repo/", "OPEN");
        verify(repositoryRepository).upsertRepository("self-repo", "https://repo/self-repo/", "SELF_ONLY");
        verify(repositoryRepository).upsertRepository("new-repo", "https://repo/new-repo/", "LOCKED");
        verify(repositoryRepository, never()).lockRepository(anyString());
    }

    private static Repository repository(final String name, final String url, final RepositoryLockStatus lockStatus) {
        final Repository repository = new Repository();
        repository.setName(name);
        repository.setRepositoryUrl(url);
        repository.setLockStatus(lockStatus);
        return repository;
    }
}
