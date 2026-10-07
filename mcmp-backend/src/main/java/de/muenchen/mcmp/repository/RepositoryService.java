package de.muenchen.mcmp.repository;

import de.muenchen.mcmp.common.OffsetBasedPageRequest;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import de.muenchen.mcmp.server.ServerFqdnDTO;
import de.muenchen.mcmp.server.ServerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final RepositoryMapper repositoryMapper;
    private final ServerRepository serverRepository;

    @Transactional(readOnly = true)
    public List<RepositoryDTO> findByServerId(final Long serverId) {
        return repositoryMapper.toDTOs(repositoryRepository.findAllByServersIdOrderByNameAscIgnoreCase(serverId));
    }

    public Page<RepositoryDTO> getVisibleRepositories(int offset, int limit, String sortBy, String sortOrder, String search, boolean favorites, boolean editableOnly) {
        final Pageable pageable = (limit == -1) ? Pageable.unpaged() : new OffsetBasedPageRequest(offset, limit);
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String cleanedSearch = null;
        if (search != null) {
            cleanedSearch = search.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
        }
        final Page<RepositoryListProjection> page = repositoryRepository.findVisibleRepositories(
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasLinuxRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole(),
                cleanedSearch,
                favorites,
                editableOnly,
                sortOrder,
                pageable
        );
        return page.map(proj -> RepositoryDTO.builder()
                .id(proj.getId())
                .name(proj.getName())
                .lockStatus(toLockStatus(proj.getLockStatus()))
                .isFavorite(Boolean.TRUE.equals(proj.getIsFavorite()))
                .canEdit(Boolean.TRUE.equals(proj.getCanEdit()))
                .build());
    }

    @Transactional(readOnly = true)
    public RepositoryDetailDTO getRepositoryById(final Long id) {
        final Repository repository = repositoryRepository.findByIdWithAppservices(id)
                .orElseThrow(() -> new EntityNotFoundException("Repository not found: " + id));
        checkUserCanViewRepository(id);
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        final boolean canEdit = Boolean.TRUE.equals(repositoryRepository.canUserEditRepository(
                id, userRoles.getUsername(), userRoles.hasAdminRole()));
        return RepositoryDetailDTO.builder()
                .id(repository.getId())
                .name(repository.getName())
                .lockStatus(repository.getLockStatus())
                .isFavorite(repositoryRepository.isFavorite(id, userRoles.getUsername()))
                .appservices(repository.getAppservices().stream()
                        .map(a -> new RepositoryAppserviceRefDTO(a.getId(), a.getName()))
                        .sorted(Comparator.comparing(RepositoryAppserviceRefDTO::name))
                        .toList())
                .canEdit(canEdit)
                .build();
    }

    public List<RepositoryDTO> getRepositoriesByAppserviceId(final Long appserviceId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return repositoryRepository.findVisibleByAppserviceId(
                        appserviceId,
                        userRoles.getUsername(),
                        userRoles.hasAdminRole(),
                        userRoles.hasReadonlyRole(),
                        userRoles.hasLinuxRole(),
                        userRoles.hasSecurityRole(),
                        userRoles.hasOperatorRole()
                ).stream()
                .map(proj -> RepositoryDTO.builder()
                        .id(proj.getId())
                        .name(proj.getName())
                        .lockStatus(toLockStatus(proj.getLockStatus()))
                        .isFavorite(Boolean.TRUE.equals(proj.getIsFavorite()))
                        .canEdit(Boolean.TRUE.equals(proj.getCanEdit()))
                        .build())
                .toList();
    }

    /**
     * All servers the repository is attached to.
     */
    public List<ServerFqdnDTO> getServersByRepositoryId(final Long id) {
        if (!repositoryRepository.existsById(id)) {
            throw new EntityNotFoundException("Repository not found: " + id);
        }
        checkUserCanViewRepository(id);
        return serverRepository.findAllByRepositoryId(id).stream()
                .map(s -> new ServerFqdnDTO(s.getId(), s.getName(), s.getFqdn()))
                .toList();
    }

    private static RepositoryLockStatus toLockStatus(final String lockStatus) {
        // unknown values are treated as LOCKED, the most restrictive state
        if (lockStatus == null) {
            return RepositoryLockStatus.LOCKED;
        }
        try {
            return RepositoryLockStatus.valueOf(lockStatus);
        } catch (IllegalArgumentException e) {
            return RepositoryLockStatus.LOCKED;
        }
    }

    private void checkUserCanViewRepository(final Long id) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        final boolean canView = repositoryRepository.canUserViewRepository(
                id,
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasLinuxRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole());
        if (!canView) {
            throw new AccessDeniedException("You are not allowed to view this repository.");
        }
    }

    public boolean existsByName(final String name) {
        return repositoryRepository.findIdByName(name).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Repository> findByNameWithAppservices(final String name) {
        return repositoryRepository.findByNameWithAppservices(name);
    }

    public boolean canUserEditRepository(final Long id) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return Boolean.TRUE.equals(repositoryRepository.canUserEditRepository(
                id, userRoles.getUsername(), userRoles.hasAdminRole()));
    }

    @Transactional
    public void addRepositoryToFavorites(final Long repositoryId) {
        repositoryRepository.addRepositoryToFavorites(repositoryId, AuthUtils.getUsername());
    }

    @Transactional
    public void removeRepositoryFromFavorites(final Long repositoryId) {
        repositoryRepository.removeRepositoryFromFavorites(repositoryId, AuthUtils.getUsername());
    }
}