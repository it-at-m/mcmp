package de.muenchen.mcmp.clients.foreman;

import de.muenchen.mcmp.foreman.ConfigForeman;
import de.muenchen.mcmp.foreman.ConfigForemanRepository;
import de.muenchen.mcmp.foreman.ForemanHost;
import de.muenchen.mcmp.foreman.ForemanHostRepository;
import de.muenchen.mcmp.foreman.ForemanInterface;
import de.muenchen.mcmp.foreman.ForemanInterfaceRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Writes the raw Foreman data of a {@link HostDTO} into the three Foreman tables
 * (config_foreman, foreman_host, foreman_interface) before it is imported into the server table.
 */
@Slf4j
@Service
@AllArgsConstructor
public class ForemanSyncService {

    private final ConfigForemanRepository configForemanRepository;
    private final ForemanHostRepository foremanHostRepository;
    private final ForemanInterfaceRepository foremanInterfaceRepository;

    /**
     * Synchronizes the raw Foreman data for all imported hosts in a single transaction.
     * <p>
     * Instead of looking up and persisting each host and interface individually, this method
     * loads all existing {@link ForemanHost} / {@link ForemanInterface} rows for the involved
     * {@link ConfigForeman} instances once, updates only the rows that actually changed and
     * finally deletes all hosts (and their interfaces) that are no longer part of the import.
     *
     * @param hosts the Foreman hosts from the import
     * @return a map keyed by {@link #hostKey(String, Long)} pointing to the synchronized host
     */
    @Transactional
    public Map<String, ForemanHost> syncAllForemanData(final List<HostDTO> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return Map.of();
        }

        final Map<String, ConfigForeman> configBySource = resolveConfigForemen(hosts);
        if (configBySource.isEmpty()) {
            log.warn("No valid Foreman source found in import; skipping Foreman data synchronization");
            return Map.of();
        }

        final Map<String, ForemanHost> existingHosts = loadExistingHosts(configBySource.values());
        final Map<Long, List<ForemanInterface>> existingInterfaces = loadExistingInterfaces(existingHosts.values());

        final Map<String, ForemanHost> syncedHosts = new HashMap<>();
        final List<ForemanHost> hostsToSave = new ArrayList<>();
        final Set<String> scheduledHostKeys = new HashSet<>();
        final Set<Long> keptHostIds = new HashSet<>();
        final OffsetDateTime now = OffsetDateTime.now();

        // Phase 1: create/update hosts in memory (only changed rows are collected)
        for (final HostDTO hostDTO : hosts) {
            if (hostDTO == null || hostDTO.id() == null) {
                continue;
            }

            final String source = normalizeSource(hostDTO.source());
            final ConfigForeman configForeman = configBySource.get(source);
            if (configForeman == null) {
                log.warn("Cannot sync Foreman host {}: source {} is null or blank", hostDTO.name(), hostDTO.source());
                continue;
            }

            final Long foremanId = hostDTO.id().longValue();
            final String key = hostKey(source, foremanId);

            ForemanHost foremanHost = syncedHosts.get(key);
            if (foremanHost == null) {
                foremanHost = existingHosts.get(key);
                if (foremanHost != null && foremanHost.getId() != null) {
                    keptHostIds.add(foremanHost.getId());
                }
            }
            if (foremanHost == null) {
                foremanHost = newForemanHost(foremanId, configForeman);
            }

            if (updateForemanHost(foremanHost, hostDTO, now) && scheduledHostKeys.add(key)) {
                hostsToSave.add(foremanHost);
            }
            syncedHosts.put(key, foremanHost);
        }

        // Phase 2: persist new/changed hosts so that ids are available for the interfaces
        if (!hostsToSave.isEmpty()) {
            foremanHostRepository.saveAll(hostsToSave);
            foremanHostRepository.flush();
        }

