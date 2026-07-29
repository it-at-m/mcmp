
package de.muenchen.mcmp.clients.cloud;

import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.cloud.CloudDTO;
import de.muenchen.mcmp.cloud.CloudService;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.server.ServerStatusType;
import de.muenchen.mcmp.types.CloudType;
import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudImportService {

    private static final int BATCH_SIZE = 100;

    private final ServerService serverService;
    private final ServerRepository serverRepository;
    private final CloudService cloudService;

    public void importCloudData(final CloudImportDTO cloudDTO) {
        log.info("Starting Cloud import process for {} servers.", cloudDTO.servers().size());

        final Cloud cloud = findOrCreateCloud(cloudDTO);
        if (cloud == null) {
            log.warn("Cloud konnte nicht gefunden/erstellt werden, Import wird abgebrochen.");
            return;
        }

        // 1) Alle Server der Cloud aus DB laden -> Map uuid -> Server
        final Map<String, Server> dbServersByUuid = serverRepository.findAllByCloudId(cloud.getId())
                .stream()
                .collect(Collectors.toMap(Server::getUuid, Function.identity()));

        // 2) Alle UUIDs aus dem DTO sammeln (für spätere Löschung)
        final Set<String> importedUuids = new HashSet<>();

        // 3+4) Update bestehender / Insert neuer Server
        final List<Server> toInsert = new ArrayList<>();

        for (final CloudImportDTO.Server dto : cloudDTO.servers()) {
            if (dto.uuid() == null || dto.uuid().isBlank()) {
                log.warn("Server ohne UUID übersprungen: name={}", dto.name());
                continue;
            }

            if (!importedUuids.add(dto.uuid())) {
                log.warn("Server mit duplizierter UUID übersprungen: name={}, uuid={}", dto.name(), dto.uuid());
                continue;
            }

            final Server existing = dbServersByUuid.get(dto.uuid());
            if (existing != null) {
                if (hasChanges(existing, dto)) {
                    applyChanges(existing, dto);
                    saveWithOptimisticLockRetry(existing);
                }
            } else {
                toInsert.add(buildNewServer(dto, cloud));
            }
        }

        // Batch-Insert neuer Server
        if (!toInsert.isEmpty()) {
            batchInsert(toInsert, cloud);
        }

        // 5) Server löschen, die nicht mehr im DTO vorhanden sind
        final Set<String> toDelete = dbServersByUuid.keySet().stream()
                .filter(uuid -> !importedUuids.contains(uuid))
                .collect(Collectors.toSet());

        if (!toDelete.isEmpty()) {
            deleteObsoleteServers(cloud.getId(), toDelete);
        }

        log.info("Cloud import finished. inserted={}, updated=checked individually, deleted={}",
                toInsert.size(), toDelete.size());
    }

    /**
     * Speichert einen Server in einer eigenen Transaktion.
     * Versionskonflikte (OptimisticLocking) werden geloggt und übersprungen –
     * der nächste Import-Lauf übernimmt die Änderung dann.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveWithOptimisticLockRetry(final Server server) {
        try {
            serverRepository.save(server);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Versionskonflikt beim Update von Server uuid={}, name={} – wird beim nächsten Import erneut versucht.",
                    server.getUuid(), server.getName());
        }
    }

    /**
     * Löscht obsolete Server in einer eigenen Transaktion via Bulk-Delete.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteObsoleteServers(final Long cloudId, final Set<String> uuids) {
        // Bulk-Delete in Batches, um sehr große IN-Klauseln zu vermeiden
        final List<String> uuidList = new ArrayList<>(uuids);
        for (int i = 0; i < uuidList.size(); i += BATCH_SIZE) {
            final List<String> batch = uuidList.subList(i, Math.min(i + BATCH_SIZE, uuidList.size()));
            try {
                serverRepository.deleteByCloudIdAndUuidIn(cloudId, batch);
            } catch (Exception e) {
                log.error("Fehler beim Löschen von Servern (cloudId={}, batch starting at {}): {}", cloudId, i, e.getMessage());
                // Fallback: einzeln löschen, um den verursachenden Server zu identifizieren
                for (final String uuid : batch) {
                    try {
                        serverRepository.deleteByCloudIdAndUuidIn(cloudId, Collections.singletonList(uuid));
                    } catch (Exception ex) {
                        log.error("Fehler beim Löschen von Server uuid={} (cloudId={}): {}", uuid, cloudId, ex.getMessage());
                    }
                }
            }
        }
    }

    private void batchInsert(final List<Server> servers, final Cloud cloud) {
        for (int i = 0; i < servers.size(); i += BATCH_SIZE) {
            final List<Server> batch = servers.subList(i, Math.min(i + BATCH_SIZE, servers.size()));
            try {
                serverRepository.saveAll(batch);
                serverRepository.flush();
            } catch (Exception e) {
                log.error("Fehler beim Batch-Insert (cloudId={}, batch starting at {}): {}",
                        cloud.getId(), i, e.getMessage());
                // Fallback: einzeln einfügen, damit der Batch-Fehler nicht alle blockiert
                for (final Server s : batch) {
                    try {
                        serverRepository.save(s);
                    } catch (Exception ex) {
                        log.error("Fehler beim Einzelinsert von Server uuid={}, name={}: {}",
                                s.getUuid(), s.getName(), ex.getMessage());
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hilfsmethoden
    // -------------------------------------------------------------------------

    private boolean hasChanges(final Server existing, final CloudImportDTO.Server dto) {
        String name = dto.name();
        if (name == null || name.isBlank()) {
            name = dto.uuid();
        } else {
            name = name.trim();
        }
        return !Objects.equals(existing.getName(), name)
                || !Objects.equals(existing.getInstanceUuid(), dto.instanceUuid())
                || !Objects.equals(existing.getVmId(), dto.vmId())
                || !Objects.equals(existing.getCluster(), dto.cluster())
                || !Objects.equals(existing.getHost(), dto.host())
                || !Objects.equals(existing.getLocation(), dto.location())
                || !Objects.equals(existing.getPowerState(), mapPowerState(dto.powerState()))
                || !Objects.equals(existing.getMemoryMb(), dto.memoryMB() != null ? dto.memoryMB().intValue() : null)
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
                || !Objects.equals(
                existing.getServerKind() != null ? existing.getServerKind().name() : null,
                dto.serverKind())
                || !Objects.equals(
                existing.getServerType() != null ? existing.getServerType().name() : null,
                dto.serverType());
    }

    private void applyChanges(final Server server, final CloudImportDTO.Server dto) {
        // Memory-Änderung tracken
        final Integer newMemory = dto.memoryMB() != null ? dto.memoryMB().intValue() : null;
        if (!Objects.equals(server.getMemoryMb(), newMemory)) {
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
        server.setPowerState(mapPowerState(dto.powerState()));
        server.setMemoryMb(newMemory);
        server.setNumCpu(dto.numCPU());
        server.setNumCoresPerSocket(dto.numCoresPerSocket());
        server.setMemoryHotAddEnabled(Boolean.TRUE.equals(dto.memoryHotAddEnabled()));
        server.setCpuHotAddEnabled(Boolean.TRUE.equals(dto.cpuHotAddEnabled()));
        server.setCpuHotRemoveEnabled(Boolean.TRUE.equals(dto.cpuHotRemoveEnabled()));
        server.setCpuTopology(dto.cpuTopology());
        server.setVmxVersion(dto.vmxVersion());
        server.setOverallStatus(parseStatus(dto.overallStatus()));
        server.setConfigStatus(parseStatus(dto.configStatus()));
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
        server.setBootTime(parseOffsetDateTime(dto.bootTime()));
        server.setHotPlugMemoryLimit(dto.hotPlugMemoryLimit());
        server.setHotPlugMemoryIncrementSize(dto.hotPlugMemoryIncrementSize());
        server.setDn(dto.dn());
        server.setAssociation(dto.association());
        server.setMemorySpeed(dto.memorySpeed());
        server.setMfgTime(parseOffsetDateTime(dto.mfgTime()));
        server.setModel(dto.model());
        server.setNumOfAdaptors(dto.numOfAdaptors());
        server.setNumOfCoresEnabled(dto.numOfCoresEnabled());
        server.setNumOfEthHostIfs(dto.numOfEthHostIfs());
        server.setNumOfFcHostIfs(dto.numOfFcHostIfs());
        server.setOperState(dto.operState());
        server.setUcsmChassisId(dto.chassisId());
        server.setUcsmChassisSlotId(dto.slotId());
        server.setUcsmServerId(dto.serverId());
        server.setMemoryMbAvailable(dto.availableMemory() != null ? dto.availableMemory().intValue() : null);
        server.setVendor(dto.vendor());
        server.setVid(dto.vid());
        server.setServerKind(parseServerKind(dto.serverKind()));
        server.setServerType(parseServerType(dto.serverType()));
    }

    private Server buildNewServer(final CloudImportDTO.Server dto, final Cloud cloud) {
        final Server server = new Server();
        server.setCloud(cloud);
        server.setUuid(dto.uuid());
        applyChanges(server, dto);
        if (server.getPowerState() == null) server.setPowerState("unknown");

        if (server.getMemoryMb() == null) server.setMemoryMb(0);
        if (server.getNumCpu() == null) server.setNumCpu(0);
        if (server.getName() == null || server.getName().isBlank()) {
            server.setName(dto.uuid());
        }
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

    private ServerStatusType parseStatus(final String value) {
        if (value == null || value.isBlank()) return ServerStatusType.gray;
        try {
            return ServerStatusType.valueOf(value.toLowerCase());
        } catch (IllegalArgumentException e) {
            return ServerStatusType.gray;
        }
    }

    private ServerKind parseServerKind(final String value) {
        if (value == null || value.isBlank()) return ServerKind.UNKNOWN;
        try {
            return ServerKind.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ServerKind.UNKNOWN;
        }
    }

    private ServerType parseServerType(final String value) {
        if (value == null || value.isBlank()) return ServerType.UNKNOWN;
        try {
            return ServerType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ServerType.UNKNOWN;
        }
    }

    private OffsetDateTime parseOffsetDateTime(final String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            log.debug("Konnte Datum nicht parsen: '{}'", value);
            return null;
        }
    }

    private String mapPowerState(final String dtoPowerState) {
        if (dtoPowerState == null) return null;
        if ("on".equalsIgnoreCase(dtoPowerState) || "up".equalsIgnoreCase(dtoPowerState)) return "poweredOn";
        if ("off".equalsIgnoreCase(dtoPowerState) || "down".equalsIgnoreCase(dtoPowerState)) return "poweredOff";
        return dtoPowerState;
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
}