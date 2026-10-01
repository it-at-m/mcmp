package de.muenchen.mcmp.clients.checkmk;

import de.muenchen.mcmp.greenit.metrics.ServerMetrics;
import de.muenchen.mcmp.greenit.metrics.ServerMetricsService;
import de.muenchen.mcmp.mountPoint.MountPoint;
import de.muenchen.mcmp.mountPoint.MountPointRepository;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.types.ServerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service class responsible for importing Checkmk performance data and associating it with existing servers.
 * It handles data persistence in bulk, with a fallback mechanism for saving individual entries in case of errors.
 * The service also logs missing hostnames and persistence issues encountered during the import process.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckMkImportService {

    private final ServerService serverService;
    private final ServerMetricsService serverMetricsService;
    private final MountPointRepository mountPointRepository;

    /**
     * Imports Checkmk performance data and associates it with existing servers.
     * Data is saved in bulk, with a fallback to individual saves in case of errors.
     * Logs warnings for missing hostnames and any persistence issues.
     *
     * @param checkMkDTO The DTO containing Checkmk performance data.
     *                   It includes a map of hostnames to their associated performance metrics.
     */
    public void importCheckMkData(final CheckMkDTO checkMkDTO) {
        log.info("Importing CheckMk data for {} hosts", checkMkDTO.hosts().size());

        // load current DB state
        final List<Server> servers = serverService.findAll();
        final Map<Long, Server> serversById = servers
                .stream()
                .collect(Collectors.toMap(Server::getId, Function.identity()));
        final Map<Long, Map<String, MountPoint>> mounts = mountPointRepository
                .findAll()
                .stream()
                .collect(Collectors.groupingBy(
                        MountPoint::getServerId,
                        Collectors.toMap(MountPoint::getDiskPath, Function.identity()))
                );

        // create map of server names to server IDs
        final Map<String, Long> serverIdMap = new HashMap<>();
        for (final Server server : servers) {
            if (server.getName() != null && !server.getName().isBlank()) {
                serverIdMap.put(server.getName().toLowerCase(), server.getId());
            }
            if (server.getFqdn() != null && !server.getFqdn().isBlank()
                    && !server.getFqdn().equalsIgnoreCase(server.getName())) {
                serverIdMap.put(server.getFqdn().toLowerCase(), server.getId());
            }
        }

        final OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        final List<ServerMetrics> metricsToSave = new ArrayList<>();
        final List<MountPoint> mountsToSave = new ArrayList<>();
        final Set<String> missingHostnames = new TreeSet<>();

        for (final Map.Entry<String, CheckMkDTO.HostData> entry : checkMkDTO.hosts().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            final String hostname = entry.getKey().toLowerCase();
            final CheckMkDTO.HostData hostData = entry.getValue();
            final Long serverId = serverIdMap.get(hostname);
            if (serverId == null) {
                missingHostnames.add(hostname);
                continue;
            }

            metricsToSave.add(new ServerMetrics(
                    serverId,
                    now,
                    hostData.cpuUtil(),
                    hostData.memUsedPercent()
            ));

            // handle mounts
            if (hostData.filesystemMetrics() == null)
                continue;

            final var server = serversById.get(serverId);
            if (server.getServerType() == ServerType.VM_VMWARE)
                continue;

            final var serverMounts = hostData
                    .filesystemMetrics()
                    .stream()
                    .map(dto -> {
                        // Fix Windows paths with "erroneous" forward
                        // slashes to be consistent with VMware.
                        final var path = fixWindowsPath(dto.path());

                        var mount = mounts.getOrDefault(serverId, Map.of()).get(path);
                        if (mount != null && !Objects.equals(mount.getSource(), "checkmk"))
                            return null; // ignore mounts created by other importers

                        if (mount == null) {
                            mount = new MountPoint();
                            mount.setServerId(serverId);
                            mount.setDiskPath(path);
                            mount.setSource("checkmk");
                        }

                        mount.setCapacityInBytes((long) (dto.sizeMiB() * 1024 * 1024));
                        mount.setFreeSpaceInBytes((long) (dto.freeMiB() * 1024 * 1024));
                        return mount;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            mountsToSave.addAll(serverMounts);
        }

        if (!missingHostnames.isEmpty()) {
            log.debug("Server not found for {} Checkmk hostnames (sorted, unique): {}", missingHostnames.size(), missingHostnames);
        }

        if (!metricsToSave.isEmpty()) {
            saveMetrics(metricsToSave);
        } else {
            log.info("Metrics import skipped (no servers found).");
        }

        if (!mountsToSave.isEmpty()) {
            saveMounts(mountsToSave);
        } else {
            log.info("Mounts import skipped (no servers found).");
        }
    }

    private void saveMetrics(List<ServerMetrics> metrics) {
        try {
            serverMetricsService.saveAllIgnoreDuplicatesAndMissingServers(metrics);
            log.info("Successfully saved performance data for {} servers", metrics.size());
        } catch (Exception e) {
            log.warn("Metrics bulk save failed, falling back to individual saves: {}", e.getMessage());

            int saved = 0;
            int skippedDeleted = 0;
            int skippedDuplicate = 0;

            for (final ServerMetrics p : metrics) {
                try {
                    serverMetricsService.saveIgnoreDuplicatesAndMissingServers(p);
                    saved++;
                } catch (Exception ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("foreign key constraint")) {
                        skippedDeleted++;
                        log.warn("Failed metrics import: Server was deleted during import: server_id={}", p.getServerId());
                    } else {
                        skippedDuplicate++;
                        log.warn("Failed metrics import: Duplicate or other error for server_id={}: {}", p.getServerId(), ex.getMessage());
                    }
                }
            }

            log.info("Metrics import complete: saved={}/{}, skipped_deleted_servers={}, skipped_duplicates={}",
                    saved, metrics.size(), skippedDeleted, skippedDuplicate);
        }
    }

    private void saveMounts(List<MountPoint> mounts) {
        try {
            mountPointRepository.saveAll(mounts);
            log.info("Successfully saved mount point data for {} mounts", mounts.size());
        } catch (Exception e) {
            log.warn("Mount bulk save failed, falling back to individual saves: {}", e.getMessage());

            int saved = 0;
            int skippedDeleted = 0;
            int skippedDuplicate = 0;

            for (final var m : mounts) {
                try {
                    mountPointRepository.save(m);
                    saved++;
                } catch (Exception ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("foreign key constraint")) {
                        skippedDeleted++;
                        log.warn("Failed mount import: Server was deleted during import: server_id={}", m.getServerId());
                    } else {
                        skippedDuplicate++;
                        log.warn("Failed mount import: Duplicate or other error for server_id={}: {}", m.getServerId(), ex.getMessage());
                    }
                }
            }

            log.info("Mount import complete: saved={}/{}, skipped_deleted_servers={}, skipped_duplicates={}",
                    saved, mounts.size(), skippedDeleted, skippedDuplicate);
        }
    }

    /**
     * Converts any forward slashes into backslashes if the path looks like
     * a Windows path.
     * <p>
     *     A path is considered to "look like a Windows path" if it
     *     starts with a single alphabetic character, followed by
     *     a colon, and a forward slash.
     * </p>
     */
    private static String fixWindowsPath(String path) {
        if (Character.isAlphabetic(path.charAt(0)) && path.charAt(1) == ':') {
            return path.replace('/', '\\');
        }

        return path;
    }
}