        // Phase 3: reconcile interfaces of the synchronized hosts
        final List<ForemanInterface> interfacesToSave = new ArrayList<>();
        final List<Long> staleInterfaceIds = new ArrayList<>();
        for (final HostDTO hostDTO : hosts) {
            if (hostDTO == null || hostDTO.id() == null) {
                continue;
            }
            final ForemanHost foremanHost = syncedHosts.get(hostKey(hostDTO.source(), hostDTO.id().longValue()));
            if (foremanHost == null) {
                continue;
            }
            reconcileInterfaces(hostDTO, foremanHost, existingInterfaces, interfacesToSave, staleInterfaceIds, now);
        }

        if (!interfacesToSave.isEmpty()) {
            foremanInterfaceRepository.saveAll(interfacesToSave);
        }
        if (!staleInterfaceIds.isEmpty()) {
            foremanInterfaceRepository.deleteAllByIdInBatch(staleInterfaceIds);
        }

        // Phase 4: delete hosts that are no longer part of the import (interfaces cascade)
        final List<Long> staleHostIds = existingHosts.values().stream()
                .filter(host -> host.getId() != null && !keptHostIds.contains(host.getId()))
                .map(ForemanHost::getId)
                .toList();
        if (!staleHostIds.isEmpty()) {
            log.info("Deleting {} Foreman host(s) that are no longer part of the import", staleHostIds.size());
            foremanHostRepository.deleteAllByIdInBatch(staleHostIds);
        }

