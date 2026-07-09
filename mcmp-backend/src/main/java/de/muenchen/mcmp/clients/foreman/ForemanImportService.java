package de.muenchen.mcmp.clients.foreman;

import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.mountPoint.MountPoint;
import de.muenchen.mcmp.mountPoint.MountPointRepository;
import de.muenchen.mcmp.ontap.*;
import de.muenchen.mcmp.repository.Repository;
import de.muenchen.mcmp.repository.RepositoryRepository;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import de.muenchen.mcmp.server.matching.ServerMatcher;
import de.muenchen.mcmp.sleeper.ThreadSleeper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ForemanImportService {

    private static final int SAVE_RETRY_ATTEMPTS = 3;

    private final ServerRepository serverRepository;
    private final MountPointRepository mountPointRepository;
    private final EntityManager entityManager;
    private final OntapQtreeRepository ontapQtreeRepository;
    private final OntapQtreeServerMountRepository ontapQtreeServerMountRepository;
    private final OntapVolumeRepository ontapVolumeRepository;
    private final OntapVolumeServerMountRepository ontapVolumeServerMountRepository;
    private final RepositoryRepository repositoryRepository;

    /**
     * Processes a single host in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processHostInNewTransaction(HostDTO hostDTO, Set<Long> processedServerIDs, List<ServerMatcher<HostDTO>> matchingStrategies) {
        if (hostDTO == null) {
            return;
        }

        Server server = matchHost(hostDTO, matchingStrategies);

        if (server != null) {
            log.debug("Matched host {} to server ID {}", hostDTO.name(), server.getId());

            if (isUpdateNeeded(server, hostDTO)) {
                log.debug("Server {} needs update", server.getName());
                server = saveServerWithRetry(server, hostDTO);
                if (server == null) {
                    log.error("Failed to save server after all retry attempts for host {}", hostDTO.name());
                    return;
                }
            }

            processedServerIDs.add(server.getId());
            if (server.getManaged() && !server.getRoleWindows()) {
                processMountPointsWithFallback(server, hostDTO);
                syncOntapServerMounts(server, hostDTO.mountpoints());
                syncRepositories(server, hostDTO.repositories());
            } else {
                // If server is unmanaged or Windows, ensure no repository assignments exist
                if (repositoryRepository.existsAssignmentsByServerId(server.getId())) {
                    repositoryRepository.deleteAssignmentsByServerId(server.getId());
                    log.debug("Cleared repository assignments for server {} (now unmanaged or Windows)", server.getName());
                }
            }
        } else {
            log.warn("Foreman Host {} from {} with ID {} could not be matched to any server", hostDTO.name(), hostDTO.source(), hostDTO.id());
        }
    }

    /**
     * Pre-processes all hosts to ensure all repositories exist in the database.
     * This is a performance optimization to avoid individual repository lookups per server.
     *
     * @param hosts the list of hosts to process
     */
    @Transactional
    public void ensureAllRepositoriesExist(final List<HostDTO> hosts) {
        log.info("Pre-processing repositories from {} hosts", hosts.size());

        // 1) Collect all unique repository names from all hosts
        final Set<String> allRepoNames = hosts.stream()
                .filter(host -> host.repositories() != null)
                .flatMap(host -> host.repositories().stream())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());

        if (allRepoNames.isEmpty()) {
            log.debug("No repositories found in host data");
            return;
        }

        log.info("Found {} unique repositories across all hosts", allRepoNames.size());

        // 2) Load existing repositories from database
        final List<Repository> existingRepos = repositoryRepository.findAll();
        final Set<String> existingRepoNames = existingRepos.stream()
                .map(Repository::getName)
                .collect(Collectors.toSet());

        // 3) Identify missing repositories
        final Set<String> missingRepoNames = allRepoNames.stream()
                .filter(name -> !existingRepoNames.contains(name))
                .collect(Collectors.toSet());

        if (missingRepoNames.isEmpty()) {
            log.debug("All repositories already exist in database");
            return;
        }

        // 4) Create all missing repositories in batch
        log.info("Creating {} new repositories", missingRepoNames.size());
        final List<Repository> newRepos = missingRepoNames.stream()
                .map(name -> {
                    Repository repo = new Repository();
                    repo.setName(name);
                    return repo;
                })
                .toList();

        repositoryRepository.saveAll(newRepos);
        log.info("Successfully created {} new repositories", newRepos.size());
    }

    /**
     * Synchronizes ONTAP server mounts incrementally.
     * - Updates existing mounts if changed
     * - Adds new mounts
     * - Removes mounts that no longer exist in Foreman
     * <p>
     * This method is optimized for frequent execution and minimizes database operations.
     */
    private void syncOntapServerMounts(final Server server, final List<MountpointDTO> mountpoints) {
        if (server == null || server.getId() == null) {
            log.warn("Cannot sync ONTAP mounts: server or server ID is null");
            return;
        }

        // 1) Load existing mounts from database
        final List<OntapQtreeServerMount> existingQtreeMounts = ontapQtreeServerMountRepository.findAllByServerId(server.getId());
        final List<OntapVolumeServerMount> existingVolumeMounts = ontapVolumeServerMountRepository.findAllByServerId(server.getId());

        // 2) Handle case where no mountpoints exist in Foreman -> delete all
        if (mountpoints == null || mountpoints.isEmpty()) {
            deleteMounts(server.getId(), existingQtreeMounts, existingVolumeMounts);
            return;
        }

        final Map<String, OntapServerMount> existingMountMap = new HashMap<>();
        existingMountMap.putAll(buildExistingMap(existingQtreeMounts));
        existingMountMap.putAll(buildExistingMap(existingVolumeMounts));

        // 3) Prepare Foreman mount data - group by normalized device
        final Map<String, List<MountpointDTO>> mountpointsByDevice = groupMountpointsByDevice(mountpoints);
        if (mountpointsByDevice.isEmpty()) {
            deleteMounts(server.getId(), existingQtreeMounts, existingVolumeMounts);
            return;
        }

        // 4) Load ONTAP entities (qtrees & volumes) for the devices
        final List<String> devices = new ArrayList<>(mountpointsByDevice.keySet());
        final Map<String, AbstractEntity> ontapEntitiesByDevice = loadOntapEntitiesByDevice(devices);

        // 5) Build maps of existing mounts for quick lookup
        final Set<String> processedKeys = new HashSet<>();

        // 6) Track which mounts should exist according to Foreman
        final List<OntapServerMount> mountsToSave = new ArrayList<>();

        // 7) Process all Foreman mountpoints and sync with database
        for (final Map.Entry<String, List<MountpointDTO>> entry : mountpointsByDevice.entrySet()) {
            final String device = entry.getKey();
            final AbstractEntity ontapEntity = ontapEntitiesByDevice.get(device);

            if (ontapEntity == null) {
                log.debug("No ONTAP match for server {} mount device '{}'", server.getName(), device);
                continue;
            }

            for (final MountpointDTO mp : entry.getValue()) {
                final String mountPoint = normalizeMountPoint(mp.mountPoint());
                final String filesystem = normalizeFilesystem(mp.filesystem());
                final List<String> options = sortOptions(mp.options());

                final String key = buildMountKey(ontapEntity.getId(), mountPoint);
                processedKeys.add(key);

                final OntapServerMount existingMount = existingMountMap.get(key);

                if (existingMount != null) {
                    // Mount exists - check if update needed
                    if (isMountChanged(existingMount.getFilesystem(), filesystem, existingMount.getOptions(), options)) {
                        existingMount.setFilesystem(filesystem);
                        existingMount.setOptions(options);
                        mountsToSave.add(existingMount);
                    }
                } else {
                    // New mount - create it
                    final OntapServerMount newMount;
                    if (ontapEntity instanceof OntapQtree) {
                        newMount = new OntapQtreeServerMount();
                        ((OntapQtreeServerMount) newMount).setOntapQtree((OntapQtree) ontapEntity);
                    } else if (ontapEntity instanceof OntapVolume) {
                        newMount = new OntapVolumeServerMount();
                        ((OntapVolumeServerMount) newMount).setOntapVolume((OntapVolume) ontapEntity);
                    } else {
                        continue;
                    }
                    newMount.setServer(server);
                    newMount.setMountPoint(mountPoint);
                    newMount.setFilesystem(filesystem);
                    newMount.setOptions(options);
                    mountsToSave.add(newMount);
                }
            }
        }

        // 8) Save all new or modified mounts
        saveMountsByType(mountsToSave, server.getName());

        // 9) Delete mounts that no longer exist in Foreman
        deleteStaleMounts(server.getId(), existingMountMap, processedKeys);
    }

    /**
     * Groups mountpoints by normalized device name.
     */
    private Map<String, List<MountpointDTO>> groupMountpointsByDevice(final List<MountpointDTO> mountpoints) {
        final Map<String, List<MountpointDTO>> byDevice = new HashMap<>();
        for (final MountpointDTO mp : mountpoints) {
            if (mp == null || mp.device() == null || mp.device().isBlank()) {
                continue;
            }
            byDevice.computeIfAbsent(normalizeDevice(mp.device()), k -> new ArrayList<>()).add(mp);
        }
        return byDevice;
    }

    /**
     * Loads volumes and qtrees by their mount path and returns a map keyed by normalized device.
     */
    private Map<String, AbstractEntity> loadOntapEntitiesByDevice(final List<String> devices) {
        final Map<String, AbstractEntity> entitiesByDevice = new HashMap<>();

        // add Qtrees
        final List<OntapQtree> qtrees = ontapQtreeRepository.findAllByMountPathNfsIn(devices);
        for (final OntapQtree q : qtrees) {
            if (q != null && q.getMountPathNfs() != null) {
                entitiesByDevice.put(normalizeDevice(q.getMountPathNfs()), q);
            }
        }

        // Add volumes (Qtrees and volumes have different mount paths, no overwrites expected)
        final List<OntapVolume> volumes = ontapVolumeRepository.findAllByMountPathNfsIn(devices);
        for (final OntapVolume v : volumes) {
            if (v != null && v.getMountPathNfs() != null) {
                entitiesByDevice.put(normalizeDevice(v.getMountPathNfs()), v);
            }
        }

        return entitiesByDevice;
    }

    /**
     * Generic helper method to build a map of existing mounts keyed by a composite key.
     * Uses the OntapServerMount interface for type safety.
     */
    private <T extends OntapServerMount> Map<String, T> buildExistingMap(final List<T> existingMounts) {
        final Map<String, T> map = new HashMap<>();
        for (final T mount : existingMounts) {
            if (mount != null && mount.getOntapEntity() != null && mount.getOntapEntity().getId() != null) {
                final String key = buildMountKey(mount.getOntapEntity().getId(), mount.getMountPoint());
                map.put(key, mount);
            }
        }
        return map;
    }

    private void saveMountsByType(final List<OntapServerMount> mountsToSave, final String serverName) {
        final List<OntapQtreeServerMount> qtreeMounts = new ArrayList<>();
        final List<OntapVolumeServerMount> volumeMounts = new ArrayList<>();

        for (final OntapServerMount mount : mountsToSave) {
            if (mount instanceof OntapQtreeServerMount) {
                qtreeMounts.add((OntapQtreeServerMount) mount);
            } else if (mount instanceof OntapVolumeServerMount) {
                volumeMounts.add((OntapVolumeServerMount) mount);
            }
        }

        if (!qtreeMounts.isEmpty()) {
            ontapQtreeServerMountRepository.saveAll(qtreeMounts);
            log.debug("Saved {} qtree mounts for server {}", qtreeMounts.size(), serverName);
        }
        if (!volumeMounts.isEmpty()) {
            ontapVolumeServerMountRepository.saveAll(volumeMounts);
            log.debug("Saved {} volume mounts for server {}", volumeMounts.size(), serverName);
        }
    }

    /**
     * Sorts mount options alphabetically for consistent storage.
     * Returns null if input is null or empty.
     */
    private List<String> sortOptions(final List<String> options) {
        if (options == null || options.isEmpty()) {
            return options;
        }
        return options.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    /**
     * Checks if a mount has changed by comparing filesystem and options.
     * Options are compared ignoring order (only content matters).
     */
    private boolean isMountChanged(final String existingFilesystem, final String newFilesystem,
                                   final List<String> existingOptions, final List<String> newOptions) {
        if (!Objects.equals(existingFilesystem, newFilesystem)) {
            return true;
        }

        // Compare options lists (ignoring order)
        if (existingOptions == null && newOptions == null) {
            return false;
        }
        if (existingOptions == null || newOptions == null) {
            return true;
        }
        if (existingOptions.size() != newOptions.size()) {
            return true;
        }

        // Compare as sets to ignore order
        return !new HashSet<>(existingOptions).equals(new HashSet<>(newOptions));
    }

    /**
     * Deletes mounts that no longer exist in Foreman.
     */
    private void deleteStaleMounts(final Long serverId, final Map<String, OntapServerMount> existingMountMap, final Set<String> processedKeys) {
        final List<OntapServerMount> mountsToDelete = existingMountMap.entrySet().stream()
                .filter(entry -> !processedKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        final List<OntapQtreeServerMount> qtreeMountsToDelete = new ArrayList<>();
        final List<OntapVolumeServerMount> volumeMountsToDelete = new ArrayList<>();

        for (final OntapServerMount mount : mountsToDelete) {
            if (mount instanceof OntapQtreeServerMount) {
                qtreeMountsToDelete.add((OntapQtreeServerMount) mount);
            } else if (mount instanceof OntapVolumeServerMount) {
                volumeMountsToDelete.add((OntapVolumeServerMount) mount);
            }
        }

        deleteMounts(serverId, qtreeMountsToDelete, volumeMountsToDelete);
    }

    /**
     * Synchronizes server repositories incrementally.
     * - Creates missing repositories in the database
     * - Adds new repository assignments to the server
     * - Removes assignments that no longer exist in Foreman
     */
    private void syncRepositories(final Server server, final List<String> repositoryNames) {
        if (server == null || server.getId() == null) {
            log.warn("Cannot sync repositories: server or server ID is null");
            return;
        }

        // 1) Load existing assignments from database
        final List<Repository> currentRepos = repositoryRepository.findAllByServersId(server.getId());
        final Set<String> currentRepoNames = currentRepos.stream()
                .map(Repository::getName)
                .collect(Collectors.toSet());

        // 2) Handle case where no repositories exist in Foreman -> clear all assignments
        if (repositoryNames == null || repositoryNames.isEmpty()) {
            if (!currentRepos.isEmpty()) {
                for (Repository repo : currentRepos) {
                    repo.getServers().remove(server);
                }
                repositoryRepository.saveAll(currentRepos);
                log.debug("Cleared all repository assignments for server {}", server.getName());
            }
            return;
        }

        final Set<String> targetRepoNames = new HashSet<>(repositoryNames);

        // 3) Identify repos to add and remove
        final Set<String> reposToAdd = targetRepoNames.stream()
                .filter(name -> !currentRepoNames.contains(name))
                .collect(Collectors.toSet());

        final List<Repository> reposToRemove = currentRepos.stream()
                .filter(repo -> !targetRepoNames.contains(repo.getName()))
                .toList();

        boolean changed = false;

        // 4) Process removals via native query to avoid updating Repository entity
        if (!reposToRemove.isEmpty()) {
            for (Repository repo : reposToRemove) {
                repositoryRepository.deleteAssignment(repo.getId(), server.getId());
            }
            changed = true;
            log.debug("Removed {} repository assignments from server {}", reposToRemove.size(), server.getName());
        }

        // 5) Process additions
        if (!reposToAdd.isEmpty()) {
            for (String name : reposToAdd) {
                Repository repository = getOrCreateRepositoryFromCache(name);
                // Native check and insert to avoid loading/updating the Repository entity
                if (!repositoryRepository.existsAssignment(repository.getId(), server.getId())) {
                    repositoryRepository.insertAssignment(repository.getId(), server.getId());
                    changed = true;
                }
            }
            log.debug("Added repository assignments to server {}", server.getName());
        }

        if (changed) {
            log.debug("Successfully synchronized repositories for server {}", server.getName());
        }
    }

    /**
     * Gets or creates a repository using an in-memory cache to avoid repeated DB lookups.
     * This method should be called within a transaction-scoped cache.
     */
    private Repository getOrCreateRepositoryFromCache(final String name) {
        return repositoryRepository.findByName(name)
                .orElseGet(() -> {
                    Repository newRepo = new Repository();
                    newRepo.setName(name);
                    return repositoryRepository.save(newRepo);
                });
    }

    /**
     * Generic method to delete qtree and volume mounts with logging.
     */
    private void deleteMounts(final Long serverId,
                              final List<OntapQtreeServerMount> qtreeMountsToDelete,
                              final List<OntapVolumeServerMount> volumeMountsToDelete) {
        if (!qtreeMountsToDelete.isEmpty()) {
            ontapQtreeServerMountRepository.deleteAll(qtreeMountsToDelete);
            log.debug("Deleted {} qtree mounts for server ID {}", qtreeMountsToDelete.size(), serverId);
        }
        if (!volumeMountsToDelete.isEmpty()) {
            ontapVolumeServerMountRepository.deleteAll(volumeMountsToDelete);
            log.debug("Deleted {} volume mounts for server ID {}", volumeMountsToDelete.size(), serverId);
        }
    }

    /**
     * Builds a unique key for a mount (id + mountPoint).
     */
    private String buildMountKey(final Long id, final String mountPoint) {
        return id + ":" + (mountPoint != null ? mountPoint : "");
    }

    /**
     * Normalizes a mount point by trimming whitespace.
     */
    private String normalizeMountPoint(final String mountPoint) {
        if (mountPoint == null || mountPoint.isBlank()) {
            return null;
        }
        return mountPoint.trim();
    }

    /**
     * Normalizes a filesystem path by trimming whitespace.
     */
    private String normalizeFilesystem(final String filesystem) {
        if (filesystem == null || filesystem.isBlank()) {
            return null;
        }
        return filesystem.trim();
    }

    private String normalizeDevice(final String device) {
        if (device == null) return null;
        return device.trim().replaceAll("/+$", "");
    }


    /**
     * Resets a single server's Foreman fields in a new transaction.
     * Uses retry logic to handle concurrent modifications.
     *
     * @param serverId the server ID to reset
     * @return true if reset was applied, false if no reset needed, null if failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Boolean resetServerInNewTransaction(Long serverId) {
        return resetServerWithRetry(serverId);
    }

    private Server matchHost(HostDTO hostDTO, List<ServerMatcher<HostDTO>> matchingStrategies) {
        return matchingStrategies.stream()
                .map(strategy -> strategy.match(hostDTO))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if a server needs to be updated based on Foreman host data.
     * Compares all relevant fields to determine if changes are necessary.
     *
     * @param server  the cached server instance
     * @param hostDTO the Foreman host data
     * @return true if update is needed, false otherwise
     */
    private boolean isUpdateNeeded(final Server server, final HostDTO hostDTO) {
        final Long expectedForemanId = hostDTO.id() != null ? Long.valueOf(hostDTO.id()) : null;
        final String expectedFqdn = hostDTO.fqdn() != null ? hostDTO.fqdn() : hostDTO.name();
        final String expectedPatchnightGroup = hostDTO.patchnightGroup() != null
                ? hostDTO.patchnightGroup().toUpperCase()
                : null;
        final Short expectedExitcode = parseExitcode(hostDTO.lhmPnExitcode());
        final Boolean expectedTetrationAgent = hostDTO.tetrationAgentIsInstalled();
        final Boolean expectedNonOracleRole = calculateNonOracleRole(hostDTO);

        // Check basic fields
        if (!Objects.equals(server.getManaged(), true) ||
            !Objects.equals(server.getForemanSource(), hostDTO.source()) ||
            !Objects.equals(server.getForemanId(), expectedForemanId) ||
            !Objects.equals(server.getFqdn(), expectedFqdn) ||
            !Objects.equals(server.getTetrationAgentInstalled(), expectedTetrationAgent) ||
            (hostDTO.linux() && (
            !Objects.equals(server.getPatchnightTime(), hostDTO.patchnightStartTime()) ||
            !Objects.equals(server.getPatchnightGroup(), expectedPatchnightGroup) ||
            !Objects.equals(server.getPatchnightExitstring(), hostDTO.lhmPnExitstring()) ||
            !Objects.equals(server.getPatchnightExitcode(), expectedExitcode)))) {
            return true;
        }

        if (hostDTO.linux() && !Objects.equals(server.getOperatingsystem(), hostDTO.operatingsystemName())) {
            return true;
        }

        // Check server info fields
        if (hostDTO.serverInfosTicketnr() != null && !hostDTO.serverInfosTicketnr().isBlank()
            && !Objects.equals(server.getServerInfosTicketNo(), hostDTO.serverInfosTicketnr())) {
            return true;
        }
        if (hostDTO.serverInfosOwnerMail() != null && !hostDTO.serverInfosOwnerMail().isBlank()
            && !Objects.equals(server.getServerInfosOwnerMail(), hostDTO.serverInfosOwnerMail())) {
            return true;
        }

        // Check database flags
        if (!Objects.equals(server.getDbOracle(), hostDTO.oracleDb()) ||
            !Objects.equals(server.getDbMariadb(), hostDTO.mariaDb()) ||
            !Objects.equals(server.getDbMysql(), hostDTO.mysqlDb()) ||
            !Objects.equals(server.getDbMssql(), hostDTO.mssqlDb()) ||
            !Objects.equals(server.getDbPostgres(), hostDTO.postgresDb()) ||
            !Objects.equals(server.getDbMongodb(), hostDTO.mongoDb()) ||
            !Objects.equals(server.getDbAdabas(), hostDTO.adabasDb())) {
            return true;
        }

        // Check role flags
        return !Objects.equals(server.getRoleWindows(), hostDTO.windows()) ||
               !Objects.equals(server.getRoleLinux(), hostDTO.linux()) ||
               !Objects.equals(server.getRoleOracle(), hostDTO.oracleDb()) ||
               !Objects.equals(server.getRoleNonOracle(), expectedNonOracleRole);
    }

    /**
     * Saves a server with retry logic in case of concurrent updates.
     * Reloads server from database and reapplies Foreman data on each retry.
     *
     * @param server  the server to save
     * @param hostDTO the Foreman host data to apply
     * @return the saved server, or null if all retries failed
     */
    private Server saveServerWithRetry(final Server server, final HostDTO hostDTO) {
        for (int attempt = 1; attempt <= SAVE_RETRY_ATTEMPTS; attempt++) {
            try {
                if (attempt > 1) {
                    entityManager.detach(server);  // WICHTIG: Entity aus Context entfernen
                }

                final Server freshServer = serverRepository.findById(server.getId())
                        .orElseThrow(() -> new IllegalStateException("Server not found: " + server.getId()));

                updateServerFromHost(freshServer, hostDTO);

                final Server savedServer = serverRepository.save(freshServer);

                log.debug("Successfully saved server {} on attempt {}", savedServer.getName(), attempt);
                return savedServer;
            } catch (OptimisticLockException | OptimisticLockingFailureException e) {
                log.warn("Optimistic locking conflict for server {} (attempt {}/{})", server.getName(), attempt, SAVE_RETRY_ATTEMPTS);

                if (attempt < SAVE_RETRY_ATTEMPTS) {
                    new ThreadSleeper().sleep(50 * attempt);
                } else {
                    log.error("Optimistic locking retries exhausted for server {}", server.getName(), e);
                }
            } catch (Exception e) {
                log.warn("Failed to save server {} (attempt {}/{}): {} - {}", server.getName(), attempt, SAVE_RETRY_ATTEMPTS, e.getClass().getSimpleName(), e.getMessage());

                if (attempt < SAVE_RETRY_ATTEMPTS) {
                    new ThreadSleeper().sleep(50 * attempt);
                } else {
                    log.error("All retry attempts exhausted for server {}", server.getName(), e);
                }
            }
        }
        return null;
    }

    /**
     * Updates a server entity with data from a Foreman host.
     *
     * @param server  the server to update
     * @param hostDTO the Foreman host data
     */
    private void updateServerFromHost(final Server server, final HostDTO hostDTO) {
        if (hostDTO.id() == null) {
            log.warn("Cannot update server {}: Foreman host ID is null", server.getName());
            return;
        }

        final String newFqdn = hostDTO.fqdn() != null ? hostDTO.fqdn() : hostDTO.name();
        final String newPatchnightGroup = hostDTO.patchnightGroup() != null
                ? hostDTO.patchnightGroup().toUpperCase()
                : null;
        final Short newExitcode = parseExitcode(hostDTO.lhmPnExitcode());
        final Boolean newTetrationAgent = hostDTO.tetrationAgentIsInstalled();

        // Basic fields
        server.setManaged(true);
        server.setForemanSource(hostDTO.source());
        server.setForemanId(hostDTO.id() != null ? hostDTO.id().longValue() : null);
        server.setFqdn(newFqdn);
        if (hostDTO.linux()) {
            server.setPatchnightTime(hostDTO.patchnightStartTime());
            server.setPatchnightGroup(newPatchnightGroup);
            server.setPatchnightExitstring(hostDTO.lhmPnExitstring());
            server.setPatchnightExitcode(newExitcode);
        }
        server.setTetrationAgentInstalled(newTetrationAgent);

        // Server info fields (only update if provided)
        if (hostDTO.serverInfosTicketnr() != null && !hostDTO.serverInfosTicketnr().isBlank()) {
            server.setServerInfosTicketNo(hostDTO.serverInfosTicketnr());
        }
        if (hostDTO.serverInfosOwnerMail() != null && !hostDTO.serverInfosOwnerMail().isBlank()) {
            server.setServerInfosOwnerMail(hostDTO.serverInfosOwnerMail());
        }

        // Database flags
        server.setDbOracle(hostDTO.oracleDb());
        server.setDbMariadb(hostDTO.mariaDb());
        server.setDbMysql(hostDTO.mysqlDb());
        server.setDbMssql(hostDTO.mssqlDb());
        server.setDbPostgres(hostDTO.postgresDb());
        server.setDbMongodb(hostDTO.mongoDb());
        server.setDbAdabas(hostDTO.adabasDb());

        // Role flags
        server.setRoleWindows(hostDTO.windows());
        server.setRoleLinux(hostDTO.linux());
        server.setRoleOracle(hostDTO.oracleDb());
        server.setRoleNonOracle(calculateNonOracleRole(hostDTO));

        if (hostDTO.linux()) {
            server.setOperatingsystem(hostDTO.operatingsystemName());
        }
    }

    /**
     * Processes mount points for a server.
     * Attempts batch save first, falls back to individual saves on error.
     */
    private void processMountPointsWithFallback(Server server, HostDTO hostDTO) {
        final List<MountPoint> mountPoints = mountPointRepository.findAllByServerId(server.getId());

        if (mountPoints == null || mountPoints.isEmpty()) {
            return;
        }

        if (hostDTO.partitions() == null) {
            hideAllMountPoints(mountPoints);
            return;
        }

        final Map<String, PartitionDTO> foremanPartitions = buildPartitionMap(hostDTO.partitions());
        final List<MountPoint> toUpdate = new ArrayList<>();

        for (final MountPoint mountPoint : mountPoints) {
            if (mountPoint == null || mountPoint.getDiskPath() == null || mountPoint.getDiskPath().isBlank()) {
                continue;
            }

            if (updateMountPointData(mountPoint, foremanPartitions.get(mountPoint.getDiskPath()))) {
                toUpdate.add(mountPoint);
            }
        }

        if (!toUpdate.isEmpty()) {
            try {
                // Versuche batch save
                mountPointRepository.saveAll(toUpdate);
                log.debug("Saved {} mount points in batch for server {}", toUpdate.size(), server.getName());
            } catch (Exception e) {
                log.warn("Batch save failed for server {}, falling back to individual saves: {}", server.getName(), e.getMessage());
                saveIndividualMountPoints(toUpdate, server.getName());
            }
        }
    }

    /**
     * Saves mount points individually when batch save fails.
     * Reloads each mount point from database if save fails and retries with updated values.
     */
    private void saveIndividualMountPoints(List<MountPoint> mountPoints, String serverName) {
        int successCount = 0;
        int failCount = 0;

        for (final MountPoint mountPoint : mountPoints) {
            boolean saved = saveMountPointWithRetry(mountPoint, serverName);
            if (saved) {
                successCount++;
            } else {
                failCount++;
            }
        }

        log.info("Individual save for server {}: {} succeeded, {} failed",
                serverName, successCount, failCount);
    }

    /**
     * Saves a single mount point with retry logic.
     * Reloads from database and reapplies changes on retry.
     *
     * @param mountPoint the mount point to save
     * @param serverName the server name for logging
     * @return true if save was successful, false otherwise
     */
    private boolean saveMountPointWithRetry(MountPoint mountPoint, String serverName) {
        final Boolean targetHidden = mountPoint.getHidden();
        final String targetForemanUuid = mountPoint.getForemanUuid();
        final Long targetForemanCapacityInBytes = mountPoint.getForemanCapacityInBytes();
        final String targetForemanPartition = mountPoint.getForemanPartition();
        final String targetForemanParttype = mountPoint.getForemanParttype();
        final String targetForemanPartuuid = mountPoint.getForemanPartuuid();
        final Boolean targetEditable = mountPoint.getEditable();

        for (int attempt = 1; attempt <= SAVE_RETRY_ATTEMPTS; attempt++) {
            try {
                MountPoint freshMountPoint;

                if (attempt == 1) {
                    freshMountPoint = mountPoint;
                } else {
                    if (mountPoint.getId() == null) {
                        log.warn("Cannot reload mount point without ID for server {}", serverName);
                        return false;
                    }

                    entityManager.detach(mountPoint);

                    freshMountPoint = mountPointRepository.findById(mountPoint.getId()).orElse(null);
                    if (freshMountPoint == null) {
                        log.warn("Mount point with ID {} not found in database for server {}", mountPoint.getId(), serverName);
                        return false;
                    }

                    log.debug("Reloaded mount point {} from database for retry attempt {}", freshMountPoint.getDiskPath(), attempt);

                    freshMountPoint.setHidden(targetHidden);
                    freshMountPoint.setForemanUuid(targetForemanUuid);
                    freshMountPoint.setForemanCapacityInBytes(targetForemanCapacityInBytes);
                    freshMountPoint.setForemanPartition(targetForemanPartition);
                    freshMountPoint.setForemanParttype(targetForemanParttype);
                    freshMountPoint.setForemanPartuuid(targetForemanPartuuid);
                    freshMountPoint.setEditable(targetEditable);
                }

                mountPointRepository.save(freshMountPoint);
                log.debug("Successfully saved mount point {} for server {} on attempt {}", freshMountPoint.getDiskPath(), serverName, attempt);
                return true;

            } catch (OptimisticLockException | OptimisticLockingFailureException e) {
                log.warn("Optimistic locking conflict for mount point {} (attempt {}/{})", mountPoint.getDiskPath(), attempt, SAVE_RETRY_ATTEMPTS);

                if (attempt < SAVE_RETRY_ATTEMPTS) {
                    new ThreadSleeper().sleep(50 * attempt);
                } else {
                    log.error("Optimistic locking retries exhausted for mount point {}", mountPoint.getDiskPath(), e);
                }
            } catch (Exception e) {
                log.error("Unexpected error saving mount point {} for server {} (attempt {}/{}): {}", mountPoint.getDiskPath(), serverName, attempt, SAVE_RETRY_ATTEMPTS, e.getMessage(), e);

                if (attempt >= SAVE_RETRY_ATTEMPTS) {
                    log.error("All retry attempts exhausted for mount point {} on server {}", mountPoint.getDiskPath(), serverName);
                    return false;
                }
                new ThreadSleeper().sleep(20 * attempt);
            }
        }

        return false;
    }

    /**
     * Updates a single mount point with partition data from Foreman.
     *
     * @param mountPoint   the mount point to update
     * @param partitionDTO the partition data from Foreman (can be null)
     * @return true if the mount point was modified, false otherwise
     */
    private boolean updateMountPointData(MountPoint mountPoint, PartitionDTO partitionDTO) {
        boolean needsUpdate = false;

        if (partitionDTO != null) {
            // Partition exists in Foreman
            if (!Boolean.FALSE.equals(mountPoint.getHidden())) {
                mountPoint.setHidden(false);
                needsUpdate = true;
            }

            if (!Objects.equals(mountPoint.getForemanUuid(), partitionDTO.uuid())) {
                mountPoint.setForemanUuid(partitionDTO.uuid());
                needsUpdate = true;
            }

            if (!Objects.equals(mountPoint.getForemanCapacityInBytes(), partitionDTO.sizeBytes())) {
                mountPoint.setForemanCapacityInBytes(partitionDTO.sizeBytes());
                needsUpdate = true;
            }

            if (!Objects.equals(mountPoint.getForemanPartition(), partitionDTO.partition())) {
                mountPoint.setForemanPartition(partitionDTO.partition());
                needsUpdate = true;
            }

            if (!Objects.equals(mountPoint.getForemanParttype(), partitionDTO.partType())) {
                mountPoint.setForemanParttype(partitionDTO.partType());
                needsUpdate = true;
            }

            if (!Objects.equals(mountPoint.getForemanPartuuid(), partitionDTO.partUUID())) {
                mountPoint.setForemanPartuuid(partitionDTO.partUUID());
                needsUpdate = true;
            }

            final boolean editable = partitionDTO.partType() == null || partitionDTO.partType().isBlank();
            if (!Objects.equals(mountPoint.getEditable(), editable)) {
                mountPoint.setEditable(editable);
                needsUpdate = true;
            }
        } else {
            // Partition not in Foreman, hide it
            if (mountPoint != null && (Boolean.FALSE.equals(mountPoint.getHidden()) || mountPoint.getForemanUuid() != null)) {
                mountPoint.setHidden(true);
                mountPoint.setEditable(false);
                mountPoint.setForemanUuid(null);
                mountPoint.setForemanCapacityInBytes(null);
                mountPoint.setForemanPartition(null);
                mountPoint.setForemanParttype(null);
                mountPoint.setForemanPartuuid(null);
                needsUpdate = true;
            }
        }

        return needsUpdate;
    }

    /**
     * Builds a map of partition DTOs keyed by mount point path.
     */
    private Map<String, PartitionDTO> buildPartitionMap(List<PartitionDTO> partitions) {
        if (partitions == null || partitions.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, PartitionDTO> map = new HashMap<>();
        for (final PartitionDTO partitionDTO : partitions) {
            if (partitionDTO != null && partitionDTO.mountPoint() != null && !partitionDTO.mountPoint().isBlank()) {
                map.put(partitionDTO.mountPoint(), partitionDTO);
            }
        }

        return map;
    }

    /**
     * Hides all mount points (when no partition data is available from Foreman).
     * Attempts batch save first, falls back to individual saves with retry on error.
     */
    private void hideAllMountPoints(List<MountPoint> mountPoints) {
        final List<MountPoint> toUpdate = new ArrayList<>();

        for (final MountPoint mountPoint : mountPoints) {
            if (mountPoint != null && (mountPoint.getHidden() != false || mountPoint.getForemanUuid() != null)) {
                mountPoint.setHidden(true);
                mountPoint.setEditable(false);
                mountPoint.setForemanUuid(null);
                mountPoint.setForemanCapacityInBytes(null);
                mountPoint.setForemanPartition(null);
                mountPoint.setForemanParttype(null);
                mountPoint.setForemanPartuuid(null);
                toUpdate.add(mountPoint);
            }
        }

        if (!toUpdate.isEmpty()) {
            try {
                mountPointRepository.saveAll(toUpdate);
                log.debug("Hidden {} mount points in batch", toUpdate.size());
            } catch (Exception e) {
                log.warn("Batch hide failed, using individual saves with retry: {}", e.getMessage());

                // Reuse existing individual save logic
                int successCount = 0;
                int failCount = 0;

                for (MountPoint mountPoint : toUpdate) {
                    if (saveMountPointWithRetry(mountPoint, "batch-hide")) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                }

                log.info("Individual hide: {} succeeded, {} failed", successCount, failCount);
            }
        }
    }

    /**
     * Resets Foreman fields of a server with retry logic.
     * Reloads server from database and reapplies reset on each retry.
     *
     * @param serverId the server ID to reset
     * @return true if reset was applied, false if no reset needed, null if all retries failed
     */
    private Boolean resetServerWithRetry(final Long serverId) {
        for (int attempt = 1; attempt <= SAVE_RETRY_ATTEMPTS; attempt++) {
            try {
                if (attempt > 1) {
                    entityManager.clear();  // Alle Entities aus Context entfernen
                }

                final Server freshServer = serverRepository.findById(serverId)
                        .orElseThrow(() -> new IllegalStateException("Server not found: " + serverId));

                if (resetServerForemanFields(freshServer)) {
                    serverRepository.save(freshServer);
                    log.debug("Successfully reset server {} on attempt {}", freshServer.getName(), attempt);
                    return true;
                } else {
                    log.debug("Server {} does not need reset", freshServer.getName());
                    return false;
                }
            } catch (OptimisticLockException | OptimisticLockingFailureException e) {
                log.warn("Optimistic locking conflict resetting server ID {} (attempt {}/{})", serverId, attempt, SAVE_RETRY_ATTEMPTS);

                if (attempt < SAVE_RETRY_ATTEMPTS) {
                    new ThreadSleeper().sleep(50 * attempt);
                } else {
                    log.error("Optimistic locking retries exhausted for server ID {}", serverId, e);
                }
            } catch (Exception e) {
                log.warn("Failed to reset server ID {} (attempt {}/{}): {} - {}", serverId, attempt, SAVE_RETRY_ATTEMPTS, e.getClass().getSimpleName(), e.getMessage());

                if (attempt < SAVE_RETRY_ATTEMPTS) {
                    new ThreadSleeper().sleep(50 * attempt);
                } else {
                    log.error("All retry attempts exhausted for resetting server ID {}", serverId, e);
                }
            }
        }
        return null;
    }

    /**
     * Resets all Foreman-related fields of a server to their default values.
     *
     * @param server the server to reset
     * @return true if any field was changed, false otherwise
     */
    private boolean resetServerForemanFields(final Server server) {
        boolean needsUpdate = false;

        if (Boolean.TRUE.equals(server.getManaged())) {
            server.setManaged(false);
            needsUpdate = true;

            // Optimized cleanup: only delete if assignments actually exist
            if (repositoryRepository.existsAssignmentsByServerId(server.getId())) {
                repositoryRepository.deleteAssignmentsByServerId(server.getId());
                log.debug("Cleared repository assignments for unprocessed server {}", server.getName());
            }
        }
        if (server.getRoleLinux()) {
            if (server.getPatchnightTime() != null) {
                server.setPatchnightTime(null);
                needsUpdate = true;
            }
            if (server.getPatchnightGroup() != null) {
                server.setPatchnightGroup(null);
                needsUpdate = true;
            }
            if (server.getPatchnightExitstring() != null) {
                server.setPatchnightExitstring(null);
                needsUpdate = true;
            }
            if (server.getPatchnightExitcode() != null) {
                server.setPatchnightExitcode(null);
                needsUpdate = true;
            }
        }
        if (server.getForemanId() != null) {
            server.setForemanId(null);
            needsUpdate = true;
        }
        if (Boolean.TRUE.equals(server.getTetrationAgentInstalled())) {
            server.setTetrationAgentInstalled(false);
            needsUpdate = true;
        }

        // Database flags
        needsUpdate |= resetBooleanField(server::getDbOracle, server::setDbOracle);
        needsUpdate |= resetBooleanField(server::getDbMariadb, server::setDbMariadb);
        needsUpdate |= resetBooleanField(server::getDbMysql, server::setDbMysql);
        needsUpdate |= resetBooleanField(server::getDbMssql, server::setDbMssql);
        needsUpdate |= resetBooleanField(server::getDbPostgres, server::setDbPostgres);
        needsUpdate |= resetBooleanField(server::getDbMongodb, server::setDbMongodb);
        needsUpdate |= resetBooleanField(server::getDbAdabas, server::setDbAdabas);

        // Role flags
        needsUpdate |= resetBooleanField(server::getRoleWindows, server::setRoleWindows);
        needsUpdate |= resetBooleanField(server::getRoleLinux, server::setRoleLinux);
        needsUpdate |= resetBooleanField(server::getRoleOracle, server::setRoleOracle);
        needsUpdate |= resetBooleanField(server::getRoleNonOracle, server::setRoleNonOracle);

        return needsUpdate;
    }

    /**
     * Helper method to reset a boolean field to false if it's null or true.
     *
     * @param getter the getter function
     * @param setter the setter function
     * @return true if the field was changed
     */
    private boolean resetBooleanField(java.util.function.Supplier<Boolean> getter,
                                      java.util.function.Consumer<Boolean> setter) {
        final Boolean value = getter.get();
        if (value == null || value) {
            setter.accept(false);
            return true;
        }
        return false;
    }

    /**
     * Parses an exit code string to a Short value.
     *
     * @param exitcodeStr the exit code as string
     * @return the parsed exit code, or null if parsing fails
     */
    private Short parseExitcode(final String exitcodeStr) {
        return parseNumber(exitcodeStr, Short::parseShort, "exitcode");
    }

    /**
     * Generic number parsing with error handling.
     *
     * @param value     the string value to parse
     * @param parser    the parsing function
     * @param fieldName the field name for logging
     * @param <T>       the target number type
     * @return the parsed number, or null if parsing fails
     */
    private <T> T parseNumber(final String value, Function<String, T> parser, final String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid {} format: '{}'", fieldName, value);
            return null;
        }
    }

    /**
     * Calculates whether a host has a non-Oracle database role.
     *
     * @param hostDTO the host data
     * @return true if any non-Oracle database is present
     */
    private Boolean calculateNonOracleRole(final HostDTO hostDTO) {
        return hostDTO.mariaDb() ||
               hostDTO.mysqlDb() ||
               hostDTO.mssqlDb() ||
               hostDTO.postgresDb() ||
               hostDTO.mongoDb() ||
               hostDTO.adabasDb();
    }

    /**
     * Resets Foreman fields for all servers not processed in the import.
     * Each server reset is performed in its own transaction to handle concurrent updates.
     *
     * @param processedServerIDs set of server IDs that were processed in the import
     */
    public void resetUnprocessedServers(Set<Long> processedServerIDs) {
        final List<Server> allServers = serverRepository.findAll();

        final List<Server> serversToReset = allServers.stream()
                .filter(server -> !processedServerIDs.contains(server.getId()))
                .toList();

        if (serversToReset.isEmpty()) {
            log.debug("No servers need Foreman field reset");
            return;
        }

        log.info("Resetting Foreman fields for {} servers not in import", serversToReset.size());

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (final Server server : serversToReset) {
            try {
                final Boolean result = resetServerInNewTransaction(server.getId());
                if (result == null) {
                    failCount++;
                } else if (result) {
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                failCount++;
                log.error("Failed to reset Foreman fields for server {}: {}", server.getName(), e.getMessage(), e);
            }
        }

        log.info("Reset complete: {} succeeded, {} skipped (no changes needed), {} failed", successCount, skipCount, failCount);
    }
}
