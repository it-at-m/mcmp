package de.muenchen.mcmp.clients.checkmk;

import de.muenchen.mcmp.greenit.metrics.ServerMetrics;
import de.muenchen.mcmp.greenit.metrics.ServerMetricsService;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

        final List<Server> servers = serverService.findAll();
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

        final List<ServerMetrics> toSave = new ArrayList<>();
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
            toSave.add(new ServerMetrics(
                    serverId,
                    now,
                    hostData.cpuUtil(),
                    hostData.memUsedPercent()
            ));
        }

        if (!missingHostnames.isEmpty()) {
            log.debug("Server not found for {} Checkmk hostnames (sorted, unique): {}", missingHostnames.size(), missingHostnames);
        }

        if (toSave.isEmpty()) {
            log.info("No matching servers found, nothing to save.");
            return;
        }

        try {
            serverMetricsService.saveAllIgnoreDuplicatesAndMissingServers(toSave);
            log.info("Successfully saved performance data for {} servers", toSave.size());
        } catch (Exception e) {
            log.warn("Checkmk bulk save failed, falling back to individual saves: {}", e.getMessage());

            int saved = 0;
            int skippedDeleted = 0;
            int skippedDuplicate = 0;

            for (final ServerMetrics p : toSave) {
                try {
                    serverMetricsService.saveIgnoreDuplicatesAndMissingServers(p);
                    saved++;
                } catch (Exception ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("foreign key constraint")) {
                        skippedDeleted++;
                        log.warn("Server was deleted during import: server_id={}", p.getServerId());
                    } else {
                        skippedDuplicate++;
                        log.warn("Duplicate or other error for server_id={}: {}", p.getServerId(), ex.getMessage());
                    }
                }
            }
            log.info("Fallback complete: saved={}/{}, skipped_deleted_servers={}, skipped_duplicates={}",
                    saved, toSave.size(), skippedDeleted, skippedDuplicate);
        }
    }
}