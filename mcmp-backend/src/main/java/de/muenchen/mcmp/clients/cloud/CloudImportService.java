
package de.muenchen.mcmp.clients.cloud;

import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.cloud.CloudDTO;
import de.muenchen.mcmp.cloud.CloudService;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.snapshot.Snapshot;
import de.muenchen.mcmp.snapshot.SnapshotRepository;
import de.muenchen.mcmp.types.CloudType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudImportService {
    private final CloudService cloudService;
    private final ServerService serverService;
    private final SnapshotRepository snapshotRepository;

    public void importCloudData(final CloudImportDTO cloudDTO) {
        log.info("Starting Cloud import process for {} servers.", cloudDTO.servers().size());

        final Cloud cloud = findOrCreateCloud(cloudDTO);
        if (cloud == null) {
            log.warn("Cloud konnte nicht gefunden/erstellt werden, Import wird abgebrochen.");
            return;
        }

        final Map<String, Server> existingServers = serverService.findAllByCloudId(cloud.getId())
                .stream()
                .collect(Collectors.toMap(Server::getUuid, Function.identity()));

        final Map<Long, Map<String, Snapshot>> existingSnapshots = snapshotRepository.findByServerCloudId(cloud.getId())
                .stream()
                .collect(Collectors.groupingBy(Snapshot::getServerId, Collectors.toMap(Snapshot::getName, Function.identity())));

        // Keep track of UUIDs to handle duplicates.
        final Set<String> importedUuids = new HashSet<>();

        // Statistics
        int inserted = 0;
        int updated = 0;
        int deleted = 0;

        for (final CloudImportDTO.Server dto : cloudDTO.servers()) {
            if (dto.uuid() == null || dto.uuid().isBlank()) {
                log.warn("Server ohne UUID übersprungen: name={}", dto.name());
                continue;
            }

            if (!importedUuids.add(dto.uuid())) {
                log.warn("Server mit duplizierter UUID übersprungen: name={}, uuid={}", dto.name(), dto.uuid());
                continue;
            }

            var server = existingServers.get(dto.uuid());
            if (server != null) {
                if (hasChanges(server, dto)) {
                    applyChanges(server, dto);
                    server = saveServer(server);

                    if (server == null)
                        continue;

                    updated++;
                }

                // Synchronize snapshots
                final Map<String, Snapshot> snapshots = existingSnapshots.get(server.getId());
                final Set<String> importedNames = new HashSet<>();

                for (final var snapshotDTO : dto.snapshots()) {
                    importedNames.add(snapshotDTO.name());
                    var snapshot = snapshots != null ? snapshots.get(snapshotDTO.name()) : null;
                    if (snapshot != null) {
                        saveSnapshot(applySnapshotChanges(snapshot, snapshotDTO));
                    } else {
                        saveSnapshot(buildNewSnapshot(snapshotDTO, server));
                    }
                }

                if (snapshots != null) {
                    for (final var snapshot : snapshots.values()) {
                        if (!importedNames.contains(snapshot.getName())) {
                            deleteSnapshot(snapshot);
                        }
                    }
                }
            } else {
                server = saveServer(buildNewServer(dto, cloud));
                if (server == null)
                    continue;

                inserted++;

                for (final var snapshotDTO : dto.snapshots()) {
                    saveSnapshot(buildNewSnapshot(snapshotDTO, server));
                }
            }
        }

        for (final var server : existingServers.values()) {
            if (!importedUuids.contains(server.getUuid())) {
                if (deleteServer(server))
                    deleted++;
            }
        }

        log.info("Cloud import finished. inserted={}, updated={}, deleted={}",
                inserted, updated, deleted);
    }

    private Server saveServer(final Server server) {
        try {
            return serverService.save(server);
        } catch (final ObjectOptimisticLockingFailureException e) {
            log.warn("Versionskonflikt beim Update von Server uuid={}, name={} – wird beim nächsten Import erneut versucht.",
                    server.getUuid(), server.getName());
            return null;
        } catch (final Exception e) {
            log.error("Fehler beim Speichern von Server uuid={}, name={}: {}",
                    server.getUuid(), server.getName(), e.getMessage());
            return null;
        }
    }

    private boolean deleteServer(final Server server) {
        try {
            serverService.delete(server);
            return true;
        } catch (final ObjectOptimisticLockingFailureException ex) {
            log.warn("Versionskonflikt beim Löschen von Server uuid={}, name={} – wird beim nächsten Import erneut versucht.",
                    server.getUuid(), server.getName());
            return false;
        } catch (final Exception e) {
            log.error("Fehler beim Löschen von Server uuid={}, name={}: {}",
                    server.getUuid(), server.getName(), e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private Snapshot saveSnapshot(final Snapshot snapshot) {
        try {
            return snapshotRepository.save(snapshot);
        } catch (final ObjectOptimisticLockingFailureException ex) {
            log.warn("Versionskonflikt beim Update von Snapshot name={}, serverID={} – wird beim nächsten Import erneut versucht.",
                    snapshot.getName(), snapshot.getServerId());
            return null;
        } catch (final Exception e) {
            log.error("Fehler beim Update von Snapshot name={}, serverID={}: {}",
                    snapshot.getName(), snapshot.getServerId(), e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean deleteSnapshot(final Snapshot snapshot) {
        try {
            snapshotRepository.delete(snapshot);
            return true;
        } catch (final ObjectOptimisticLockingFailureException ex) {
            log.warn("Versionskonflikt beim Löschen von Snapshot name={}, serverID={} – wird beim nächsten Import erneut versucht.",
                    snapshot.getName(), snapshot.getServerId());
            return false;
        } catch (final Exception e) {
            log.error("Fehler beim Löschen von Snapshot name={}, serverID={}: {}",
                    snapshot.getName(), snapshot.getServerId(), e.getMessage());
            return false;
        }
    }

    private Cloud findOrCreateCloud(final CloudImportDTO cloudImportDTO) {
        if (cloudImportDTO == null) {
            throw new IllegalArgumentException("cloudImportDTO darf nicht null sein");
        }
        if (cloudImportDTO.cloud() == null || cloudImportDTO.cloud().isBlank()) {
            throw new IllegalArgumentException("cloudImportDTO.cloud darf nicht null/leer sein");
        }

        final String endpoint = cloudImportDTO.cloud();

        final Cloud byEndpoint = cloudService.findByApiEndpoint(endpoint);
        if (byEndpoint != null) {
            log.debug("Cloud mit apiEndpoint='{}' gefunden (id={})", endpoint, byEndpoint.getId());
            return byEndpoint;
        }

        log.info("Cloud mit apiEndpoint/fqdn='{}' nicht vorhanden. Erstelle neue Cloud automatisch.", endpoint);

        final CloudDTO newCloud = CloudDTO.builder()
                .id(null)
                .name(endpoint)
                .fqdn(endpoint)
                .serverGui(null)
                .cloudType(cloudImportDTO.cloudType())
                .apiDescription(cloudImportDTO.cloudType() + " " + endpoint)
                .apiUsername(null)
                .apiPassword(null)
                .apiEndpoint(endpoint)
                .enabled(true)
                .locked(false)
                .configInfobloxId(null)
                .configBaasId(null)
                .greenItEnabled(false)
                .build();

        try {
            cloudService.createCloudEntry(newCloud);
        } catch (Exception e) {
            log.error("Fehler beim Erstellen der Cloud für endpoint='{}': {}", endpoint, e.getMessage(), e);
            return null;
        }

        final Cloud created = cloudService.findByApiEndpoint(endpoint);
        if (created == null) {
            log.warn("Cloud wurde angelegt, konnte aber danach nicht per apiEndpoint='{}' geladen werden.", endpoint);
        }
        return created;
    }

    private boolean hasChanges(final Server existing, final CloudImportDTO.Server dto) {
        return !Objects.equals(existing.getName(), dto.name())
                || !Objects.equals(existing.getInstanceUuid(), dto.instanceUuid())
                || !Objects.equals(existing.getVmId(), dto.vmId())
                || !Objects.equals(existing.getCluster(), dto.cluster())
                || !Objects.equals(existing.getHost(), dto.host())
                || !Objects.equals(existing.getLocation(), dto.location())
                || !Objects.equals(existing.getPowerState(), dto.powerState())
                || !Objects.equals(existing.getMemoryMb(), dto.memoryMB())
                || !Objects.equals(existing.getNumCpu(), dto.numCPU())
                || !Objects.equals(existing.getNumCoresPerSocket(), dto.numCoresPerSocket())
                || !Objects.equals(existing.getMemoryHotAddEnabled(), dto.memoryHotAddEnabled())
                || !Objects.equals(existing.getCpuHotAddEnabled(), dto.cpuHotAddEnabled())
                || !Objects.equals(existing.getCpuHotRemoveEnabled(), dto.cpuHotRemoveEnabled())
                || !Objects.equals(existing.getCpuTopology(), dto.cpuTopology())
                || !Objects.equals(existing.getVmxVersion(), dto.vmxVersion())
                || !Objects.equals(existing.getGuestConfigId(), dto.guestConfigId())
                || !Objects.equals(existing.getGuestConfigFullName(), dto.guestConfigFullName())
                || !Objects.equals(existing.getGuestToolsId(), dto.guestToolsId())
                || !Objects.equals(existing.getGuestToolsFullName(), dto.guestToolsFullName())
                || !Objects.equals(existing.getGuestToolsState(), dto.guestToolsState())
                || !Objects.equals(existing.getGuestToolsRunningStatus(), dto.guestToolsRunningStatus())
                || !Objects.equals(existing.getGuestToolsVersionStatus(), dto.guestToolsVersionStatus())
                || !Objects.equals(existing.getGuestToolsVersionStatus2(), dto.guestToolsVersionStatus2())
                || !Objects.equals(existing.getGuestToolsInstallType(), dto.guestToolsInstallType())
                || !Objects.equals(existing.getGuestToolsVersion(), dto.guestToolsVersion())
                || !Objects.equals(existing.getGuestToolsFamily(), dto.guestToolsFamily())
                || !Objects.equals(existing.getGuestToolsHostname(), dto.guestToolsHostname())
                || !Objects.equals(existing.getGuestToolsIpAddress(), dto.guestToolsIpAddress())
                || !Objects.equals(existing.getGuestToolsArchitecture(), dto.guestToolsArchitecture())
                || !Objects.equals(existing.getGuestToolsBitness(), dto.guestToolsBitness())
                || !Objects.equals(existing.getGuestToolsBuildNumber(), dto.guestToolsBuildNumber())
                || !Objects.equals(existing.getGuestToolsCpeString(), dto.guestToolsCpeString())
                || !Objects.equals(existing.getGuestToolsDistroAddlVersion(), dto.guestToolsDistroAddlVersion())
                || !Objects.equals(existing.getGuestToolsDistroName(), dto.guestToolsDistroName())
                || !Objects.equals(existing.getGuestToolsDistroVersion(), dto.guestToolsDistroVersion())
                || !Objects.equals(existing.getGuestToolsFamilyName(), dto.guestToolsFamilyName())
                || !Objects.equals(existing.getGuestToolsKernelVersion(), dto.guestToolsKernelVersion())
                || !Objects.equals(existing.getGuestToolsPrettyName(), dto.guestToolsPrettyName())
                || !Objects.equals(existing.getHotPlugMemoryLimit(), dto.hotPlugMemoryLimit())
                || !Objects.equals(existing.getHotPlugMemoryIncrementSize(), dto.hotPlugMemoryIncrementSize())
                || !Objects.equals(existing.getDn(), dto.dn())
                || !Objects.equals(existing.getAssociation(), dto.association())
                || !Objects.equals(existing.getMemorySpeed(), dto.memorySpeed())
                || !Objects.equals(existing.getModel(), dto.model())
                || !Objects.equals(existing.getNumOfAdaptors(), dto.numOfAdaptors())
                || !Objects.equals(existing.getNumOfCoresEnabled(), dto.numOfCoresEnabled())
                || !Objects.equals(existing.getNumOfEthHostIfs(), dto.numOfEthHostIfs())
                || !Objects.equals(existing.getNumOfFcHostIfs(), dto.numOfFcHostIfs())
                || !Objects.equals(existing.getOperState(), dto.operState())
                || !Objects.equals(existing.getUcsmChassisId(), dto.chassisId())
                || !Objects.equals(existing.getUcsmChassisSlotId(), dto.slotId())
                || !Objects.equals(existing.getUcsmServerId(), dto.serverId())
                || !Objects.equals(existing.getVendor(), dto.vendor())
                || !Objects.equals(existing.getVid(), dto.vid())
                || !Objects.equals(existing.getServerKind(), dto.serverKind())
                || !Objects.equals(existing.getServerType(), dto.serverType());
    }

    private void applyChanges(final Server server, final CloudImportDTO.Server dto) {
        // Memory-Änderung tracken
        if (!Objects.equals(server.getMemoryMb(), dto.memoryMB())) {
            if (server.getMemoryMbChangeDate() != null) {
                server.setMemoryMbChangeDatePrev(server.getMemoryMbChangeDate());
            }
            server.setMemoryMbPrev(server.getMemoryMb());
            server.setMemoryMbChangeDate(OffsetDateTime.now());
        }
        // CPU-Änderung tracken
        if (!Objects.equals(server.getNumCpu(), dto.numCPU())) {
            if (server.getNumCpuChangeDatePrev() != null) {
                server.setNumCpuChangeDatePrev(server.getNumCpuChangeDate());
            }
            server.setNumCpuPrev(server.getNumCpu());
            server.setNumCpuChangeDate(OffsetDateTime.now());
        }

        if (dto.name() == null || dto.name().isBlank()) {
            server.setName(dto.uuid());
        } else {
            server.setName(dto.name().trim());
        }
        server.setInstanceUuid(dto.instanceUuid());
        server.setVmId(dto.vmId());
        server.setCluster(dto.cluster());
        server.setHost(dto.host());
        server.setLocation(dto.location());
        server.setPowerState(dto.powerState());
        server.setMemoryMb(dto.memoryMB());
        server.setNumCpu(dto.numCPU());
        server.setNumCoresPerSocket(dto.numCoresPerSocket());
        server.setMemoryHotAddEnabled(Boolean.TRUE.equals(dto.memoryHotAddEnabled()));
        server.setCpuHotAddEnabled(Boolean.TRUE.equals(dto.cpuHotAddEnabled()));
        server.setCpuHotRemoveEnabled(Boolean.TRUE.equals(dto.cpuHotRemoveEnabled()));
        server.setCpuTopology(dto.cpuTopology());
        server.setVmxVersion(dto.vmxVersion());
        server.setOverallStatus(dto.overallStatus());
        server.setConfigStatus(dto.configStatus());
        server.setGuestConfigId(dto.guestConfigId());
        server.setGuestConfigFullName(dto.guestConfigFullName());
        server.setGuestToolsId(dto.guestToolsId());
        server.setGuestToolsFullName(dto.guestToolsFullName());
        server.setGuestToolsState(dto.guestToolsState());
        server.setGuestToolsRunningStatus(dto.guestToolsRunningStatus());
        server.setGuestToolsVersionStatus(dto.guestToolsVersionStatus());
        server.setGuestToolsVersionStatus2(dto.guestToolsVersionStatus2());
        server.setGuestToolsInstallType(dto.guestToolsInstallType());
        server.setGuestToolsVersion(dto.guestToolsVersion());
        server.setGuestToolsFamily(dto.guestToolsFamily());
        server.setGuestToolsHostname(dto.guestToolsHostname());
        server.setGuestToolsIpAddress(dto.guestToolsIpAddress());
        server.setGuestToolsArchitecture(dto.guestToolsArchitecture());
        server.setGuestToolsBitness(dto.guestToolsBitness());
        server.setGuestToolsBuildNumber(dto.guestToolsBuildNumber());
        server.setGuestToolsCpeString(dto.guestToolsCpeString());
        server.setGuestToolsDistroAddlVersion(dto.guestToolsDistroAddlVersion());
        server.setGuestToolsDistroName(dto.guestToolsDistroName());
        server.setGuestToolsDistroVersion(dto.guestToolsDistroVersion());
        server.setGuestToolsFamilyName(dto.guestToolsFamilyName());
        server.setGuestToolsKernelVersion(dto.guestToolsKernelVersion());
        server.setGuestToolsPrettyName(dto.guestToolsPrettyName());
        server.setBootTime(dto.bootTime());
        server.setHotPlugMemoryLimit(dto.hotPlugMemoryLimit());
        server.setHotPlugMemoryIncrementSize(dto.hotPlugMemoryIncrementSize());
        server.setDn(dto.dn());
        server.setAssociation(dto.association());
        server.setMemorySpeed(dto.memorySpeed());
        server.setMfgTime(dto.mfgTime());
        server.setModel(dto.model());
        server.setNumOfAdaptors(dto.numOfAdaptors());
        server.setNumOfCoresEnabled(dto.numOfCoresEnabled());
        server.setNumOfEthHostIfs(dto.numOfEthHostIfs());
        server.setNumOfFcHostIfs(dto.numOfFcHostIfs());
        server.setOperState(dto.operState());
        server.setUcsmChassisId(dto.chassisId());
        server.setUcsmChassisSlotId(dto.slotId());
        server.setUcsmServerId(dto.serverId());
        server.setMemoryMbAvailable(dto.availableMemory());
        server.setVendor(dto.vendor());
        server.setVid(dto.vid());
        server.setServerKind(dto.serverKind());
        server.setServerType(dto.serverType());
    }

    private Server buildNewServer(final CloudImportDTO.Server dto, final Cloud cloud) {
        final Server server = new Server();
        server.setCloud(cloud);
        server.setUuid(dto.uuid());
        applyChanges(server, dto);

        if (server.getFqdn() == null || server.getFqdn().isBlank()) {
            if (cloud.getCloudType() == CloudType.UCS_CIMC) {
                server.setFqdn(normalizeMgmtFQDN(cloud.getApiEndpoint().trim()));
            } else {
                if (dto.name() == null || dto.name().isBlank()) {
                    server.setFqdn(dto.uuid());
                } else {
                    server.setFqdn(dto.name().trim());
                }
            }
        }

        return server;
    }

    private Snapshot applySnapshotChanges(Snapshot snapshot, final CloudImportDTO.Snapshot dto) {
        snapshot.setName(dto.name());
        snapshot.setDescription(dto.description());
        snapshot.setCreateTime(dto.createTime());
        snapshot.setQuiesced(dto.quiesced());
        snapshot.setReplaySupported(dto.replaySupported());
        return snapshot;
    }

    private Snapshot buildNewSnapshot(final CloudImportDTO.Snapshot dto, final Server server) {
        final Snapshot snapshot = new Snapshot();
        snapshot.setSnapshotId(Math.abs(dto.name().hashCode()));
        snapshot.setServerId(server.getId());
        return applySnapshotChanges(snapshot, dto);
    }

    /**
     * Normalizes a management FQDN by removing 'm' from the end of the hostname if present.
     *
     * <p>If the hostname (part before the first dot) ends with 'm', the 'm' is removed.</p>
     *
     * <p>Example: "dcwik102m.example.org" becomes "dcwik102.example.org"</p>
     *
     * @param mgmtFQDN the management FQDN to normalize, may be null
     * @return the normalized FQDN, or null if input is null
     */
    public static String normalizeMgmtFQDN(final String mgmtFQDN) {
        if (mgmtFQDN == null) return null;

        int dotIndex = mgmtFQDN.indexOf('.');
        if (dotIndex == -1) return mgmtFQDN; // no domain, return as is

        String hostname = mgmtFQDN.substring(0, dotIndex);
        String domain = mgmtFQDN.substring(dotIndex);

        if (hostname.endsWith("m")) {
            return hostname.substring(0, hostname.length() - 1) + domain;
        } else {
            return mgmtFQDN;
        }
    }
}