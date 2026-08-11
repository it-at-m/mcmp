package de.muenchen.mcmp.kubernetes;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.common.OffsetBasedPageRequest;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class KubernetesNamespaceService {

    private final KubernetesNamespaceRepository repository;

    public Page<KubernetesNamespaceListDTO> getVisibleNamespaces(
            final int offset, final int limit,
            final String sortBy, final String sortOrder, final String search, final boolean favorites) {
        final Pageable pageable = (limit == -1) ? Pageable.unpaged() : new OffsetBasedPageRequest(offset, limit);
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String cleanedSearch = null;
        if (search != null) {
            cleanedSearch = search.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
        }
        return repository.findVisibleNamespaces(
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole(),
                cleanedSearch,
                favorites,
                sortBy,
                sortOrder,
                pageable
        ).map(proj -> KubernetesNamespaceListDTO.builder()
                .id(proj.getId())
                .name(proj.getName())
                .environment(proj.getEnvironment())
                .isFavorite(Boolean.TRUE.equals(proj.getIsFavorite()))
                .build());
    }

    @Transactional
    public void addNamespaceToFavorites(final Long namespaceId) {
        repository.addNamespaceToFavorites(namespaceId, AuthUtils.getUsername());
    }

    @Transactional
    public void removeNamespaceFromFavorites(final Long namespaceId) {
        repository.removeNamespaceFromFavorites(namespaceId, AuthUtils.getUsername());
    }

    public List<KubernetesNamespaceRefDTO> getNamespacesByAppserviceId(final Long appserviceId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return repository.findByAppserviceId(
                appserviceId,
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole()
        ).stream()
                .map(proj -> KubernetesNamespaceRefDTO.builder()
                        .id(proj.getId())
                        .name(proj.getName())
                        .clusterName(proj.getClusterName())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KubernetesNamespaceDetailDTO getNamespaceById(final Long id) {
        final KubernetesNamespace namespace = repository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Kubernetes namespace not found: " + id));

        final List<Appservice> appservices = namespace.getAppservices() != null
                ? new java.util.ArrayList<>(namespace.getAppservices())
                : Collections.emptyList();

        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        final boolean canEdit = Boolean.TRUE.equals(repository.canUserEditNamespace(
                id, userRoles.getUsername(), userRoles.hasAdminRole()));

        String webconsoleUrl = null;
        if (namespace.getCluster() != null && namespace.getCluster().getWebConsoleUrl() != null) {
            webconsoleUrl = namespace.getCluster().getWebConsoleUrl().trim();
            if (webconsoleUrl.endsWith("/")) {
                webconsoleUrl += namespace.getName();
            } else {
                webconsoleUrl += "/" + namespace.getName();
            }
        }

        return KubernetesNamespaceDetailDTO.builder()
                .id(namespace.getId())
                .name(namespace.getName())
                .sysId(namespace.getSysId())
                .sysClass(namespace.getSysClass())
                .lastDiscovered(namespace.getLastDiscovered())
                .k8sUid(namespace.getK8sUid())
                .environment(namespace.getEnvironment() != null ? namespace.getEnvironment().name() : null)
                .clusterName(namespace.getCluster() != null ? namespace.getCluster().getName() : null)
                .webconsoleUrl(webconsoleUrl)
                .appservices(appservices.stream()
                        .map(a -> new KubernetesAppserviceRefDTO(a.getId(), a.getName()))
                        .sorted(Comparator.comparing(KubernetesAppserviceRefDTO::name))
                        .collect(Collectors.toList()))
                .canEdit(canEdit)
                .build();
    }
}
