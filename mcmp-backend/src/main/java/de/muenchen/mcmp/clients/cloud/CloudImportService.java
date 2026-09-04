
package de.muenchen.mcmp.clients.cloud;

import de.muenchen.mcmp.clients.cloud.model.CloudDTO;
import de.muenchen.mcmp.clients.cloud.model.ServerDTO;
import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.cloud.CloudService;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.snapshot.Snapshot;
import de.muenchen.mcmp.snapshot.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudImportService {
    private final CloudService cloudService;
    private final ServerService serverService;
    private final SnapshotRepository snapshotRepository;

    public void importCloudData(final CloudDTO cloudDTO) {
        log.info("Starting Cloud import process for {} servers.", cloudDTO.servers().size());

        final Cloud cloud = findOrCreateCloud(cloudDTO);
        if (cloud == null) {
            log.warn("Cloud konnte nicht gefunden/erstellt werden, Import wird abgebrochen.");
            return;
        }

        // Fetch current DB state in advance.
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
        int skipped = 0;
        int errors = 0;

        for (final ServerDTO dto : cloudDTO.servers()) {
            if (dto.uuid() == null || dto.uuid().isBlank()) {
                log.warn("Server ohne UUID übersprungen: name={}", dto.name());
                skipped++;
                continue;
            }

            if (!importedUuids.add(dto.uuid())) {
                log.warn("Server mit duplizierter UUID übersprungen: name={}, uuid={}", dto.name(), dto.uuid());
                skipped++;
                continue;
            }

            var server = existingServers.get(dto.uuid());
            if (server == null) {
                server = tryApply(serverService::save, dto.build(cloud), s ->
                        "beim Insert von Server name=%s".formatted(s.getName()));

                if (server == null) {
                    errors++;
                    continue;
                }

                inserted++;
            } else if (dto.hasChanges(server)) {
                dto.applyChanges(server);
                server = tryApply(serverService::save, server, s ->
                        "beim Update von Server name=%s".formatted(s.getName()));

                if (server == null) {
                    errors++;
                    continue;
                }

                updated++;
            }

            // Synchronize snapshots
            final Map<String, Snapshot> snapshots = Objects.requireNonNullElseGet(
                    existingSnapshots.get(server.getId()), Collections::emptyMap);
            final Set<String> importedNames = new HashSet<>();

            for (final var snapshotDTO : dto.snapshots()) {
                importedNames.add(snapshotDTO.name());
                var snapshot = snapshots.get(snapshotDTO.name());
                if (snapshot == null) {
                    tryConsume(snapshotRepository::save, snapshotDTO.build(server), s ->
                            "beim Insert von Snapshot serverID=%s name=%s".formatted(s.getServerId(), s.getName()));
                } else if (snapshotDTO.hasChanges(snapshot)) {
                    snapshotDTO.applyChanges(snapshot);
                    tryConsume(snapshotRepository::save, snapshot, s ->
                            "beim Update von Snapshot serverID=%s name=%s".formatted(s.getServerId(), s.getName()));
                }
            }

            for (final var snapshot : snapshots.values()) {
                if (!importedNames.contains(snapshot.getName())) {
                    tryConsume(snapshotRepository::delete, snapshot, s ->
                            "beim Delete von Snapshot serverID=%s name=%s".formatted(s.getServerId(), s.getName()));
                }
            }
        }

        for (final var server : existingServers.values()) {
            if (!importedUuids.contains(server.getUuid())) {
                if (tryConsume(serverService::delete, server, s ->
                        "beim Delete von Server name=%s".formatted(s.getName()))) {
                    deleted++;
                } else {
                    errors++;
                }
            }
        }

        log.info("Cloud import finished. inserted={}, updated={}, deleted={}, skipped={}, errors={}",
                inserted, updated, deleted, skipped, errors);
    }

    private Cloud findOrCreateCloud(final CloudDTO cloudDTO) {
        final var endpoint = cloudDTO.cloud();
        final var cloud = cloudService.findByApiEndpoint(endpoint);
        if (cloud != null) {
            return cloud;
        }

        log.info("Erstelle neue Cloud mit fqdn={}", endpoint);
        tryConsume(cloudService::createCloudEntry, cloudDTO.build(), c ->
                "beim Insert der Cloud fqdn=%s".formatted(endpoint));

        return cloudService.findByApiEndpoint(endpoint);
    }

    /**
     * Wrapper for Functions such as
     * <code>entityRepository::save</code> methods.  Logs errors with
     * an appropriate message and returns the return value, or null if
     * the operation failed.
     *
     * @param function   The save function.
     * @param input      The entity to save.
     * @param descriptor Function returning a human-readable
     *                   description for error logging.
     * @return The return value of the save function, or null if an
     * error occurred.
     */
    private <T, R> R tryApply(Function<T, R> function, T input, Function<T, String> descriptor) {
        try {
            return function.apply(input);
        } catch (final ObjectOptimisticLockingFailureException ex) {
            log.warn("Versionskonflikt {} (wird beim nächsten Import erneut versucht)", descriptor.apply(input));
            return null;
        } catch (final Exception e) {
            log.error("Fehler {}: {}", descriptor.apply(input), e.getLocalizedMessage());
            return null;
        }
    }

    /**
     * Wrapper for Consumers such as
     * <code>entityRepository::delete</code> methods, analogous to
     * <code>save</code>.
     *
     * @param consumer   The delete function.
     * @param input      The entity to delete.
     * @param descriptor Function returning a human-readable
     *                   description for error logging.
     * @return Whether the operation succeeded.
     */
    private <T> boolean tryConsume(Consumer<T> consumer, T input, Function<T, String> descriptor) {
        final var result = tryApply(i -> {
            consumer.accept(i);
            return true;
        }, input, descriptor);
        return Boolean.TRUE.equals(result);
    }
}
