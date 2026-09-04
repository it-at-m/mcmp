
package de.muenchen.mcmp.clients.cloud;

import de.muenchen.mcmp.clients.cloud.model.*;
import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.cloud.CloudService;
import de.muenchen.mcmp.disk.Disk;
import de.muenchen.mcmp.disk.DiskRepository;
import de.muenchen.mcmp.mountPoint.MountPoint;
import de.muenchen.mcmp.mountPoint.MountPointRepository;
import de.muenchen.mcmp.nic.Nic;
import de.muenchen.mcmp.nic.NicRepository;
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
    private final DiskRepository diskRepository;
    private final NicRepository nicRepository;
    private final MountPointRepository mountPointRepository;

    public void importCloudData(final CloudDTO cloudDTO) {
        log.info("Starting Cloud import process for {} servers.", cloudDTO.servers().size());

        final Cloud cloud = findOrCreateCloud(cloudDTO);
        if (cloud == null) {
            log.warn("Cloud konnte nicht gefunden/erstellt werden, Import wird abgebrochen.");
            return;
        }

        // Fetch current DB state in advance.
        final var servers = serverService.findAllByCloudId(cloud.getId())
                .stream()
                .collect(Collectors.toMap(Server::getUuid, Function.identity()));
        final var snapshots = snapshotRepository.findByServerCloudId(cloud.getId())
                .stream()
                .collect(Collectors.groupingBy(Snapshot::getServerId));
        final var disks = diskRepository.findByServerCloudId(cloud.getId())
                .stream()
                .collect(Collectors.groupingBy(Disk::getServerId));
        final var mountPoints = mountPointRepository.findByServerCloudId(cloud.getId())
                .stream()
                .collect(Collectors.groupingBy(MountPoint::getServerId));
        final var nics = nicRepository.findByServerCloudId(cloud.getId())
                .stream()
                .collect(Collectors.groupingBy(Nic::getServerId));

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

            var server = servers.get(dto.uuid());
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

            final var serverSnapshots = snapshots.getOrDefault(server.getId(), new ArrayList<>());
            importSnapshots(server, serverSnapshots, dto.snapshots());

            final var serverDisks = disks.getOrDefault(server.getId(), new ArrayList<>());
            importDisks(server, serverDisks, dto.disks());

            final var serverMountPoints = mountPoints.getOrDefault(server.getId(), new ArrayList<>());
            importMountPoints(server, serverMountPoints, dto.mountPoints());

            final var serverNics = nics.getOrDefault(server.getId(), new ArrayList<>());
            importNics(server, serverNics, dto.nics());
        }

        for (final var server : servers.values()) {
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

    private void importDisks(
            final Server server,
            final List<Disk> disks,
            final List<DiskDTO> dtos
    ) {
        final Map<Integer, Disk> disksByKey = disks.stream()
                .collect(Collectors.toMap(Disk::getVdiskKey, Function.identity()));
        final Set<Integer> importedKeys = new HashSet<>();

        for (final var dto : dtos) {
            importedKeys.add(dto.vdiskKey());
            var disk = disksByKey.get(dto.vdiskKey());
            if (disk == null) {
                tryConsume(diskRepository::save, dto.build(server), s ->
                        "beim Insert von Disk serverID=%s vDiskKey=%s".formatted(s.getServerId(), s.getVdiskKey()));
            } else if (dto.hasChanges(disk)) {
                dto.applyChanges(disk);
                tryConsume(diskRepository::save, disk, s ->
                        "beim Update von Disk serverID=%s vDiskKey=%s".formatted(s.getServerId(), s.getVdiskKey()));
            }
        }

        for (final var disk : disks) {
            if (!importedKeys.contains(disk.getVdiskKey())) {
                tryConsume(diskRepository::delete, disk, s ->
                        "beim Delete von Disk serverID=%s vDiskKey=%s".formatted(s.getServerId(), s.getVdiskKey()));
            }
        }
    }

    private void importMountPoints(
            final Server server,
            final List<MountPoint> mountPoints,
            final List<MountPointDTO> dtos
    ) {
        final Map<String, MountPoint> mountPointsByPath = mountPoints.stream()
                .collect(Collectors.toMap(MountPoint::getDiskPath, Function.identity()));
        final Set<String> importedPaths = new HashSet<>();

        for (final var dto : dtos) {
            importedPaths.add(dto.diskPath());
            var mountPoint = mountPointsByPath.get(dto.diskPath());
            if (mountPoint == null) {
                tryConsume(mountPointRepository::save, dto.build(server), s ->
                        "beim Insert von MountPoint serverID=%s diskPath=%s".formatted(s.getServerId(), s.getDiskPath()));
            } else if (dto.hasChanges(mountPoint)) {
                dto.applyChanges(mountPoint);
                tryConsume(mountPointRepository::save, mountPoint, s ->
                        "beim Update von MountPoint serverID=%s diskPath=%s".formatted(s.getServerId(), s.getDiskPath()));
            }
        }

        for (final var mountPoint : mountPoints) {
            if (!importedPaths.contains(mountPoint.getDiskPath())) {
                tryConsume(mountPointRepository::delete, mountPoint, s ->
                        "beim Delete von MountPoint serverID=%s diskPath=%s".formatted(s.getServerId(), s.getDiskPath()));
            }
        }
    }

    private void importNics(
            final Server server,
            final List<Nic> nics,
            final List<NicDTO> dtos
    ) {
        final Map<Integer, Nic> nicsByKey = nics.stream()
                .collect(Collectors.toMap(Nic::getVnicKey, Function.identity()));
        final Set<Integer> importedKeys = new HashSet<>();

        for (final var dto : dtos) {
            importedKeys.add(dto.vNicKey());
            var nic = nicsByKey.get(dto.vNicKey());
            if (nic == null) {
                tryConsume(nicRepository::save, dto.build(server), s ->
                        "beim Insert von NIC serverID=%s vNicKey=%d".formatted(s.getServerId(), s.getVnicKey()));
            } else if (dto.hasChanges(nic)) {
                dto.applyChanges(nic);
                tryConsume(nicRepository::save, nic, s ->
                        "beim Update von Nic serverID=%s vNicKey=%d".formatted(s.getServerId(), s.getVnicKey()));
            }
        }

        for (final var nic : nics) {
            if (!importedKeys.contains(nic.getVnicKey())) {
                tryConsume(nicRepository::delete, nic, s ->
                        "beim Delete von Nic serverID=%s vDiskKey=%d".formatted(s.getServerId(), s.getVnicKey()));
            }
        }
    }

    private void importSnapshots(
            final Server server,
            final List<Snapshot> snapshots,
            final List<SnapshotDTO> dtos
    ) {
        final Map<String, Snapshot> snapshotsByName = snapshots.stream()
                .collect(Collectors.toMap(Snapshot::getName, Function.identity()));
        final Set<String> importedNames = new HashSet<>();

        for (final var dto : dtos) {
            importedNames.add(dto.name());
            var snapshot = snapshotsByName.get(dto.name());
            if (snapshot == null) {
                tryConsume(snapshotRepository::save, dto.build(server), s ->
                        "beim Insert von Snapshot serverID=%s name=%s".formatted(s.getServerId(), s.getName()));
            } else if (dto.hasChanges(snapshot)) {
                dto.applyChanges(snapshot);
                tryConsume(snapshotRepository::save, snapshot, s ->
                        "beim Update von Snapshot serverID=%s name=%s".formatted(s.getServerId(), s.getName()));
            }
        }

        for (final var snapshot : snapshots) {
            if (!importedNames.contains(snapshot.getName())) {
                tryConsume(snapshotRepository::delete, snapshot, s ->
                        "beim Delete von Snapshot serverID=%s name=%s".formatted(s.getServerId(), s.getName()));
            }
        }
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
