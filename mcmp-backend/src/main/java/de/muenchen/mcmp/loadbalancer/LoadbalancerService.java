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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class LoadbalancerService {

    private final LbVirtualServerRepository repository;
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

        final List<LbVirtualServerPoolRef> poolRefs =
                lvs.getPoolRefs() != null ? lvs.getPoolRefs() : Collections.emptyList();

        final List<UnifiedLoadbalancerPoolDTO> poolDTOs = poolRefs.stream()
                .map(ref -> {
                    final LbPool pool = ref.getPool();
                    return UnifiedLoadbalancerPoolDTO.builder()
                            .name(pool.getName())
                            .lbMethod(pool.getLbMethod())
                            .monitorCondition(pool.getMonitorCondition())
                            .monitors(toMonitorDTOs(pool.getMonitors()))
                            .poolRef(new LbPoolRef(ref.getIsDefault(), ref.getHosts(), ref.getPaths()))
                            .members(pool.getMembers() == null ? Collections.emptyList() :
                                    pool.getMembers().stream()
                                            .map(m -> UnifiedLoadbalancerMemberDTO.builder()
                                                    .ip(m.getIp())
                                                    .port(m.getPort())
                                                    .serverId(m.getServer() != null ? m.getServer().getId() : null)
                                                    .serverName(m.getServer() != null ? m.getServer().getName() : null)
                                                    .monitorCondition(m.getMonitorCondition())
                                                    .monitors(toMonitorDTOs(m.getMonitors()))
                                                    .build())
                                            .collect(Collectors.toList()))
                            .build();
                })
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
                .irules(lvs.getIrules().stream()
                        .map(i -> new LbIruleDTO(i.getName(), i.getContent()))
                        .sorted(Comparator.comparing(LbIruleDTO::name))
                        .collect(Collectors.toList()))
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

    private List<LbMonitor> toMonitorDTOs(final List<LbPoolMonitor> monitors) {
        if (monitors == null) {
            return Collections.emptyList();
        }
        return monitors.stream()
                .map(m -> new LbMonitor(
                        m.getType(),
                        m.getInterval(),
                        m.getPort(),
                        m.getMethod(),
                        m.getPath(),
                        m.getHost(),
                        m.getHttpVersion(),
                        m.getExpect()
                ))
                .collect(Collectors.toList());
    }

    private String extractDomain(final String name) {
        final String lastSegment = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        final String candidate = lastSegment.contains("_") ? lastSegment.substring(0, lastSegment.indexOf('_')) : lastSegment;
        return candidate.contains(".") ? candidate : null;
    }
}
