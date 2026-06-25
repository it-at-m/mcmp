package de.muenchen.mcmp.loadbalancer;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class LoadbalancerService {

    private final LbVirtualServerRepository repository;
    private final LbPoolRepository poolRepository;
    private final LbPoolMemberRepository poolMemberRepository;

    public Page<LbVirtualServerListDTO> getVisibleLoadbalancers(
            final int offset, final int limit,
            final String sortBy, final String sortOrder, final String search) {
        final Pageable pageable = (limit == -1) ? Pageable.unpaged() : new OffsetBasedPageRequest(offset, limit);
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        String cleanedSearch = null;
        if (search != null) {
            cleanedSearch = search.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
        }
        return repository.findVisibleLoadbalancers(
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole(),
                userRoles.hasNetworkRole(),
                cleanedSearch,
                sortBy,
                sortOrder,
                pageable
        ).map(proj -> LbVirtualServerListDTO.builder()
                .id(proj.getId())
                .name(proj.getName())
                .domain(extractDomain(proj.getName()))
                .listen(proj.getListen())
                .port(proj.getPort())
                .appserviceName(proj.getAppserviceName())
                .build());
    }

    @Transactional(readOnly = true)
    public UnifiedLoadbalancer getLoadbalancerById(final Long id) {
        final LbVirtualServer lvs = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Loadbalancer not found: " + id));

        final Map<String, LbPoolRef> poolRefs =
                lvs.getPoolRefs() != null ? lvs.getPoolRefs() : Collections.emptyMap();

        final List<LbPool> pools = poolRefs.isEmpty()
                ? Collections.emptyList()
                : poolRepository.findAllByNameIn(poolRefs.keySet());

        final List<UnifiedLoadbalancerPoolDTO> poolDTOs = pools.stream()
                .map(pool -> UnifiedLoadbalancerPoolDTO.builder()
                        .name(pool.getName())
                        .lbMethod(pool.getLbMethod())
                        .monitorCondition(pool.getMonitorCondition())
                        .monitors(pool.getMonitors())
                        .poolRef(poolRefs.get(pool.getName()))
                        .members(pool.getMembers() == null ? Collections.emptyList() :
                                pool.getMembers().stream()
                                        .map(m -> UnifiedLoadbalancerMemberDTO.builder()
                                                .ip(m.getIp())
                                                .port(m.getPort())
                                                .serverId(m.getServer() != null ? m.getServer().getId() : null)
                                                .serverName(m.getServer() != null ? m.getServer().getName() : null)
                                                .monitorCondition(m.getMonitorCondition())
                                                .monitors(m.getMonitors())
                                                .build())
                                        .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return UnifiedLoadbalancer.builder()
                .id(lvs.getId())
                .name(lvs.getName())
                .listen(lvs.getListen())
                .forward(lvs.getForward())
                .port(lvs.getPort())
                .persistence(lvs.getPersistence())
                .wafEnabled(lvs.isWafEnabled())
                .wafStatus(lvs.getWafStatus())
                .redirect80(lvs.isRedirect80())
                .addresses(lvs.getAddresses())
                .domains(lvs.getDomains())
                .appserviceNames(lvs.getAppservices().stream()
                        .map(Appservice::getName)
                        .collect(Collectors.toSet()))
                .pools(poolDTOs)
                .build();
    }

    public List<LbServerMembershipDTO> getPoolMembershipsByServerId(final Long serverId) {
        return poolMemberRepository.findMembershipsByServerId(serverId).stream()
                .map(p -> LbServerMembershipDTO.builder()
                        .vsId(p.getVsId())
                        .vsDomain(p.getVsDomain())
                        .poolName(p.getPoolName())
                        .memberIp(p.getMemberIp())
                        .memberPort(p.getMemberPort())
                        .build())
                .collect(Collectors.toList());
    }

    private String extractDomain(final String name) {
        final String lastSegment = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        final String candidate = lastSegment.contains("_") ? lastSegment.substring(0, lastSegment.indexOf('_')) : lastSegment;
        return candidate.contains(".") ? candidate : null;
    }
}