        log.info("Synchronized {} Foreman host(s) ({} created/updated, {} deleted)",
                syncedHosts.size(), hostsToSave.size(), staleHostIds.size());
        return syncedHosts;
    }

    /**
     * Resolves / creates the {@link ConfigForeman} entries for all sources contained in the import.
     */
    private Map<String, ConfigForeman> resolveConfigForemen(final List<HostDTO> hosts) {
        final Set<String> sources = hosts.stream()
                .filter(Objects::nonNull)
                .map(HostDTO::source)
                .filter(source -> source != null && !source.isBlank())
                .map(ForemanSyncService::normalizeSource)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (sources.isEmpty()) {
            return Map.of();
        }

        final Map<String, ConfigForeman> configBySource = new HashMap<>();
        for (final ConfigForeman configForeman : configForemanRepository.findAllByFqdnInIgnoreCase(sources)) {
            configBySource.put(normalizeSource(configForeman.getFqdn()), configForeman);
        }

        final OffsetDateTime now = OffsetDateTime.now();
        for (final String source : sources) {
            configBySource.computeIfAbsent(source, key -> {
                final ConfigForeman configForeman = new ConfigForeman();
                configForeman.setFqdn(key);
                configForeman.setVersion(0L);
                configForeman.setCreatedAt(now);
                configForeman.setUpdatedAt(now);
                configForeman.setEnabled(false);
                configForeman.setApiUsername("TODO");
                configForeman.setApiPasswordEncrypted(new byte[0]);
                configForeman.setApiEndpoint("https://" + key + "/api");
                log.info("Created new config_foreman entry for fqdn {}", key);
                return configForemanRepository.save(configForeman);
            });
        }
        return configBySource;
    }

    /**
     * Loads all existing hosts for the given {@code config_foreman} entries, keyed by
     * {@link #hostKey(String, Long)}.
     */
    private Map<String, ForemanHost> loadExistingHosts(final Collection<ConfigForeman> configs) {
        final Map<Long, String> sourceByConfigId = configs.stream()
                .filter(config -> config.getId() != null)
                .collect(Collectors.toMap(ConfigForeman::getId,
                        config -> normalizeSource(config.getFqdn()),
                        (first, second) -> first));
        if (sourceByConfigId.isEmpty()) {
            return new HashMap<>();
        }

        final Map<String, ForemanHost> hostsByKey = new HashMap<>();
        for (final ForemanHost host : foremanHostRepository.findAllByConfigForemanIdIn(sourceByConfigId.keySet())) {
            final String source = sourceByConfigId.get(host.getConfigForeman().getId());
            if (source != null) {
                hostsByKey.put(hostKey(source, host.getForemanId()), host);
            }
        }
        return hostsByKey;
    }

    /**
     * Loads all existing interfaces for the given hosts, grouped by host id.
     */
    private Map<Long, List<ForemanInterface>> loadExistingInterfaces(final Collection<ForemanHost> hosts) {
        final List<Long> hostIds = hosts.stream()
                .map(ForemanHost::getId)
                .filter(Objects::nonNull)
                .toList();
        if (hostIds.isEmpty()) {
            return Map.of();
        }
        return foremanInterfaceRepository.findAllByForemanHostIdIn(hostIds).stream()
                .collect(Collectors.groupingBy(foremanInterface -> foremanInterface.getForemanHost().getId()));
    }

    /**
     * Applies the values of the {@link HostDTO} to the {@link ForemanHost}.
     *
     * @return {@code true} if at least one field changed (or the host is new)
     */
    private boolean updateForemanHost(final ForemanHost foremanHost, final HostDTO hostDTO, final OffsetDateTime now) {
        boolean changed = foremanHost.getId() == null;
        changed |= updateField(foremanHost::getName, foremanHost::setName, hostDTO.name());
        changed |= updateField(foremanHost::getHostname, foremanHost::setHostname, hostDTO.hostname());
        changed |= updateField(foremanHost::getMac, foremanHost::setMac, hostDTO.mac());
        changed |= updateField(foremanHost::getOperatingsystem, foremanHost::setOperatingsystem, hostDTO.operatingsystemName());
        changed |= updateField(foremanHost::getUuid, foremanHost::setUuid, hostDTO.uuid());
        changed |= updateField(foremanHost::getDmiProductSerialNumber, foremanHost::setDmiProductSerialNumber, hostDTO.dmiProductSerialNumber());
        changed |= updateField(foremanHost::getDmiProductUUID, foremanHost::setDmiProductUUID, hostDTO.dmiProductUUID());

        if (changed) {
            foremanHost.setUpdatedAt(now);
        }
        return changed;
    }

    private ForemanHost newForemanHost(final Long foremanId, final ConfigForeman configForeman) {
        final ForemanHost foremanHost = new ForemanHost();
        foremanHost.setForemanId(foremanId);
        foremanHost.setConfigForeman(configForeman);
        foremanHost.setVersion(0L);
        final OffsetDateTime now = OffsetDateTime.now();
        foremanHost.setCreatedAt(now);
        foremanHost.setUpdatedAt(now);
        return foremanHost;
    }

    /**
     * Creates / updates / deletes the interfaces of a single host based on the existing data
     * loaded in {@link #loadExistingInterfaces(Collection)}.
     */
    private void reconcileInterfaces(final HostDTO hostDTO,
                                     final ForemanHost foremanHost,
                                     final Map<Long, List<ForemanInterface>> existingInterfaces,
                                     final List<ForemanInterface> interfacesToSave,
                                     final List<Long> staleInterfaceIds,
                                     final OffsetDateTime now) {
        final List<InterfaceDTO> interfaces = hostDTO.interfaces();
        if (interfaces == null) {
            return;
        }

        final List<ForemanInterface> existing = existingInterfaces.getOrDefault(foremanHost.getId(), List.of());
        final Map<Long, ForemanInterface> interfacesByForemanId = existing.stream()
                .collect(Collectors.toMap(ForemanInterface::getForemanId,
                        interfaceEntity -> interfaceEntity,
                        (first, second) -> first));

        final Set<Long> syncedForemanIds = new HashSet<>();
        for (final InterfaceDTO interfaceDTO : interfaces) {
            if (interfaceDTO == null || interfaceDTO.id() == null) {
                continue;
            }

            final Long foremanId = interfaceDTO.id().longValue();
            syncedForemanIds.add(foremanId);
            final ForemanInterface foremanInterface = interfacesByForemanId.computeIfAbsent(
                    foremanId,
                    id -> newForemanInterface(id, foremanHost));

            if (updateForemanInterface(foremanInterface, interfaceDTO, hostDTO, now)) {
                interfacesToSave.add(foremanInterface);
            }
        }

        existing.stream()
                .filter(foremanInterface -> !syncedForemanIds.contains(foremanInterface.getForemanId()))
                .map(ForemanInterface::getId)
                .filter(Objects::nonNull)
                .forEach(staleInterfaceIds::add);
    }

    /**
     * Applies the values of the {@link InterfaceDTO} to the {@link ForemanInterface}.
     *
     * @return {@code true} if at least one field changed (or the interface is new)
     */
    private boolean updateForemanInterface(final ForemanInterface foremanInterface,
                                           final InterfaceDTO interfaceDTO,
                                           final HostDTO hostDTO,
                                           final OffsetDateTime now) {
        boolean changed = foremanInterface.getId() == null;
        changed |= updateField(foremanInterface::getExecution, foremanInterface::setExecution, boolOrFalse(interfaceDTO.execution()));
        changed |= updateField(foremanInterface::getFqdn, foremanInterface::setFqdn, interfaceDTO.fqdn());
        changed |= updateField(foremanInterface::getIdentifier, foremanInterface::setIdentifier, interfaceDTO.identifier());
        changed |= updateField(foremanInterface::getIp, foremanInterface::setIp, interfaceDTO.ip());
        changed |= updateField(foremanInterface::getIp6, foremanInterface::setIp6, interfaceDTO.ip6());
        changed |= updateField(foremanInterface::getMac, foremanInterface::setMac, interfaceDTO.mac());
        changed |= updateField(foremanInterface::getMtu, foremanInterface::setMtu, interfaceDTO.mtu());
        changed |= updateField(foremanInterface::getManaged, foremanInterface::setManaged, boolOrFalse(interfaceDTO.managed()));
        changed |= updateField(foremanInterface::getName, foremanInterface::setName, interfaceDTO.name());
        changed |= updateField(foremanInterface::getPrimary, foremanInterface::setPrimary, boolOrFalse(interfaceDTO.primary()));
        changed |= updateField(foremanInterface::getProvision, foremanInterface::setProvision, boolOrFalse(interfaceDTO.provision()));
        changed |= updateField(foremanInterface::getSubnetName, foremanInterface::setSubnetName, interfaceDTO.subnetName());
        changed |= updateField(foremanInterface::getType, foremanInterface::setType, interfaceDTO.type());
        changed |= updateField(foremanInterface::getVirtual, foremanInterface::setVirtual, boolOrFalse(interfaceDTO.virtual()));
        changed |= updateField(foremanInterface::getLinux, foremanInterface::setLinux, boolOrFalse(hostDTO.linux()));
        changed |= updateField(foremanInterface::getWindows, foremanInterface::setWindows, boolOrFalse(hostDTO.windows()));

        if (changed) {
            foremanInterface.setUpdatedAt(now);
        }
        return changed;
    }

    private ForemanInterface newForemanInterface(final Long foremanId, final ForemanHost foremanHost) {
        final ForemanInterface foremanInterface = new ForemanInterface();
        foremanInterface.setForemanId(foremanId);
        foremanInterface.setForemanHost(foremanHost);
        foremanInterface.setVersion(0L);
        final OffsetDateTime now = OffsetDateTime.now();
        foremanInterface.setCreatedAt(now);
        foremanInterface.setUpdatedAt(now);
        foremanInterface.setExecution(false);
        foremanInterface.setManaged(false);
        foremanInterface.setPrimary(false);
        foremanInterface.setProvision(false);
        foremanInterface.setVirtual(false);
        foremanInterface.setLinux(false);
        foremanInterface.setWindows(false);
        return foremanInterface;
    }

    /**
     * Builds the stable key used to correlate an import host with its {@link ForemanHost}.
     */
    public static String hostKey(final String source, final Long foremanId) {
        if (foremanId == null) {
            return null;
        }
        return normalizeSource(source) + ":" + foremanId;
    }

    private static String normalizeSource(final String source) {
        return source == null ? null : source.trim().toLowerCase();
    }

    /**
     * Updates a field only if the new value differs from the current one.
     *
     * @return {@code true} if the value was changed, {@code false} otherwise
     */
    private <T> boolean updateField(final Supplier<T> getter, final Consumer<T> setter, final T value) {
        if (!Objects.equals(getter.get(), value)) {
            setter.accept(value);
            return true;
        }
        return false;
    }

    private static boolean boolOrFalse(final Boolean value) {
        return value != null && value;
    }
}