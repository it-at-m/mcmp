package de.muenchen.mcmp.clients.loadbalancer;

import de.muenchen.mcmp.loadbalancer.*;
import de.muenchen.mcmp.server.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadBalancerImportService {

    private final LbVirtualServerRepository virtualServerRepository;
    private final LbPoolRepository poolRepository;
    private final ServerRepository serverRepository;

    @Transactional
    public void importData(final LoadBalancerDTO dto) {
        final long start = System.currentTimeMillis();
        log.info("Starting loadbalancer import: virtualServers={}, pools={}",
                dto.virtualServers().size(), dto.pools().size());

        upsertVirtualServers(dto.virtualServers());
        log.info("Virtual servers upserted in {}ms", System.currentTimeMillis() - start);

        final long poolStart = System.currentTimeMillis();
        final Map<String, Long> ipToServerId = buildIpToServerIdMap(dto.pools());
        log.info("Server IP lookup: matched={} of {} member IPs in {}ms",
                ipToServerId.size(), countMemberIps(dto.pools()), System.currentTimeMillis() - poolStart);

        upsertPools(dto.pools(), ipToServerId);
        log.info("Pools upserted in {}ms", System.currentTimeMillis() - poolStart);

        log.info("Loadbalancer import completed in {}ms", System.currentTimeMillis() - start);
    }

    private Map<String, Long> buildIpToServerIdMap(final Map<String, LoadBalancerDTO.PoolDTO> poolMap) {
        final Set<String> allIps = poolMap.values().stream()
                .filter(p -> p.poolMembers() != null)
                .flatMap(p -> p.poolMembers().stream())
                .map(LoadBalancerDTO.PoolMemberDTO::ip)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (allIps.isEmpty()) {
            return Map.of();
        }

        return serverRepository.findIdsByGuestToolsIpAddressIn(allIps).stream()
                .collect(Collectors.toMap(
                        ServerRepository.ServerIpProjection::getGuestToolsIpAddress,
                        ServerRepository.ServerIpProjection::getId
                ));
    }

    private long countMemberIps(final Map<String, LoadBalancerDTO.PoolDTO> poolMap) {
        return poolMap.values().stream()
                .filter(p -> p.poolMembers() != null)
                .mapToLong(p -> p.poolMembers().size())
                .sum();
    }

    private void upsertVirtualServers(final Map<String, LoadBalancerDTO.VirtualServerDTO> vsMap) {
        final Map<String, LbVirtualServer> existing = virtualServerRepository.findAll().stream()
                .collect(Collectors.toMap(LbVirtualServer::getName, Function.identity()));

        existing.entrySet().stream()
                .filter(e -> !vsMap.containsKey(e.getKey()))
                .forEach(e -> virtualServerRepository.delete(e.getValue()));

        final List<LbVirtualServer> toSave = new ArrayList<>(vsMap.size());
        for (final Map.Entry<String, LoadBalancerDTO.VirtualServerDTO> entry : vsMap.entrySet()) {
            final LbVirtualServer entity = existing.getOrDefault(entry.getKey(), new LbVirtualServer());
            applyVirtualServer(entity, entry.getKey(), entry.getValue());
            toSave.add(entity);
        }
        virtualServerRepository.saveAll(toSave);
        log.debug("Upserted {} virtual servers", toSave.size());
    }

    private List<String> extractDomains(final String name) {
        final String lastSegment = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        final String candidate = lastSegment.contains("_") ? lastSegment.substring(0, lastSegment.indexOf('_')) : lastSegment;
        return candidate.contains(".") ? List.of(candidate) : List.of();
    }

    private void applyVirtualServer(final LbVirtualServer entity, final String name,
                                    final LoadBalancerDTO.VirtualServerDTO vs) {
        entity.setName(name);
        entity.setListen(vs.listen());
        entity.setForward(vs.forward());
        entity.setPort(vs.port());
        entity.setPersistence(vs.persistence());
        entity.setRedirect80(vs.redirect80());
        entity.setAddresses(vs.addresses());
        entity.setDomains(extractDomains(name));
        entity.setIrules(vs.irules());

        if (vs.waf() != null) {
            entity.setWafEnabled(vs.waf().enabled());
            entity.setWafStatus(vs.waf().status());
        } else {
            entity.setWafEnabled(false);
            entity.setWafStatus(null);
        }

        if (vs.pool() != null) {
            final Map<String, LbPoolRef> poolRefs = new HashMap<>();
            for (final Map.Entry<String, LoadBalancerDTO.PoolRefDTO> ref : vs.pool().entrySet()) {
                final LoadBalancerDTO.PoolRefDTO refDTO = ref.getValue();
                poolRefs.put(ref.getKey(), new LbPoolRef(refDTO.isDefault(), refDTO.hosts(), refDTO.paths()));
            }
            entity.setPoolRefs(poolRefs);
        } else {
            entity.setPoolRefs(null);
        }
    }

    private void upsertPools(final Map<String, LoadBalancerDTO.PoolDTO> poolMap, final Map<String, Long> ipToServerId) {
        final Map<String, LbPool> existing = poolRepository.findAll().stream()
                .collect(Collectors.toMap(LbPool::getName, Function.identity()));

        existing.entrySet().stream()
                .filter(e -> !poolMap.containsKey(e.getKey()))
                .forEach(e -> poolRepository.delete(e.getValue()));

        for (final Map.Entry<String, LoadBalancerDTO.PoolDTO> entry : poolMap.entrySet()) {
            final LbPool pool = existing.getOrDefault(entry.getKey(), new LbPool());
            applyPool(pool, entry.getKey(), entry.getValue(), ipToServerId);
            poolRepository.save(pool);
        }
        log.debug("Upserted {} pools", poolMap.size());
    }

    private void applyPool(final LbPool pool, final String name, final LoadBalancerDTO.PoolDTO poolDTO,
                           final Map<String, Long> ipToServerId) {
        pool.setName(name);
        pool.setLbMethod(poolDTO.lbMethod());
        pool.setMonitorCondition(monitorConditionToString(poolDTO.monitorCondition()));
        pool.setMonitors(mapMonitors(poolDTO.monitors()));

        final List<LbPoolMember> newMembers = new ArrayList<>();
        if (poolDTO.poolMembers() != null) {
            for (final LoadBalancerDTO.PoolMemberDTO memberDTO : poolDTO.poolMembers()) {
                final LbPoolMember member = new LbPoolMember();
                member.setPool(pool);
                member.setIp(memberDTO.ip());
                member.setPort(memberDTO.port());
                member.setMonitorCondition(monitorConditionToString(memberDTO.monitorCondition()));
                member.setMonitors(mapMonitors(memberDTO.monitors()));
                final Long serverId = ipToServerId.get(memberDTO.ip());
                if (serverId != null) {
                    member.setServer(serverRepository.getReferenceById(serverId));
                }
                newMembers.add(member);
            }
        }

        if (pool.getMembers() != null) {
            pool.getMembers().clear();
            pool.getMembers().addAll(newMembers);
        } else {
            pool.setMembers(newMembers);
        }
    }

    private List<LbMonitor> mapMonitors(final List<LoadBalancerDTO.MonitorDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream()
                .map(m -> new LbMonitor(
                        m.type(),
                        m.interval() > 0 ? m.interval() : null,
                        m.port() != null ? m.port().toString() : null,
                        m.method(),
                        m.path(),
                        m.host(),
                        m.version(),
                        m.expect()
                ))
                .toList();
    }

    private String monitorConditionToString(final Object condition) {
        if (condition == null) {
            return null;
        }
        return condition.toString();
    }
}
