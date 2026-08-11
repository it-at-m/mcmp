package de.muenchen.mcmp.clients.snow;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceRepository;
import de.muenchen.mcmp.database.DatabaseInstance;
import de.muenchen.mcmp.database.DatabaseInstanceRepository;
import de.muenchen.mcmp.database.DatabasePdbInstance;
import de.muenchen.mcmp.database.DatabasePdbInstanceRepository;
import de.muenchen.mcmp.group.Group;
import de.muenchen.mcmp.group.GroupRepository;
import de.muenchen.mcmp.kubernetes.KubernetesCluster;
import de.muenchen.mcmp.kubernetes.KubernetesClusterRepository;
import de.muenchen.mcmp.kubernetes.KubernetesNamespace;
import de.muenchen.mcmp.kubernetes.KubernetesNamespaceRepository;
import de.muenchen.mcmp.loadbalancer.LbVirtualServer;
import de.muenchen.mcmp.loadbalancer.LbVirtualServerCi;
import de.muenchen.mcmp.loadbalancer.LbVirtualServerCiRepository;
import de.muenchen.mcmp.loadbalancer.LbVirtualServerRepository;
import de.muenchen.mcmp.ontap.*;
import de.muenchen.mcmp.repository.Repository;
import de.muenchen.mcmp.repository.RepositoryRepository;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import de.muenchen.mcmp.server.matching.ServerMatcher;
import de.muenchen.mcmp.storagegrid.StorageGridAccount;
import de.muenchen.mcmp.storagegrid.StorageGridAccountRepository;
import de.muenchen.mcmp.storagegrid.StorageGridBucket;
import de.muenchen.mcmp.storagegrid.StorageGridBucketRepository;
import de.muenchen.mcmp.types.EnvironmentType;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnowService {

    private static final DateTimeFormatter SNOW_DATE_FORMATTER_CLOSED_AT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .optionalStart()
            .appendLiteral('T')
            .optionalEnd()
            .optionalStart()
            .appendLiteral(' ')
            .optionalEnd()
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .optionalStart()
            .appendPattern(".SSS")
            .optionalEnd()
            .optionalStart()
            .appendOffset("+HHMM", "Z")
            .optionalEnd()
            .optionalStart()
            .appendOffsetId()
            .optionalEnd()
            .toFormatter();
    private static final DateTimeFormatter SNOW_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CMDB_CI_WIN_SERVER = "cmdb_ci_win_server";
    private static final String CMDB_CI_LINUX_SERVER = "cmdb_ci_linux_server";
    private static final String CMDB_CI_VMWARE_INSTANCE = "cmdb_ci_vmware_instance";
    private final AppserviceRepository appserviceRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ServerRepository serverRepository;
    private final OntapSvmRepository ontapSvmRepository;
    private final OntapVolumeRepository ontapVolumeRepository;
    private final OntapQtreeRepository ontapQtreeRepository;
    private final StorageGridAccountRepository storageGridAccountRepository;
    private final StorageGridBucketRepository storageGridBucketRepository;
    private final LbVirtualServerRepository lbVirtualServerRepository;
    private final LbVirtualServerCiRepository lbVirtualServerCiRepository;
    private final KubernetesClusterRepository kubernetesClusterRepository;
    private final KubernetesNamespaceRepository kubernetesNamespaceRepository;
    private final RepositoryRepository repositoryRepository;
    private final DatabaseInstanceRepository databaseInstanceRepository;
    private final DatabasePdbInstanceRepository databasePdbInstanceRepository;
    private final SnowServerCache snowServerCache;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    @Lazy
    private SnowService self;

    @Async
    public void importAsync(final SnowDataRequestDTO data) {
        try {
            self.importServiceNowData(data);
        } catch (Exception e) {
            log.error("Exception during ServiceNow import", e);
        }
    }

    public void importServiceNowData(final SnowDataRequestDTO data) {
        try {
            self.processSnowUser(data.users());
            self.processSnowGroups(data.groups());
            self.processSnowCIs(data.cmdbCIs());
            self.processAppServices(data.appServices());
            self.processSnowStorageServers(data.storageServers());
            self.processSnowStorageVolumes(data.storageVolumes());
            self.processSnowStorageQTrees(data.storageQTrees());
            self.processSnowStorageGridAccounts(data.storageAccounts());
            self.processSnowStorageGridBuckets(data.storageBuckets());
            self.processSnowLbServices(data.lbServices());
            self.processSnowKubernetesClusters(data.kubernetesClusters());
            self.processSnowPackageRepositories(data.packageRepositories());
            self.processSnowDatabaseInstances(data.databaseInstances());
            self.processSnowDatabasePdbInstances(data.databasePdbInstances());
            log.info("Successfully completed ServiceNow data processing");
        } catch (Exception e) {
            int users = data != null && data.users() != null ? data.users().size() : 0;
            int groups = data != null && data.groups() != null ? data.groups().size() : 0;
            int cis = data != null && data.cmdbCIs() != null ? data.cmdbCIs().size() : 0;
            int apps = data != null && data.appServices() != null ? data.appServices().size() : 0;
            int storage = data != null && data.storageServers() != null ? data.storageServers().size() : 0;
            int storageVolumes = data != null && data.storageVolumes() != null ? data.storageVolumes().size() : 0;
            int storageQTrees = data != null && data.storageQTrees() != null ? data.storageQTrees().size() : 0;
            int lbServices = data != null && data.lbServices() != null ? data.lbServices().size() : 0;
            int kubernetesClusters = data != null && data.kubernetesClusters() != null ? data.kubernetesClusters().size() : 0;
            int packageRepositories = data != null && data.packageRepositories() != null ? data.packageRepositories().size() : 0;
            int databaseInstances = data != null && data.databaseInstances() != null ? data.databaseInstances().size() : 0;
            int databasePdbInstances = data != null && data.databasePdbInstances() != null ? data.databasePdbInstances().size() : 0;

            log.error("ServiceNow import failed (users={}, groups={}, cis={}, appServices={}, storageServers={}, storageVolumes={}, storageQTrees={}, lbServices={}, packageRepositories={}, databaseInstances={}, databasePdbInstances={}, kubernetesClusters={})", users, groups, cis, apps, storage, storageVolumes, storageQTrees, lbServices, packageRepositories, databaseInstances, databasePdbInstances, kubernetesClusters, e);

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ServiceNow import failed", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowUser(List<SnowDataRequestDTO.UserDTO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }

        // 1. Load all existing users and index them by username for efficient primary lookup
        final List<User> existingUsers = userRepository.findAll();
        final Map<String, User> existingUserMap = existingUsers.stream()
                .collect(Collectors.toMap(user -> user.getUsername().toLowerCase(), Function.identity()));

        // 2. Collect all usernames from the import for subsequent cleanup logic
        final Set<String> importedUsernames = users.stream()
                .map(SnowDataRequestDTO.UserDTO::userId)
                .collect(Collectors.toSet());

        // 3. Process imported users (create new or update existing records)
        for (final SnowDataRequestDTO.UserDTO userDTO : users) {
            if (userDTO.userId() == null || userDTO.userId().isBlank()) {
                continue;
            }
            User existingUser = existingUserMap.get(userDTO.userId().toLowerCase());

            // Fallback: If no match found by username, check via sys_id to handle identity stability
            // during username changes (e.g. name change due to marriage).
            if (existingUser == null && userDTO.sysId() != null && !userDTO.sysId().isBlank()) {
                existingUser = userRepository.findBySysId(userDTO.sysId());
                if (existingUser != null) {
                    log.info("Username change detected for sys_id {}: old='{}', new='{}'",
                            userDTO.sysId(), existingUser.getUsername(), userDTO.userId());
                }
            }

            if (existingUser != null) {
                // User found - check if attributes have changed and trigger update if necessary
                if (hasUserChanged(existingUser, userDTO)) {
                    final User freshUser = userRepository.findById(existingUser.getId()).orElse(null);
                    if (freshUser != null) {
                        updateExistingUser(freshUser, userDTO);
                        try {
                            userRepository.save(freshUser);
                            existingUserMap.put(freshUser.getUsername().toLowerCase(), freshUser);
                        } catch (Exception e) {
                            log.error("Exception update existingUser : username={} sysid={} department={} id={}\n", existingUser.getUsername(), existingUser.getSysId(), existingUser.getDepartment(), existingUser.getSysId(), e);
                        }
                    } else {
                        log.warn("User with sysId {} / username {} not found in DB", userDTO.sysId(), userDTO.userId());
                    }
                }
            } else {
                // Identity is unknown (neither username nor sys_id matched) - create new user
                final User newUser = createNewUser(userDTO);
                try {
                    userRepository.save(newUser);
                    existingUserMap.put(newUser.getUsername().toLowerCase(), newUser);
                } catch (Exception e) {
                    log.error("Exception save newUser : username={} sysid={} department={} id={}", newUser.getUsername(), newUser.getSysId(), newUser.getDepartment(), newUser.getSysId(), e);
                }
            }
        }

        // 4. Remove users that are no longer present in the ServiceNow import
        // Note: Repository query filters out special/admin users to prevent accidental deletion.
        List<User> usersToDelete = userRepository.findNonSpecialUsersNotInUsernames(importedUsernames);

        if (!usersToDelete.isEmpty()) {
            try {
                userRepository.deleteAll(usersToDelete);
            } catch (Exception e) {
                log.error("Exception delete usersToDelete : usernames={}\n", usersToDelete.stream().map(User::getUsername).collect(Collectors.joining(", ")), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowGroups(List<SnowDataRequestDTO.GroupDTO> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        // 1. Alle existierenden Groups aus der DB laden
        final List<Group> existingGroups = groupRepository.findAll();
        final Map<String, Group> existingGroupMap = existingGroups.stream()
                .collect(Collectors.toMap(Group::getSysId, Function.identity()));

        // 2. Alle User laden für Zuordnung - nach sysId indexiert
        final Map<String, User> userBySysIdMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getSysId, Function.identity()));

        // 3. Set der SysIds aus dem Import für später
        final Set<String> importedSysIds = groups.stream()
                .map(SnowDataRequestDTO.GroupDTO::sysId)
                .collect(Collectors.toSet());

        // 4. Groups aus Import verarbeiten (erstellen oder aktualisieren)
        for (final SnowDataRequestDTO.GroupDTO groupDTO : groups) {
            final Group existingGroup = existingGroupMap.get(groupDTO.sysId());

            if (existingGroup != null) {
                // Group existiert - prüfen ob Update nötig ist
                if (hasGroupChanged(existingGroup, groupDTO, userBySysIdMap)) {
                    final Group freshGroup = groupRepository.findById(existingGroup.getId()).orElse(null);
                    if (freshGroup != null) {
                        updateExistingGroup(freshGroup, groupDTO, userBySysIdMap);
                        try {
                            groupRepository.save(freshGroup);
                            existingGroupMap.put(freshGroup.getSysId(), freshGroup);
                        } catch (Exception e) {
                            log.error("Exception update existingGroup : sysid={} name={} id={}\n", freshGroup.getSysId(), freshGroup.getName(), freshGroup.getId(), e);
                        }
                    } else {
                        log.warn("Group with sysId {} not found in DB", groupDTO.sysId());
                    }
                }
            } else {
                // Neue Group erstellen
                final Group newGroup = createNewGroup(groupDTO, userBySysIdMap);
                try {
                    groupRepository.save(newGroup);
                    existingGroupMap.put(newGroup.getSysId(), newGroup);
                } catch (Exception e) {
                    log.error("Exception save newGroup : sysid={} name={} id={}\n", newGroup.getSysId(), newGroup.getName(), newGroup.getId(), e);
                }
            }
        }

        // 5. Groups löschen, die nicht im Import vorhanden sind
        List<Group> groupsToDelete = groupRepository.findGroupsNotInSysIds(importedSysIds);
        if (!groupsToDelete.isEmpty()) {
            try {
                groupRepository.deleteAll(groupsToDelete);
            } catch (Exception e) {
                log.error("Exception delete groupsToDelete : sysids={}\n", groupsToDelete.stream().map(Group::getSysId).collect(Collectors.joining(", ")), e);
            }
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    protected void processSnowCIs(List<SnowDataRequestDTO.CIDTO> cmdbCIs) {
        if (cmdbCIs == null) {
            cmdbCIs = Collections.emptyList();
        }

        int updatedServers = 0;
        int processedCis = cmdbCIs.size();
        final int BATCH_SIZE = 50;

        // Maps für CI-Daten erstellen für O(1) Lookups
        Map<Long, SnowDataRequestDTO.CIDTO> uuidSnowServerMap = new HashMap<>();
        Map<Long, SnowDataRequestDTO.CIDTO> uuidSnowInstanceMap = new HashMap<>();
        Map<String, SnowDataRequestDTO.CIDTO> serverSysIdSnowServerMap = new HashMap<>();

        try (SnowServerCache cache = snowServerCache.loadForResource()) {
            log.info("ServiceNow server cache loaded for CI matching");

            List<ServerMatcher<SnowDataRequestDTO.CIDTO>> snowServerMatchingStrategies = List.of(
                    cache.createServerSysIdMatcher(SnowDataRequestDTO.CIDTO::sysId),
                    cache.createSerialNumberMatcher(SnowDataRequestDTO.CIDTO::serialNumber),
                    cache.createMacAddressMatcher(SnowDataRequestDTO.CIDTO::macAddress)
                    //cache.createIpAddressMatcher(SnowDataRequestDTO.CIDTO::ipAddress)
            );

            // Instance-matching strategies
            List<ServerMatcher<SnowDataRequestDTO.CIDTO>> snowInstanceMatchingStrategies = List.of(
                    cache.createInstanceSysIdMatcher(SnowDataRequestDTO.CIDTO::sysId),
                    cache.createInstanceUuidMatcher(SnowDataRequestDTO.CIDTO::vmInstanceUUID)
                    //cache.createIpAddressMatcher(SnowDataRequestDTO.CIDTO::ipAddress)
            );

            log.debug("Initialized {} server and {} instance matching strategies",
                    snowServerMatchingStrategies.size(), snowInstanceMatchingStrategies.size());

            // CI-Verarbeitung und Mapping erstellen
            for (final SnowDataRequestDTO.CIDTO ciDto : cmdbCIs) {
                if (CMDB_CI_WIN_SERVER.equals(ciDto.sysClassName()) || CMDB_CI_LINUX_SERVER.equals(ciDto.sysClassName())) {
                    final Server server = matchCI(ciDto, snowServerMatchingStrategies);
                    if (server != null) {
                        uuidSnowServerMap.put(server.getId(), ciDto);
                    }
                    serverSysIdSnowServerMap.put(ciDto.sysId(), ciDto);
                } else if (CMDB_CI_VMWARE_INSTANCE.equals(ciDto.sysClassName())) {
                    if (ciDto.vmInstanceUUID() != null && !ciDto.vmInstanceUUID().isBlank()) {
                        final Server server = matchCI(ciDto, snowInstanceMatchingStrategies);
                        if (server != null) {
                            uuidSnowInstanceMap.put(server.getId(), ciDto);
                        }
                    }
                } else {
                    log.debug("Ignoring CI with sysClassName: {}", ciDto.sysClassName());
                }
            }
        }
        final List<Server> allServers = serverRepository.findAll();
        final List<Server> serversToUpdate = new ArrayList<>();

        for (final Server server : allServers) {
            SnowDataRequestDTO.CIDTO serverCI = null;
            SnowDataRequestDTO.CIDTO vmwareCI = null;
            if (server.getUuid() != null) {
                serverCI = uuidSnowServerMap.get(server.getId());
            }
            if (server.getInstanceUuid() != null) {
                vmwareCI = uuidSnowInstanceMap.get(server.getId());
                if (serverCI == null && vmwareCI != null && vmwareCI.serverSysId() != null && !vmwareCI.serverSysId().isBlank()) {
                    serverCI =  serverSysIdSnowServerMap.get(vmwareCI.serverSysId());
                }
            }

            // Änderungen prüfen (inkl. Cleanup wenn CI nicht mehr vorhanden)
            if (hasServerSnowFieldsChanges(server, serverCI, vmwareCI)) {
                updateServerSnowFields(server, serverCI, vmwareCI);
                serversToUpdate.add(server);

                log.debug("Prepared server Snow data update for UUID: {} (serverCI: {}, vmwareCI: {})",
                        server.getUuid(), serverCI != null, vmwareCI != null);

                // Batch-Update durchführen wenn Batch-Größe erreicht
                if (serversToUpdate.size() >= BATCH_SIZE) {
                    try {
                        updatedServers += self.processBatch(serversToUpdate);
                    } catch (Exception e) {
                        log.warn("Batch failed, falling back to individual updates", e);
                        updatedServers += self.processBatchIndividually(serversToUpdate);
                    }
                    serversToUpdate.clear();
                }
            }
        }

        // Letzte Batch verarbeiten
        if (!serversToUpdate.isEmpty()) {
            try {
                updatedServers += self.processBatch(serversToUpdate);
            } catch (Exception e) {
                log.warn("Last batch failed, falling back to individual updates", e);
                updatedServers += self.processBatchIndividually(serversToUpdate);
            }
        }

        log.info("CI processing completed: processed {} CIs, updated {} servers", processedCis, updatedServers);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int processBatch(List<Server> serversToUpdate) {
        // Aktuelle Versionen aus DB laden (Race Condition vermeiden)
        final List<Long> serverIds = serversToUpdate.stream()
                .map(Server::getId)
                .toList();
        final Map<Long, Server> currentServers = serverRepository.findAllById(serverIds)
                .stream()
                .collect(Collectors.toMap(Server::getId, Function.identity()));
        final List<Server> validServersToUpdate = new ArrayList<>();

        for (final Server serverToUpdate : serversToUpdate) {
            final Server currentServer = currentServers.get(serverToUpdate.getId());
            if (currentServer != null) {
                // Snow-Felder vom zu aktualisierenden Server auf den aktuellen Server übertragen
                copySnowFields(serverToUpdate, currentServer);
                validServersToUpdate.add(currentServer);
            } else {
                log.warn("Server with ID {} was deleted during processing", serverToUpdate.getId());
            }
        }
        if (!validServersToUpdate.isEmpty()) {
            serverRepository.saveAll(validServersToUpdate);
            serverRepository.flush(); // Force immediate flush within transaction
            log.debug("Batch updated {} servers", validServersToUpdate.size());
        }
        return validServersToUpdate.size();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    protected int processBatchIndividually(List<Server> serversToUpdate) {
        int successCount = 0;
        for (final Server serverToUpdate : serversToUpdate) {
            try {
                successCount += self.processSingleServer(serverToUpdate);
            } catch (Exception e) {
                log.error("Exception saving individual server with ID: {}", serverToUpdate.getId(), e);
            }
        }
        return successCount;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int processSingleServer(Server serverToUpdate) {
        final Server currentServer = serverRepository.findById(serverToUpdate.getId()).orElse(null);
        if (currentServer != null) {
            copySnowFields(serverToUpdate, currentServer);
            serverRepository.save(currentServer);
            log.debug("Individual update for server UUID: {}", currentServer.getUuid());
            return 1;
        } else {
            log.warn("Server with ID {} was deleted during processing", serverToUpdate.getId());
            return 0;
        }
    }

    private void copySnowFields(Server source, Server target) {
        // Snow Server Felder
        target.setSnowServerName(source.getSnowServerName());
        target.setSnowServerSysId(source.getSnowServerSysId());
        target.setSnowServerSysClass(source.getSnowServerSysClass());
        target.setSnowServerHardwareStatus(source.getSnowServerHardwareStatus());
        target.setSnowServerLastDiscovered(source.getSnowServerLastDiscovered());

        // Snow Instance Felder
        target.setSnowInstanceName(source.getSnowInstanceName());
        target.setSnowInstanceSysId(source.getSnowInstanceSysId());
        target.setSnowInstanceSysClass(source.getSnowInstanceSysClass());
        target.setSnowInstanceLastDiscovered(source.getSnowInstanceLastDiscovered());

        // GreenIT Felder (werden von updateServerSnowInstanceFields gesetzt)
        target.setGreenItShutdownChangeRejectedDate(source.getGreenItShutdownChangeRejectedDate());
        target.setGreenItRightsizingChangeRejectedDate(source.getGreenItRightsizingChangeRejectedDate());
        target.setGreenItShutdownChangePending(source.getGreenItShutdownChangePending());
        target.setGreenItRightsizingChangePending(source.getGreenItRightsizingChangePending());
    }

    @Nullable
    private Server matchCI(SnowDataRequestDTO.CIDTO ciDto, List<ServerMatcher<SnowDataRequestDTO.CIDTO>> strategies) {
        return strategies.stream()
                .map(strategy -> strategy.match(ciDto))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private <T> T extractOrNull(final SnowDataRequestDTO.CIDTO ci, final Function<SnowDataRequestDTO.CIDTO, T> extractor) {
        return ci != null ? extractor.apply(ci) : null;
    }

    private boolean hasServerSnowFieldsChanges(final Server server, final SnowDataRequestDTO.CIDTO serverCI, final SnowDataRequestDTO.CIDTO vmwareCI) {
        return hasServerSnowServerFieldsChanges(server, serverCI) || hasServerSnowInstanceFieldsChanges(server, vmwareCI);
    }

    private boolean hasServerSnowServerFieldsChanges(final Server server, final SnowDataRequestDTO.CIDTO serverCI) {
        return !Objects.equals(server.getSnowServerName(), extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::name)) ||
                !Objects.equals(server.getSnowServerSysId(), extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::sysId)) ||
                !Objects.equals(server.getSnowServerSysClass(), extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::sysClassName)) ||
                !Objects.equals(server.getSnowServerHardwareStatus(), extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::hardwareStatus)) ||
                !Objects.equals(server.getSnowServerLastDiscovered(), parseSnowDateTime(extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::lastDiscovered)));
    }

    private boolean hasServerSnowInstanceFieldsChanges(final Server server, final SnowDataRequestDTO.CIDTO vmwareCI) {
        return !Objects.equals(server.getSnowInstanceName(), extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::name)) ||
                !Objects.equals(server.getSnowInstanceSysId(), extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::sysId)) ||
                !Objects.equals(server.getSnowInstanceSysClass(), extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::sysClassName)) ||
                !Objects.equals(server.getSnowInstanceLastDiscovered(), parseSnowDateTime(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::lastDiscovered))) ||
                !Objects.equals(server.getGreenItShutdownChangeRejectedDate(), parseSnowDateTimeClosedAt(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::shutdownTaskClosedAt))) ||
                !Objects.equals(server.getGreenItRightsizingChangeRejectedDate(), parseSnowDateTimeClosedAt(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::rightsizeTaskClosedAt))) ||
                !Objects.equals(server.getGreenItShutdownChangePending(), Objects.requireNonNullElse(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::lockedShutdown), false)) ||
                !Objects.equals(server.getGreenItRightsizingChangePending(), Objects.requireNonNullElse(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::lockedRightsize), false));
    }


    private void updateServerSnowFields(final Server server, final SnowDataRequestDTO.CIDTO serverCI, final SnowDataRequestDTO.CIDTO vmwareCI) {
        updateServerSnowServerFields(server, serverCI);
        updateServerSnowInstanceFields(server, vmwareCI);
    }

    private void updateServerSnowServerFields(final Server server, final SnowDataRequestDTO.CIDTO serverCI) {
        server.setSnowServerName(extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::name));
        server.setSnowServerSysId(extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::sysId));
        server.setSnowServerSysClass(extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::sysClassName));
        server.setSnowServerHardwareStatus(extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::hardwareStatus));
        server.setSnowServerLastDiscovered(parseSnowDateTime(extractOrNull(serverCI, SnowDataRequestDTO.CIDTO::lastDiscovered)));
    }

    private void updateServerSnowInstanceFields(final Server server, final SnowDataRequestDTO.CIDTO vmwareCI) {
        server.setSnowInstanceName(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::name));
        server.setSnowInstanceSysId(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::sysId));
        server.setSnowInstanceSysClass(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::sysClassName));
        server.setSnowInstanceLastDiscovered(parseSnowDateTime(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::lastDiscovered)));
        server.setGreenItShutdownChangeRejectedDate(parseSnowDateTimeClosedAt(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::shutdownTaskClosedAt)));
        server.setGreenItRightsizingChangeRejectedDate(parseSnowDateTimeClosedAt(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::rightsizeTaskClosedAt)));
        server.setGreenItShutdownChangePending(Objects.requireNonNullElse(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::lockedShutdown), false));
        server.setGreenItRightsizingChangePending(Objects.requireNonNullElse(extractOrNull(vmwareCI, SnowDataRequestDTO.CIDTO::lockedRightsize), false));
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processAppServices(final List<SnowDataRequestDTO.AppServiceDTO> appServices) {
        if (appServices == null || appServices.isEmpty()) {
            return;
        }

        // 1. Alle bestehenden App Services in Map laden (key = sysId)
        final List<Appservice> existingAppServices = appserviceRepository.findAll();
        final Map<String, Appservice> existingAppServicesMap = existingAppServices.stream()
                .collect(Collectors.toMap(Appservice::getSysId, Function.identity()));

        // Alle User laden für Zuordnung - nach sysId indexiert
        final Map<String, User> userBySysIdMap = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getSysId, Function.identity()));

        // Alle existierenden Groups aus der DB laden
        final Map<String, Group> groupsByGroupMap = groupRepository.findAll().stream()
                .collect(Collectors.toMap(Group::getSysId, Function.identity()));

        // Alle Server aus der DB laden
        final List<Server> allServers = serverRepository.findAll();
        final Map<String, Server> serversBySnowSysIdMap = new HashMap<>();
        for (Server server : allServers) {
            if (server.getSnowServerSysId() != null) {
                serversBySnowSysIdMap.put(server.getSnowServerSysId(), server);
            }
            if (server.getSnowInstanceSysId() != null) {
                serversBySnowSysIdMap.put(server.getSnowInstanceSysId(), server);
            }
        }

        // Set der sysIds aus dem DTO für später
        final Set<String> importedSysIds = appServices.stream()
                .map(SnowDataRequestDTO.AppServiceDTO::sysId)
                .collect(Collectors.toSet());

        // 2. App Services aus DTO verarbeiten
        for (final SnowDataRequestDTO.AppServiceDTO appService : appServices) {
            if (appService == null || appService.sysId() == null || appService.sysId().isBlank() || appService.environment() == null || appService.environment().isBlank()) {
                log.warn("Ignoring invalid AppService: {}", appService);
                continue;
            }
            Appservice existingAppService = existingAppServicesMap.get(appService.sysId());

            if (existingAppService != null) {
                // App Service existiert bereits - prüfen ob Update nötig
                if (hasAppServiceChanged(existingAppService, appService, serversBySnowSysIdMap)) {
                    existingAppService = appserviceRepository.findById(existingAppService.getId()).orElse(null);
                    if (existingAppService != null) {
                        updateAppService(existingAppService, appService, userBySysIdMap, groupsByGroupMap, serversBySnowSysIdMap);
                        try {
                            appserviceRepository.save(existingAppService);
                        } catch (EntityNotFoundException | DataIntegrityViolationException e) {
                            // Server existiert nicht mehr - aus Appservice entfernen (mit Schutzzeit)
                            removeInvalidServersWithProtection(existingAppService);
                            appserviceRepository.save(existingAppService);
                        } catch (Exception e) {
                            log.error("Error saving AppService: {}", appService, e);
                        }
                        log.debug("Updated AppService: {}", appService);
                    } else {
                        log.warn("AppService with sysId {} not found in DB", appService.sysId());
                    }
                } else {
                    log.debug("Ignoring AppService {}: No change detected", appService.sysId());
                }
            } else {
                // Neuen App Service erstellen
                final Appservice newAppService = createAppService(appService, userBySysIdMap, groupsByGroupMap, serversBySnowSysIdMap);
                try {
                    appserviceRepository.save(newAppService);
                } catch (EntityNotFoundException | DataIntegrityViolationException e) {
                    // Server existiert nicht mehr - aus Appservice entfernen (mit Schutzzeit)
                    removeInvalidServersWithProtection(newAppService);
                    appserviceRepository.save(newAppService);
                } catch (Exception e) {
                    log.error("Error saving AppService: {}", appService, e);
                }
                log.debug("Created new AppService: {}", appService);
            }
        }

        // 3. App Services löschen, die nicht mehr im DTO enthalten sind
        final List<Appservice> appServicesToDelete = existingAppServices.stream()
                .filter(appService -> !importedSysIds.contains(appService.getSysId()))
                .toList();
        appserviceRepository.deleteAll(appServicesToDelete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowStorageServers(List<SnowDataRequestDTO.StorageServerDTO> storageServers) {
        if (storageServers == null) {
            storageServers = Collections.emptyList();
        }

        final List<OntapSvm> allSvms = ontapSvmRepository.findAll();
        final Map<UUID, OntapSvm> svmMap = allSvms.stream().collect(Collectors.toMap(OntapSvm::getSwmUuid, Function.identity()));
        final Set<UUID> importedUuids = new HashSet<>();

        for (final SnowDataRequestDTO.StorageServerDTO dto : storageServers) {
            if (dto.serialNumber() == null || dto.serialNumber().isBlank()) {
                continue;
            }

            try {
                final UUID swmUuid = UUID.fromString(dto.serialNumber());
                importedUuids.add(swmUuid);
                final OntapSvm svm = svmMap.get(swmUuid);

                if (svm != null) {
                    final OffsetDateTime lastDiscovered = parseSnowDateTime(dto.lastDiscovered());

                    if (!Objects.equals(svm.getSnowName(), dto.name()) ||
                            !Objects.equals(svm.getSnowSysId(), dto.sysId()) ||
                            !Objects.equals(svm.getSnowSysClass(), dto.sysClass()) ||
                            isTimeChanged(svm.getSnowLastDiscovered(), lastDiscovered)) {

                        ontapSvmRepository.updateSnowFields(svm.getId(), dto.name(), dto.sysId(), dto.sysClass(), lastDiscovered);
                        log.debug("Updated Snow fields for OntapSvm: {}", swmUuid);
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in storage server serial_number: {}", dto.serialNumber());
            }
        }

        for (final OntapSvm svm : allSvms) {
            if (!importedUuids.contains(svm.getSwmUuid())) {
                if (svm.getSnowSysId() != null) {
                    ontapSvmRepository.updateSnowFields(svm.getId(), null, null, null, null);
                    log.debug("Cleared Snow fields for OntapSvm (not in import): {}", svm.getSwmUuid());
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowStorageVolumes(List<SnowDataRequestDTO.StorageVolumeDTO> storageVolumes) {
        if (storageVolumes == null || storageVolumes.isEmpty()) {
            return;
        }

        final List<OntapVolume> allVolumes = ontapVolumeRepository.findAllWithAppservices();
        final Map<UUID, OntapVolume> volumeMap = allVolumes.stream()
                .collect(Collectors.toMap(OntapVolume::getVolumeUuid, Function.identity()));

        final Map<Long, Set<String>> existingAssociations = allVolumes.stream()
                .collect(Collectors.toMap(
                        OntapVolume::getId,
                        v -> v.getAppservices().stream()
                                .map(Appservice::getNumber)
                                .collect(Collectors.toSet())
                ));

        final Set<UUID> importedUuids = new HashSet<>();

        for (final SnowDataRequestDTO.StorageVolumeDTO dto : storageVolumes) {
            if (dto.volumeId() == null || dto.volumeId().isBlank()) {
                continue;
            }

            try {
                final UUID volumeUuid = UUID.fromString(dto.volumeId());
                importedUuids.add(volumeUuid);
                final OntapVolume volume = volumeMap.get(volumeUuid);

                if (volume != null) {
                    boolean svmMatches = volume.getSvm() != null &&
                            (Objects.equals(volume.getSvm().getSnowSysId(), dto.svmSysId()) ||
                                    Objects.equals(volume.getSvm().getSwmUuid().toString(), dto.svmUUID()));

                    if (svmMatches) {
                        final OffsetDateTime lastDiscovered = parseSnowDateTime(dto.lastDiscovered());

                        boolean nameChanged = !Objects.equals(volume.getSnowName(), dto.name());
                        boolean sysIdChanged = !Objects.equals(volume.getSnowSysId(), dto.sysId());
                        boolean sysClassChanged = !Objects.equals(volume.getSnowSysClass(), dto.sysClass());
                        boolean timeChanged = isTimeChanged(volume.getSnowLastDiscovered(), lastDiscovered);

                        if (nameChanged || sysIdChanged || sysClassChanged || timeChanged) {
                            if (log.isDebugEnabled()) {
                                log.debug("Update trigger for OntapVolume {}: nameChanged={}, sysIdChanged={}, sysClassChanged={}, timeChanged={}",
                                        volumeUuid, nameChanged, sysIdChanged, sysClassChanged, timeChanged);
                                if (nameChanged) log.debug("  Name: DB='{}' vs DTO='{}'", volume.getSnowName(), dto.name());
                                if (sysIdChanged) log.debug("  SysId: DB='{}' vs DTO='{}'", volume.getSnowSysId(), dto.sysId());
                                if (sysClassChanged) log.debug("  SysClass: DB='{}' vs DTO='{}'", volume.getSnowSysClass(), dto.sysClass());
                                if (timeChanged) log.debug("  Time: DB='{}' vs DTO='{}'", volume.getSnowLastDiscovered(), lastDiscovered);
                            }

                            ontapVolumeRepository.updateSnowFields(volume.getId(), dto.name(), dto.sysId(), dto.sysClass(), lastDiscovered);
                            log.debug("Updated Snow fields for OntapVolume: {}", volumeUuid);
                        }


                        updateVolumeAppServiceAssociations(volume.getId(), dto.appServiceNumber(), existingAssociations.get(volume.getId()));
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format in storage volume volume_id: {}", dto.volumeId());
            }
        }

        for (final OntapVolume volume : allVolumes) {
            if (!importedUuids.contains(volume.getVolumeUuid())) {
                if (volume.getSnowSysId() != null || volume.getSnowName() != null) {
                    ontapVolumeRepository.updateSnowFields(volume.getId(), null, null, null, null);
                    ontapVolumeRepository.deleteAppServiceAssociations(volume.getId());
                    log.debug("Cleared Snow fields and associations for OntapVolume (not in import): {}", volume.getVolumeUuid());
                }
            }
        }
    }

    private void updateVolumeAppServiceAssociations(Long volumeId, List<String> incomingNumbers, Set<String> currentNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = currentNumbers != null ? currentNumbers : Collections.emptySet();

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                ontapVolumeRepository.deleteAppServiceAssociations(volumeId);
            } else {
                ontapVolumeRepository.deleteObsoleteAppServiceAssociations(volumeId, cleanIncoming);
                ontapVolumeRepository.addAppServiceAssociations(volumeId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for volume ID {}: {} incoming numbers", volumeId, cleanIncoming.size());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowStorageQTrees(List<SnowDataRequestDTO.StorageQTreeDTO> storageQTrees) {
        if (storageQTrees == null || storageQTrees.isEmpty()) {
            return;
        }

        final List<OntapQtree> allQtrees = ontapQtreeRepository.findAllWithAppservices();

        // Key: volumeUuid + ":" + qtreeId
        final Map<String, OntapQtree> qtreeMap = allQtrees.stream()
                .filter(q -> q.getVolume() != null && q.getVolume().getVolumeUuid() != null && q.getQtreeId() != null)
                .collect(Collectors.toMap(
                        q -> q.getVolume().getVolumeUuid().toString() + ":" + q.getQtreeId(),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        final Map<Long, Set<String>> existingAssociations = allQtrees.stream()
                .collect(Collectors.toMap(
                        OntapQtree::getId,
                        q -> q.getAppservices().stream()
                                .map(Appservice::getNumber)
                                .collect(Collectors.toSet())
                ));

        final Set<String> importedKeys = new HashSet<>();

        for (final SnowDataRequestDTO.StorageQTreeDTO dto : storageQTrees) {
            if (dto.volumeId() == null || dto.volumeId().isBlank() || dto.qtreeId() == null || dto.qtreeId().isBlank()) {
                continue;
            }

            final String key = dto.volumeId().toLowerCase() + ":" + dto.qtreeId();
            importedKeys.add(key);
            final OntapQtree qtree = qtreeMap.get(key);

            if (qtree != null) {
                boolean svmMatches = qtree.getVolume() != null && qtree.getVolume().getSvm() != null &&
                        (Objects.equals(qtree.getVolume().getSvm().getSnowSysId(), dto.svmSysId()) ||
                                Objects.equals(qtree.getVolume().getSvm().getSwmUuid().toString(), dto.svmUUID()));

                if (svmMatches) {
                    final OffsetDateTime lastDiscovered = parseSnowDateTime(dto.lastDiscovered());

                    if (!Objects.equals(qtree.getSnowName(), dto.name()) ||
                            !Objects.equals(qtree.getSnowSysId(), dto.sysId()) ||
                            !Objects.equals(qtree.getSnowSysClass(), dto.sysClass()) ||
                            isTimeChanged(qtree.getSnowLastDiscovered(), lastDiscovered)) {

                        ontapQtreeRepository.updateSnowFields(qtree.getId(), dto.name(), dto.sysId(), dto.sysClass(), lastDiscovered);
                        log.debug("Updated Snow fields for OntapQtree: volume={}, qtreeId={}", dto.volumeId(), dto.qtreeId());
                    }

                    updateQtreeAppServiceAssociations(qtree.getId(), dto.appServiceNumber(), existingAssociations.get(qtree.getId()));
                }
            }
        }

        for (final OntapQtree qtree : allQtrees) {
            String qtreeKey = qtree.getVolume().getVolumeUuid().toString() + ":" + qtree.getQtreeId();
            if (!importedKeys.contains(qtreeKey)) {
                if (qtree.getSnowSysId() != null || qtree.getSnowName() != null) {
                    ontapQtreeRepository.updateSnowFields(qtree.getId(), null, null, null, null);
                    ontapQtreeRepository.deleteAppServiceAssociations(qtree.getId());
                    log.debug("Cleared Snow fields and associations for OntapQtree (not in import): {}", qtreeKey);
                }
            }
        }
    }

    private void updateQtreeAppServiceAssociations(Long qtreeId, List<String> incomingNumbers, Set<String> currentNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = currentNumbers != null ? currentNumbers : Collections.emptySet();

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                ontapQtreeRepository.deleteAppServiceAssociations(qtreeId);
            } else {
                ontapQtreeRepository.deleteObsoleteAppServiceAssociations(qtreeId, cleanIncoming);
                ontapQtreeRepository.addAppServiceAssociations(qtreeId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for qtree ID {}: {} incoming numbers", qtreeId, cleanIncoming.size());
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowStorageGridAccounts(List<SnowDataRequestDTO.StorageAccountDTO> storageAccounts) {
        if (storageAccounts == null || storageAccounts.isEmpty()) {
            return;
        }

        final List<StorageGridAccount> allAccounts = storageGridAccountRepository.findAllWithAppservices();
        final Map<String, StorageGridAccount> accountMap = allAccounts.stream()
                .collect(Collectors.toMap(StorageGridAccount::getAccountId, Function.identity()));

        final Map<Long, Set<String>> existingAssociations = allAccounts.stream()
                .collect(Collectors.toMap(
                        StorageGridAccount::getId,
                        a -> a.getAppservices().stream()
                                .map(Appservice::getNumber)
                                .collect(Collectors.toSet())
                ));

        final Set<String> importedAccountIds = new HashSet<>();

        for (final SnowDataRequestDTO.StorageAccountDTO dto : storageAccounts) {
            if (dto.accountId() == null) {
                continue;
            }
            final String accountId = dto.accountId().trim();
            if (accountId.isBlank()) {
                continue;
            }

            importedAccountIds.add(accountId);
            final StorageGridAccount account = accountMap.get(accountId);

            if (account != null) {
                // Snow Felder aktualisieren
                if (!Objects.equals(account.getSnowName(), dto.name()) ||
                        !Objects.equals(account.getSnowSysId(), dto.sysId()) ||
                        !Objects.equals(account.getSnowSysClass(), dto.sysClass())) {

                    storageGridAccountRepository.updateSnowFields(
                            account.getId(),
                            dto.name(),
                            dto.sysId(),
                            dto.sysClass()
                    );
                    log.debug("Updated Snow fields for StorageGridAccount: {}", dto.accountId());
                }

                updateAccountAppServiceAssociations(account.getId(), dto.appServiceNumber(), existingAssociations.get(account.getId()));
            }
        }

        for (final StorageGridAccount account : allAccounts) {
            if (!importedAccountIds.contains(account.getAccountId())) {
                if (account.getSnowSysId() != null || account.getSnowName() != null || account.getSnowSysClass() != null) {
                    storageGridAccountRepository.updateSnowFields(account.getId(), null, null, null);
                    storageGridAccountRepository.deleteAppServiceAssociations(account.getId());
                    log.debug("Cleared Snow fields and associations for StorageGridAccount (not in import): {}", account.getAccountId());
                }
            }
        }
    }

    private void updateAccountAppServiceAssociations(Long accountId, List<String> incomingNumbers, Set<String> currentNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = currentNumbers != null ? currentNumbers : Collections.emptySet();

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                storageGridAccountRepository.deleteAppServiceAssociations(accountId);
            } else {
                storageGridAccountRepository.deleteObsoleteAppServiceAssociations(accountId, cleanIncoming);
                storageGridAccountRepository.addAppServiceAssociations(accountId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for account ID {}: {} incoming numbers", accountId, cleanIncoming.size());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowStorageGridBuckets(List<SnowDataRequestDTO.StorageBucketDTO> storageBuckets) {
        if (storageBuckets == null || storageBuckets.isEmpty()) {
            return;
        }

        // 1. Load all accounts to map String accountId to technical Long ID
        final Map<String, Long> accountIdToTechnicalId = storageGridAccountRepository.findAll().stream()
                .collect(Collectors.toMap(StorageGridAccount::getAccountId, StorageGridAccount::getId));

        // 2. Load all buckets including AppServices and Accounts to avoid N+1 selects
        final List<StorageGridBucket> allBuckets = storageGridBucketRepository.findAllBuckets();

        // Group buckets by account ID for efficient lookup
        final Map<Long, Map<String, StorageGridBucket>> bucketsByAccount = allBuckets.stream()
                .filter(b -> b.getStorageGridAccount() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getStorageGridAccount().getId(),
                        Collectors.toMap(StorageGridBucket::getName, Function.identity())
                ));

        // Extract existing AppService numbers per bucket ID for in-memory comparison
        final Map<Long, Set<String>> existingAssociations = allBuckets.stream()
                .collect(Collectors.toMap(
                        StorageGridBucket::getId,
                        b -> b.getAppservices().stream()
                                .map(Appservice::getNumber)
                                .collect(Collectors.toSet())
                ));

        final Set<Long> importedBucketIds = new HashSet<>();

        // 3. Process import data
        for (final SnowDataRequestDTO.StorageBucketDTO dto : storageBuckets) {
            if (dto.accountId() == null || dto.name() == null) {
                continue;
            }

            final Long technicalAccountId = accountIdToTechnicalId.get(dto.accountId().trim());
            if (technicalAccountId == null) {
                log.warn("StorageGridAccount with accountId '{}' not found for bucket '{}'", dto.accountId(), dto.name());
                continue;
            }

            final Map<String, StorageGridBucket> accountBuckets = bucketsByAccount.getOrDefault(technicalAccountId, Collections.emptyMap());
            final StorageGridBucket bucket = accountBuckets.get(dto.name().trim());

            if (bucket != null) {
                importedBucketIds.add(bucket.getId());

                // Update Snow fields if necessary
                if (!Objects.equals(bucket.getSnowName(), dto.name()) ||
                        !Objects.equals(bucket.getSnowSysId(), dto.sysId()) ||
                        !Objects.equals(bucket.getSnowSysClass(), dto.sysClass())) {

                    storageGridBucketRepository.updateSnowFields(
                            bucket.getId(),
                            dto.name(),
                            dto.sysId(),
                            dto.sysClass()
                    );
                    log.debug("Updated Snow fields for StorageGridBucket: {}", dto.name());
                }

                // Synchronize AppService associations only if changed
                updateBucketAppServiceAssociations(bucket.getId(), dto.appServiceNumber(), existingAssociations.get(bucket.getId()));
            }
        }

        // 4. Cleanup: Reset attributes and delete associations if not present in import
        for (final StorageGridBucket bucket : allBuckets) {
            if (!importedBucketIds.contains(bucket.getId())) {
                if (bucket.getSnowSysId() != null || bucket.getSnowName() != null || bucket.getSnowSysClass() != null) {
                    storageGridBucketRepository.updateSnowFields(bucket.getId(), null, null, null);
                    storageGridBucketRepository.deleteAppServiceAssociations(bucket.getId());
                    log.debug("Cleared Snow fields and associations for StorageGridBucket: {}", bucket.getName());
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowLbServices(List<SnowDataRequestDTO.LbServiceDTO> lbServices) {
        if (lbServices == null || lbServices.isEmpty()) {
            return;
        }

        final List<LbVirtualServerCi> allCis = lbVirtualServerCiRepository.findAllWithAppservices();
        final Map<String, LbVirtualServerCi> ciMap = allCis.stream()
                .collect(Collectors.toMap(LbVirtualServerCi::getSnowSysId, Function.identity()));

        final Map<Long, Set<String>> existingCiAssociations = allCis.stream()
                .collect(Collectors.toMap(
                        LbVirtualServerCi::getId,
                        ci -> ci.getAppservices().stream()
                                .map(Appservice::getNumber)
                                .collect(Collectors.toSet())
                ));

        final List<LbVirtualServer> allVs = lbVirtualServerRepository.findAll();
        final Set<String> importedSysIds = new HashSet<>();
        final Map<Long, Set<String>> vsToAppServices = new HashMap<>();

        for (final SnowDataRequestDTO.LbServiceDTO dto : lbServices) {
            if (dto.sysId() == null || dto.name() == null) {
                continue;
            }

            importedSysIds.add(dto.sysId());
            LbVirtualServerCi ci = ciMap.get(dto.sysId());
            final OffsetDateTime lastDiscovered = parseSnowDateTime(dto.lastDiscovered());

            final String prefixSearch = stripVsSuffix(dto.name());
            final Optional<LbVirtualServer> vs = allVs.stream()
                    .filter(v -> v.getName().startsWith(prefixSearch))
                    .findFirst();

            if (vs.isPresent()) {
                if (ci == null) {
                    ci = new LbVirtualServerCi();
                    ci.setSnowSysId(dto.sysId());
                    ci.setLbVirtualServer(vs.get());
                }

                if (!Objects.equals(ci.getSnowName(), dto.name()) ||
                        !Objects.equals(ci.getSnowSysClass(), dto.sysClass()) ||
                        !Objects.equals(ci.getLbVirtualServer().getId(), vs.get().getId()) ||
                        isTimeChanged(ci.getSnowLastDiscovered(), lastDiscovered)) {

                    ci.setSnowName(dto.name());
                    ci.setSnowSysClass(dto.sysClass());
                    ci.setSnowLastDiscovered(lastDiscovered);
                    ci.setLbVirtualServer(vs.get());
                    lbVirtualServerCiRepository.save(ci);
                    log.debug("Updated LbVirtualServerCi: {} (assigned to VS: {})", dto.name(), vs.get().getName());
                }

                updateLbCiAppServiceAssociations(ci.getId(), dto.appServiceNumber(), existingCiAssociations.get(ci.getId()));

                // Collect AppServices for the LbVirtualServer
                if (dto.appServiceNumber() != null) {
                    vsToAppServices.computeIfAbsent(vs.get().getId(), k -> new HashSet<>()).addAll(dto.appServiceNumber());
                }
            } else {
                log.warn("No LbVirtualServer found for CI: {} (prefix search: {})", dto.name(), prefixSearch);
            }
        }

        // Cleanup CIs
        for (final LbVirtualServerCi ci : allCis) {
            if (!importedSysIds.contains(ci.getSnowSysId())) {
                lbVirtualServerCiRepository.delete(ci);
                log.debug("Deleted LbVirtualServerCi (not in import): {}", ci.getSnowName());
            }
        }

        // Update AppServices on LbVirtualServer level
        for (Map.Entry<Long, Set<String>> entry : vsToAppServices.entrySet()) {
            Long vsId = entry.getKey();
            List<String> appServiceNumbers = new ArrayList<>(entry.getValue());
            updateVsAppServiceAssociations(vsId, new ArrayList<>(entry.getValue()));
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    protected void processSnowPackageRepositories(List<SnowDataRequestDTO.PackageRepositoryDTO> packageRepositories) {
        if (packageRepositories == null || packageRepositories.isEmpty()) {
            return;
        }

        final List<Repository> allRepos = repositoryRepository.findAllWithAppservices();

        // Map for SysId lookup
        final Map<String, Repository> repoBySysId = allRepos.stream()
                .filter(r -> r.getSnowSysId() != null)
                .collect(Collectors.toMap(Repository::getSnowSysId, Function.identity()));

        // Map for Name lookup (fallback)
        final Map<String, Repository> repoByName = allRepos.stream()
                .collect(Collectors.toMap(Repository::getName, Function.identity(), (existing, replacement) -> existing));

        final Map<Long, Set<String>> existingAssociations = allRepos.stream()
                .collect(Collectors.toMap(
                        Repository::getId,
                        r -> r.getAppservices().stream()
                                .map(Appservice::getNumber)
                                .collect(Collectors.toSet())
                ));

        final Set<Long> importedRepoIds = new HashSet<>();

        for (final SnowDataRequestDTO.PackageRepositoryDTO dto : packageRepositories) {
            if (dto.sysId() == null || dto.name() == null) {
                continue;
            }

            try {
                Repository repo = repoBySysId.get(dto.sysId());
                if (repo == null) {
                    repo = repoByName.get(dto.name());
                }

                if (repo != null) {
                    importedRepoIds.add(repo.getId());
                    final OffsetDateTime lastDiscovered = parseSnowDateTime(dto.lastDiscovered());

                    if (!Objects.equals(repo.getSnowName(), dto.name()) ||
                            !Objects.equals(repo.getSnowSysId(), dto.sysId()) ||
                            !Objects.equals(repo.getSnowSysClass(), dto.sysClass()) ||
                            isTimeChanged(repo.getSnowLastDiscovered(), lastDiscovered)) {

                        repositoryRepository.updateSnowFields(
                                repo.getId(),
                                dto.name(),
                                dto.sysId(),
                                dto.sysClass(),
                                lastDiscovered
                        );
                        log.debug("Updated Snow fields for Repository: {}", dto.name());
                    }

                    updateRepoAppServiceAssociations(repo.getId(), dto.appServiceNumber(), existingAssociations.get(repo.getId()));
                }
            } catch (Exception e) {
                log.error("Failed to process package repository: sys_id={}, name={}", dto.sysId(), dto.name(), e);
            }
        }

        for (final Repository repo : allRepos) {
            if (!importedRepoIds.contains(repo.getId())) {
                if (repo.getSnowSysId() != null || repo.getSnowName() != null || repo.getSnowSysClass() != null) {
                    try {
                        repositoryRepository.updateSnowFields(repo.getId(), null, null, null, null);
                        repositoryRepository.deleteAppServiceAssociations(repo.getId());
                        log.debug("Cleared Snow fields and associations for Repository: {}", repo.getName());
                    } catch (Exception e) {
                        log.error("Failed to clear Snow fields and associations for Repository: id={}, name={}", repo.getId(), repo.getName(), e);
                    }
                }
            }
        }
    }

    private void updateRepoAppServiceAssociations(Long repoId, List<String> incomingNumbers, Set<String> currentNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = currentNumbers != null ? currentNumbers : Collections.emptySet();

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                repositoryRepository.deleteAppServiceAssociations(repoId);
            } else {
                repositoryRepository.deleteObsoleteAppServiceAssociations(repoId, cleanIncoming);
                repositoryRepository.addAppServiceAssociations(repoId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for repo ID {}: {} incoming numbers", repoId, cleanIncoming.size());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    protected void processSnowDatabaseInstances(List<SnowDataRequestDTO.DatabaseInstanceDTO> databaseInstances) {
        if (databaseInstances == null) {
            databaseInstances = Collections.emptyList();
        }

        final List<DatabaseInstance> allDatabaseInstances = databaseInstanceRepository.findAllWithAppservicesAndServers();

        final Map<String, DatabaseInstance> databaseInstanceBySysId = allDatabaseInstances.stream()
                .filter(databaseInstance -> databaseInstance.getSnowSysId() != null)
                .collect(Collectors.toMap(DatabaseInstance::getSnowSysId, Function.identity(), (existing, replacement) -> existing));

       final Set<Long> importedDatabaseInstanceIds = new HashSet<>();

        for (final SnowDataRequestDTO.DatabaseInstanceDTO dto : databaseInstances) {
            if (dto == null || dto.sysId() == null || dto.sysId().isBlank()) {
                continue;
            }

            try {
                DatabaseInstance databaseInstance = databaseInstanceBySysId.get(dto.sysId());
                final OffsetDateTime lastDiscovered = parseSnowDateTime(dto.lastDiscovered());

                if (databaseInstance == null) {
                    databaseInstanceRepository.insertIfNotExists(
                            dto.name(),
                            dto.sysId(),
                            dto.sysClass(),
                            lastDiscovered,
                            dto.version()
                    );

                    final Optional<DatabaseInstance> newDatabaseInstance = databaseInstanceRepository.findBySnowSysIdWithAppservicesAndServers(dto.sysId());
                    if (newDatabaseInstance.isEmpty()) {
                        log.warn("DatabaseInstance was not created or found after insert: sys_id={}", dto.sysId());
                        continue;
                    }

                    importedDatabaseInstanceIds.add(newDatabaseInstance.get().getId());
                    updateDatabaseInstanceAppServiceAssociations(newDatabaseInstance.get(), dto.appServiceNumber());
                    updateDatabaseInstanceServerAssociations(newDatabaseInstance.get(), dto.serverSysIds());

                    log.debug("Created DatabaseInstance from Snow import: sys_id={}, name={}", dto.sysId(), dto.name());
                    continue;
                }

                importedDatabaseInstanceIds.add(databaseInstance.getId());

                if (!Objects.equals(databaseInstance.getSnowName(), dto.name()) ||
                        !Objects.equals(databaseInstance.getSnowSysClass(), dto.sysClass()) ||
                        !Objects.equals(databaseInstance.getSnowLastDiscovered(), lastDiscovered) ||
                        !Objects.equals(databaseInstance.getSnowVersion(), dto.version())) {

                    databaseInstanceRepository.updateSnowFields(
                            databaseInstance.getId(),
                            dto.name(),
                            dto.sysClass(),
                            lastDiscovered,
                            dto.version()
                    );

                    log.debug("Updated Snow fields for DatabaseInstance: sys_id={}, name={}", dto.sysId(), dto.name());
                }

                updateDatabaseInstanceAppServiceAssociations(databaseInstance, dto.appServiceNumber());
                updateDatabaseInstanceServerAssociations(databaseInstance, dto.serverSysIds());
            } catch (Exception e) {
                log.error("Failed to process database instance: sys_id={}, name={}", dto.sysId(), dto.name(), e);
            }
        }

        for (final DatabaseInstance databaseInstance : allDatabaseInstances) {
            if (!importedDatabaseInstanceIds.contains(databaseInstance.getId())) {
                try {
                    databaseInstanceRepository.deleteAppServiceAssociations(databaseInstance.getId());
                    databaseInstanceRepository.deleteServerAssociations(databaseInstance.getId());
                    databaseInstanceRepository.deleteDatabaseInstanceById(databaseInstance.getId());
                    log.debug("Deleted DatabaseInstance not present in Snow import: id={}, sys_id={}, name={}",
                            databaseInstance.getId(), databaseInstance.getSnowSysId(), databaseInstance.getSnowName());
                } catch (Exception e) {
                    log.error("Failed to delete DatabaseInstance: id={}, sys_id={}, name={}",
                            databaseInstance.getId(), databaseInstance.getSnowSysId(), databaseInstance.getSnowName(), e);
                }
            }
        }
    }

    private void updateDatabaseInstanceAppServiceAssociations(DatabaseInstance databaseInstance, List<String> incomingNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = databaseInstance.getAppservices().stream()
                .map(Appservice::getNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                databaseInstanceRepository.deleteAppServiceAssociations(databaseInstance.getId());
            } else {
                databaseInstanceRepository.deleteObsoleteAppServiceAssociations(databaseInstance.getId(), cleanIncoming);
                databaseInstanceRepository.addAppServiceAssociations(databaseInstance.getId(), cleanIncoming);
            }
            log.debug("Synchronized AppService associations for DatabaseInstance ID {}: {} incoming numbers",
                    databaseInstance.getId(), cleanIncoming.size());
        }
    }

    private void updateDatabaseInstanceServerAssociations(DatabaseInstance databaseInstance, List<String> incomingServerSysIds) {
        final List<String> cleanIncoming = incomingServerSysIds != null ? incomingServerSysIds : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = databaseInstance.getServers().stream()
                .map(Server::getSnowServerSysId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                databaseInstanceRepository.deleteServerAssociations(databaseInstance.getId());
            } else {
                databaseInstanceRepository.deleteObsoleteServerAssociations(databaseInstance.getId(), cleanIncoming);
                databaseInstanceRepository.addServerAssociations(databaseInstance.getId(), cleanIncoming);
            }
            log.debug("Synchronized Server associations for DatabaseInstance ID {}: {} incoming server sys_ids",
                    databaseInstance.getId(), cleanIncoming.size());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    protected void processSnowDatabasePdbInstances(List<SnowDataRequestDTO.DatabasePdbInstanceDTO> databasePdbInstances) {
        if (databasePdbInstances == null) {
            databasePdbInstances = Collections.emptyList();
        }

        final List<DatabasePdbInstance> allDatabasePdbInstances = databasePdbInstanceRepository.findAllWithAppservicesAndDatabaseInstances();

        final Map<String, DatabasePdbInstance> databasePdbInstanceBySysId = allDatabasePdbInstances.stream()
                .filter(databasePdbInstance -> databasePdbInstance.getSnowSysId() != null)
                .collect(Collectors.toMap(DatabasePdbInstance::getSnowSysId, Function.identity(), (existing, replacement) -> existing));

        final Set<Long> importedDatabasePdbInstanceIds = new HashSet<>();

        for (final SnowDataRequestDTO.DatabasePdbInstanceDTO dto : databasePdbInstances) {
            if (dto == null || dto.sysId() == null || dto.sysId().isBlank()) {
                continue;
            }

            try {
                DatabasePdbInstance databasePdbInstance = databasePdbInstanceBySysId.get(dto.sysId());
                final OffsetDateTime lastDiscovered = parseSnowDateTime(dto.lastDiscovered());

                if (databasePdbInstance == null) {
                    databasePdbInstanceRepository.insertIfNotExists(
                            dto.name(),
                            dto.sysId(),
                            dto.sysClass(),
                            lastDiscovered,
                            dto.sid()
                    );

                    final Optional<DatabasePdbInstance> newDatabasePdbInstance =
                            databasePdbInstanceRepository.findBySnowSysIdWithAppservicesAndDatabaseInstances(dto.sysId());

                    if (newDatabasePdbInstance.isEmpty()) {
                        log.warn("DatabasePdbInstance was not created or found after insert: sys_id={}", dto.sysId());
                        continue;
                    }

                    importedDatabasePdbInstanceIds.add(newDatabasePdbInstance.get().getId());
                    updateDatabasePdbInstanceAppServiceAssociations(newDatabasePdbInstance.get(), dto.appServiceNumber());
                    updateDatabasePdbInstanceDatabaseInstanceAssociations(newDatabasePdbInstance.get(), dto.databaseInstanceSysIds());

                    log.debug("Created DatabasePdbInstance from Snow import: sys_id={}, name={}", dto.sysId(), dto.name());
                    continue;
                }

                importedDatabasePdbInstanceIds.add(databasePdbInstance.getId());

                if (!Objects.equals(databasePdbInstance.getSnowName(), dto.name()) ||
                        !Objects.equals(databasePdbInstance.getSnowSysClass(), dto.sysClass()) ||
                        !Objects.equals(databasePdbInstance.getSnowLastDiscovered(), lastDiscovered) ||
                        !Objects.equals(databasePdbInstance.getSnowPdb(), dto.sid())) {

                    databasePdbInstanceRepository.updateSnowFields(
                            databasePdbInstance.getId(),
                            dto.name(),
                            dto.sysClass(),
                            lastDiscovered,
                            dto.sid()
                    );

                    log.debug("Updated Snow fields for DatabasePdbInstance: sys_id={}, name={}", dto.sysId(), dto.name());
                }

                updateDatabasePdbInstanceAppServiceAssociations(databasePdbInstance, dto.appServiceNumber());
                updateDatabasePdbInstanceDatabaseInstanceAssociations(databasePdbInstance, dto.databaseInstanceSysIds());
            } catch (Exception e) {
                log.error("Failed to process database pdb instance: sys_id={}, name={}", dto.sysId(), dto.name(), e);
            }
        }

        for (final DatabasePdbInstance databasePdbInstance : allDatabasePdbInstances) {
            if (!importedDatabasePdbInstanceIds.contains(databasePdbInstance.getId())) {
                try {
                    databasePdbInstanceRepository.deleteAppServiceAssociations(databasePdbInstance.getId());
                    databasePdbInstanceRepository.deleteDatabaseInstanceAssociations(databasePdbInstance.getId());
                    databasePdbInstanceRepository.deleteDatabasePdbInstanceById(databasePdbInstance.getId());
                    log.debug("Deleted DatabasePdbInstance not present in Snow import: id={}, sys_id={}, name={}",
                            databasePdbInstance.getId(), databasePdbInstance.getSnowSysId(), databasePdbInstance.getSnowName());
                } catch (Exception e) {
                    log.error("Failed to delete DatabasePdbInstance: id={}, sys_id={}, name={}",
                            databasePdbInstance.getId(), databasePdbInstance.getSnowSysId(), databasePdbInstance.getSnowName(), e);
                }
            }
        }
    }

    private void updateDatabasePdbInstanceAppServiceAssociations(DatabasePdbInstance databasePdbInstance, List<String> incomingNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = databasePdbInstance.getAppservices().stream()
                .map(Appservice::getNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                databasePdbInstanceRepository.deleteAppServiceAssociations(databasePdbInstance.getId());
            } else {
                databasePdbInstanceRepository.deleteObsoleteAppServiceAssociations(databasePdbInstance.getId(), cleanIncoming);
                databasePdbInstanceRepository.addAppServiceAssociations(databasePdbInstance.getId(), cleanIncoming);
            }
            log.debug("Synchronized AppService associations for DatabasePdbInstance ID {}: {} incoming numbers",
                    databasePdbInstance.getId(), cleanIncoming.size());
        }
    }

    private void updateDatabasePdbInstanceDatabaseInstanceAssociations(DatabasePdbInstance databasePdbInstance, List<String> incomingDatabaseInstanceSysIds) {
        final List<String> cleanIncoming = incomingDatabaseInstanceSysIds != null ? incomingDatabaseInstanceSysIds : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = databasePdbInstance.getDatabaseInstances().stream()
                .map(DatabaseInstance::getSnowSysId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                databasePdbInstanceRepository.deleteDatabaseInstanceAssociations(databasePdbInstance.getId());
            } else {
                databasePdbInstanceRepository.deleteObsoleteDatabaseInstanceAssociations(databasePdbInstance.getId(), cleanIncoming);
                databasePdbInstanceRepository.addDatabaseInstanceAssociations(databasePdbInstance.getId(), cleanIncoming);
            }
            log.debug("Synchronized DatabaseInstance associations for DatabasePdbInstance ID {}: {} incoming database instance sys_ids",
                    databasePdbInstance.getId(), cleanIncoming.size());
        }
    }

    /**
     * Strips everything after the last "_vs" in a LoadBalancer name.
     * Example: "/test/test.example.org_https_vs-Redirect-" -> "/test/test.example.org_https_vs"
     *
     * @param name The LoadBalancer name to process
     * @return The stripped name, or the original name if "_vs" is not found
     */
    public static String stripVsSuffix(final String name) {
        if (name == null) {
            return null;
        }
        int lastVsIndex = name.toLowerCase().lastIndexOf("_vs");
        if (lastVsIndex != -1) {
            return name.substring(0, lastVsIndex + 3);
        }
        return name;
    }

    private void updateVsAppServiceAssociations(Long vsId, List<String> incomingNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentNumbers = lbVirtualServerRepository.findAppserviceNumbersByVsId(vsId);

        if (!currentNumbers.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                lbVirtualServerCiRepository.deleteVsAppServiceAssociations(vsId);
            } else {
                lbVirtualServerCiRepository.deleteObsoleteVsAppServiceAssociations(vsId, cleanIncoming);
                lbVirtualServerCiRepository.addVsAppServiceAssociations(vsId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for VirtualServer ID {}: {} incoming numbers", vsId, cleanIncoming.size());
        }
    }

    private void updateLbCiAppServiceAssociations(Long ciId, List<String> incomingNumbers, Set<String> currentNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = currentNumbers != null ? currentNumbers : Collections.emptySet();

        if (!currentSet.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                lbVirtualServerCiRepository.deleteAppServiceAssociations(ciId);
            } else {
                lbVirtualServerCiRepository.deleteObsoleteAppServiceAssociations(ciId, cleanIncoming);
                lbVirtualServerCiRepository.addAppServiceAssociations(ciId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for CI ID {}: {} incoming numbers", ciId, cleanIncoming.size());
        }
    }

    private void updateBucketAppServiceAssociations(Long bucketId, List<String> incomingNumbers, Set<String> currentNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);
        final Set<String> currentSet = currentNumbers != null ? currentNumbers : Collections.emptySet();

        // Native queries are only executed if the sets differ
        if (!currentSet.equals(incomingSet)) {
            // Remove associations to AppServices no longer in the list
            if (cleanIncoming.isEmpty()) {
                storageGridBucketRepository.deleteAppServiceAssociations(bucketId);
            } else {
                storageGridBucketRepository.deleteObsoleteAppServiceAssociations(bucketId, cleanIncoming);
            }

            // Add new associations (ON CONFLICT DO NOTHING prevents duplicates)
            if (!cleanIncoming.isEmpty()) {
                storageGridBucketRepository.addAppServiceAssociations(bucketId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for bucket ID {}: {} incoming numbers", bucketId, cleanIncoming.size());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void processSnowKubernetesClusters(List<SnowDataRequestDTO.KubernetesClusterDTO> clusterDTOs) {
        if (clusterDTOs == null || clusterDTOs.isEmpty()) {
            return;
        }

        final List<KubernetesCluster> allClusters = kubernetesClusterRepository.findAll();
        final Map<String, KubernetesCluster> clusterMap = allClusters.stream()
                .collect(Collectors.toMap(KubernetesCluster::getSysId, Function.identity()));

        final Set<String> importedClusterSysIds = new HashSet<>();

        for (final SnowDataRequestDTO.KubernetesClusterDTO dto : clusterDTOs) {
            if (dto.sysId() == null) continue;
            importedClusterSysIds.add(dto.sysId());

            KubernetesCluster cluster = clusterMap.get(dto.sysId());
            if (cluster == null) {
                cluster = new KubernetesCluster();
                cluster.setSysId(dto.sysId());
            }

            updateKubernetesClusterFields(cluster, dto);
            cluster = kubernetesClusterRepository.save(cluster);

            processSnowKubernetesNamespaces(cluster, dto.kubernetesNamespaces());
        }

        // Cleanup Clusters
        for (final KubernetesCluster cluster : allClusters) {
            if (!importedClusterSysIds.contains(cluster.getSysId())) {
                kubernetesClusterRepository.delete(cluster);
                log.debug("Deleted KubernetesCluster (not in import): {}", cluster.getName());
            }
        }
    }

    private void updateKubernetesClusterFields(KubernetesCluster cluster, SnowDataRequestDTO.KubernetesClusterDTO dto) {
        cluster.setName(dto.name());
        cluster.setSysClass(dto.sysClass());
        cluster.setLastDiscovered(dto.lastDiscovered() != null ? Date.from(parseSnowDateTime(dto.lastDiscovered()).toInstant()) : null);
        cluster.setK8sUid(dto.k8sUid());
        cluster.setEnvironment(parseEnvironmentType(dto.environment()));
    }

    private void processSnowKubernetesNamespaces(KubernetesCluster cluster, List<SnowDataRequestDTO.KubernetesNamespaceDTO> namespaceDTOs) {
        final List<KubernetesNamespace> existingNamespaces = kubernetesNamespaceRepository.findAllByClusterIdWithAppservices(cluster.getId());
        final Map<String, KubernetesNamespace> namespaceMap = existingNamespaces.stream()
                .collect(Collectors.toMap(KubernetesNamespace::getSysId, Function.identity()));

        final Set<String> importedNamespaceSysIds = new HashSet<>();

        if (namespaceDTOs != null) {
            for (final SnowDataRequestDTO.KubernetesNamespaceDTO dto : namespaceDTOs) {
                if (dto.sysId() == null) continue;
                importedNamespaceSysIds.add(dto.sysId());

                KubernetesNamespace namespace = namespaceMap.get(dto.sysId());
                if (namespace == null) {
                    namespace = new KubernetesNamespace();
                    namespace.setSysId(dto.sysId());
                    namespace.setCluster(cluster);
                }

                updateKubernetesNamespaceFields(namespace, dto);
                namespace = kubernetesNamespaceRepository.save(namespace);

                // Update AppService Associations
                Set<String> currentAppServiceNumbers = namespace.getAppservices().stream()
                        .map(Appservice::getNumber)
                        .collect(Collectors.toSet());
                updateNamespaceAppServiceAssociations(namespace.getId(), dto.appServiceNumber(), currentAppServiceNumbers);
            }
        }

        // Cleanup Namespaces for this Cluster
        for (final KubernetesNamespace namespace : existingNamespaces) {
            if (!importedNamespaceSysIds.contains(namespace.getSysId())) {
                kubernetesNamespaceRepository.delete(namespace);
                log.debug("Deleted KubernetesNamespace (not in import for cluster {}): {}", cluster.getName(), namespace.getName());
            }
        }
    }

    private void updateKubernetesNamespaceFields(KubernetesNamespace namespace, SnowDataRequestDTO.KubernetesNamespaceDTO dto) {
        namespace.setName(dto.name());
        namespace.setSysClass(dto.sysClass());
        namespace.setLastDiscovered(dto.lastDiscovered() != null ? Date.from(parseSnowDateTime(dto.lastDiscovered()).toInstant()) : null);
        namespace.setK8sUid(dto.k8sUid());
        namespace.setEnvironment(parseEnvironmentType(dto.environment()));
    }

    private void updateNamespaceAppServiceAssociations(Long namespaceId, List<String> incomingNumbers, Set<String> currentNumbers) {
        final List<String> cleanIncoming = incomingNumbers != null ? incomingNumbers : Collections.emptyList();
        final Set<String> incomingSet = new HashSet<>(cleanIncoming);

        if (!currentNumbers.equals(incomingSet)) {
            if (cleanIncoming.isEmpty()) {
                kubernetesNamespaceRepository.deleteAppServiceAssociations(namespaceId);
            } else {
                kubernetesNamespaceRepository.deleteObsoleteAppServiceAssociations(namespaceId, cleanIncoming);
                kubernetesNamespaceRepository.addAppServiceAssociations(namespaceId, cleanIncoming);
            }
            log.debug("Synchronized AppService associations for namespace ID {}: {} incoming numbers", namespaceId, cleanIncoming.size());
        }
    }

    private boolean hasUserChanged(final User existingUser, final SnowDataRequestDTO.UserDTO userDTO) {
        return !existingUser.getUsername().equals(userDTO.userId()) ||
                !existingUser.getSysId().equals(userDTO.sysId()) ||
                !java.util.Objects.equals(existingUser.getDepartment(), userDTO.department()) ||
                !java.util.Objects.equals(existingUser.getName(), userDTO.name()) ||
                !java.util.Objects.equals(existingUser.getEmail(), userDTO.email());
    }

    private void updateExistingUser(final User existingUser, SnowDataRequestDTO.UserDTO userDTO) {
        existingUser.setUsername(userDTO.userId());
        existingUser.setSysId(userDTO.sysId());
        existingUser.setDepartment(userDTO.department() != null ? userDTO.department() : "");
        existingUser.setName(userDTO.name());
        existingUser.setEmail(userDTO.email());
    }

    private User createNewUser(final SnowDataRequestDTO.UserDTO userDTO) {
        final User newUser = new User();
        newUser.setUsername(userDTO.userId());
        newUser.setSysId(userDTO.sysId());
        newUser.setDepartment(userDTO.department() != null ? userDTO.department() : "");
        newUser.setName(userDTO.name());
        newUser.setEmail(userDTO.email());
        newUser.setAdmin(false);
        newUser.setSpecialRole(false);
        return newUser;
    }

    private boolean hasGroupChanged(final Group existingGroup, final SnowDataRequestDTO.GroupDTO groupDTO, final Map<String, User> userBySysIdMap) {
        if (!existingGroup.getName().equals(groupDTO.name())) {
            return true;
        }
        final String newManagerSysId = groupDTO.manager();
        final String currentManagerSysId = existingGroup.getManager() != null ? existingGroup.getManager().getSysId() : null;
        if (!Objects.equals(currentManagerSysId, newManagerSysId)) {
            return true;
        }
        final Set<String> currentMemberSysIds = existingGroup.getUsers().stream()
                .map(User::getSysId)
                .collect(Collectors.toSet());

        final Set<String> newMemberSysIds = prepareGroupMemberSysIds(groupDTO, userBySysIdMap);
        return !currentMemberSysIds.equals(newMemberSysIds);
    }

    private void updateExistingGroup(final Group existingGroup, final SnowDataRequestDTO.GroupDTO groupDTO, final Map<String, User> userBySysIdMap) {
        existingGroup.setName(groupDTO.name());
        if (groupDTO.manager() != null) {
            User manager = userBySysIdMap.get(groupDTO.manager());
            existingGroup.setManager(manager);
        }
        final Set<String> memberSysIds = prepareGroupMemberSysIds(groupDTO, userBySysIdMap);
        final Set<User> members = memberSysIds.stream()
                .map(userBySysIdMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        existingGroup.setUsers(members);
    }

    private Group createNewGroup(final SnowDataRequestDTO.GroupDTO groupDTO, final Map<String, User> userBySysIdMap) {
        final Group newGroup = new Group();
        newGroup.setSysId(groupDTO.sysId());
        newGroup.setName(groupDTO.name());
        newGroup.setManager(userBySysIdMap.get(groupDTO.manager()));
        final Set<String> memberSysIds = prepareGroupMemberSysIds(groupDTO, userBySysIdMap);
        final Set<User> members = memberSysIds.stream()
                .map(userBySysIdMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        newGroup.setUsers(members);
        return newGroup;
    }

    private Set<String> prepareGroupMemberSysIds(final SnowDataRequestDTO.GroupDTO groupDTO, final Map<String, User> userBySysIdMap) {
        final Set<String> memberSysIds = new HashSet<>();
        if (groupDTO.members() != null) {
            memberSysIds.addAll(groupDTO.members());
        }
        if (groupDTO.manager() != null && userBySysIdMap.containsKey(groupDTO.manager())) {
            memberSysIds.add(groupDTO.manager());
        }
        return memberSysIds;
    }

    protected OffsetDateTime parseSnowDateTime(final String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }

        try {
            final LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString, SNOW_DATE_FORMATTER);
            return localDateTime.atOffset(ZoneOffset.UTC).withNano(0);
        } catch (DateTimeParseException e) {
            log.warn("Error parsing Snow date: {}", dateTimeString, e);
            return null;
        }
    }

    private boolean isTimeChanged(OffsetDateTime current, OffsetDateTime incoming) {
        if (current == null && incoming == null) return false;
        if (current == null || incoming == null) return true;
        return !current.toInstant().equals(incoming.toInstant());
    }

    protected OffsetDateTime parseSnowDateTimeClosedAt(final String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }

        try {
            final LocalDateTime localDateTime = LocalDateTime.parse(dateTimeString, SNOW_DATE_FORMATTER_CLOSED_AT);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            log.warn("Error parsing Snow ClosedAt date: {}", dateTimeString, e);
            return null;
        }
    }

    private boolean hasAppServiceChanged(final Appservice existing, final SnowDataRequestDTO.AppServiceDTO dto, final Map<String, Server> serversBySnowSysIdMap) {
        boolean hasChanged = false;

        // Name Vergleich
        boolean nameChanged = !Objects.equals(existing.getName(), dto.name());
        if (nameChanged) {
            log.debug("AppService {}: Name geändert von '{}' zu '{}'", existing.getSysId(), existing.getName(), dto.name());
            hasChanged = true;
        }

        // Number Vergleich
        boolean numberChanged = !Objects.equals(existing.getNumber(), dto.number());
        if (numberChanged) {
            log.debug("AppService {}: Number geändert von '{}' zu '{}'", existing.getSysId(), existing.getNumber(), dto.number());
            hasChanged = true;
        }

        // UsedFor Vergleich
        boolean usedForChanged = !Objects.equals(existing.getUsedFor(), dto.usedFor());
        if (usedForChanged) {
            log.debug("AppService {}: UsedFor geändert von '{}' zu '{}'", existing.getSysId(), existing.getUsedFor(), dto.usedFor());
            hasChanged = true;
        }

        // Environment Vergleich
        EnvironmentType newEnvironment = parseEnvironmentType(dto.environment());
        boolean environmentChanged = !Objects.equals(existing.getEnvironment(), newEnvironment);
        if (environmentChanged) {
            log.debug("AppService {}: Environment geändert von '{}' zu '{}'", existing.getSysId(), existing.getEnvironment(), newEnvironment);
            hasChanged = true;
        }

        // CswEnforced Vergleich
        boolean cswEnforcedChanged = !Objects.equals(existing.getCswEnforced(), dto.cswEnforced());
        if (cswEnforcedChanged) {
            log.debug("AppService {}: CswEnforced geändert von '{}' zu '{}'", existing.getSysId(), existing.getCswEnforced(), dto.cswEnforced());
            hasChanged = true;
        }

        // BusinessServiceNumbers Vergleich
        boolean businessServiceNumbersChanged = hasBusinessServiceNumbersChanged(existing, dto);
        if (businessServiceNumbersChanged) {
            log.debug("AppService {}: BusinessServiceNumbers geändert!", existing.getSysId());
            hasChanged = true;
        }

        // Group Vergleich
        boolean groupChanged = hasGroupChanged(existing, dto);
        if (groupChanged) {
            log.debug("AppService {}: Group wurde geändert", existing.getSysId());
            hasChanged = true;
        }

        // OwnedBy Vergleich
        boolean ownedByChanged = hasOwnedByChanged(existing, dto);
        if (ownedByChanged) {
            log.debug("AppService {}: OwnedBy wurde geändert", existing.getSysId());
            hasChanged = true;
        }

        // ServiceOwnerDelegate Vergleich
        boolean serviceOwnerDelegateChanged = hasServiceOwnerDelegateChanged(existing, dto);
        if (serviceOwnerDelegateChanged) {
            log.debug("AppService {}: ServiceOwnerDelegate wurde geändert", existing.getSysId());
            hasChanged = true;
        }

        // ServerAssignments Vergleich
        boolean serverAssignmentsChanged = hasServerAssignmentsChanged(existing, dto, serversBySnowSysIdMap);
        if (serverAssignmentsChanged) {
            log.debug("AppService {}: ServerAssignments wurden geändert", existing.getSysId());
            hasChanged = true;
        }

        if (!hasChanged) {
            log.debug("AppService {}: Keine Änderungen erkannt", existing.getSysId());
        }

        return hasChanged;
    }

    private void updateAppService(final Appservice appService, final SnowDataRequestDTO.AppServiceDTO dto, final Map<String, User> userBySysIdMap, final Map<String, Group> groupsByGroupMap, final Map<String, Server> serversBySnowSysIdMap) {
        appService.setName(dto.name());
        appService.setNumber(dto.number());
        appService.setUsedFor(dto.usedFor());
        appService.setEnvironment(parseEnvironmentType(dto.environment()));
        appService.setCswEnforced(dto.cswEnforced());

        // Business Service Numbers als kommagetrennte Liste speichern
        if (dto.businessServiceNumbers() != null && !dto.businessServiceNumbers().isEmpty()) {
            appService.setBusinessServiceNumbers(String.join(",", dto.businessServiceNumbers()));
        } else {
            appService.setBusinessServiceNumbers(null);
        }

        // Group setzen
        if (dto.group() != null) {
            Group group = groupsByGroupMap.get(dto.group());
            appService.setChangeGroup(group);
        } else {
            appService.setChangeGroup(null);
        }

        // OwnedBy setzen
        if (dto.ownedBy() != null) {
            User ownedBy = userBySysIdMap.get(dto.ownedBy());
            appService.setOwnedBy(ownedBy);
        } else {
            appService.setOwnedBy(null);
        }

        // Service Owner Delegate setzen
        if (dto.serviceOwnerDelegate() != null) {
            User serviceOwnerDelegate = userBySysIdMap.get(dto.serviceOwnerDelegate());
            appService.setServiceOwnerDelegate(serviceOwnerDelegate);
        } else {
            appService.setServiceOwnerDelegate(null);
        }

        // Server-Zuordnungen aktualisieren
        updateServerAssignments(appService, dto.cis(), serversBySnowSysIdMap);
    }

    private Appservice createAppService(final SnowDataRequestDTO.AppServiceDTO dto, final Map<String, User> userBySysIdMap, final Map<String, Group> groupsByGroupMap, final Map<String, Server> serversBySnowSysIdMap) {
        Appservice appService = new Appservice();
        appService.setSysId(dto.sysId());
        appService.setName(dto.name());
        appService.setNumber(dto.number());
        appService.setUsedFor(dto.usedFor());
        appService.setEnvironment(parseEnvironmentType(dto.environment()));
        appService.setCswEnforced(dto.cswEnforced());

        // Business Service Numbers als kommagetrennte Liste speichern
        if (dto.businessServiceNumbers() != null && !dto.businessServiceNumbers().isEmpty()) {
            appService.setBusinessServiceNumbers(String.join(",", dto.businessServiceNumbers()));
        }

        // Group setzen
        if (dto.group() != null) {
            final Group group = groupsByGroupMap.get(dto.group());
            appService.setChangeGroup(group);
        }

        // OwnedBy setzen
        if (dto.ownedBy() != null) {
            final User ownedBy = userBySysIdMap.get(dto.ownedBy());
            appService.setOwnedBy(ownedBy);
        }

        // Service Owner Delegate setzen
        if (dto.serviceOwnerDelegate() != null) {
            final User serviceOwnerDelegate = userBySysIdMap.get(dto.serviceOwnerDelegate());
            appService.setServiceOwnerDelegate(serviceOwnerDelegate);
        }

        // Server-Zuordnungen setzen
        updateServerAssignments(appService, dto.cis(), serversBySnowSysIdMap);

        return appService;
    }

    private boolean hasBusinessServiceNumbersChanged(final Appservice existing, final SnowDataRequestDTO.AppServiceDTO dto) {
        if ((dto.businessServiceNumbers() == null || dto.businessServiceNumbers().isEmpty()) && (existing.getBusinessServiceNumbers() == null || existing.getBusinessServiceNumbers().isBlank())) {
            return false;
        }
        if (dto.businessServiceNumbers() == null || existing.getBusinessServiceNumbers() == null) {
            return true;
        }
        final String newBusinessServiceNumbers = String.join(",", dto.businessServiceNumbers());
        return !Objects.equals(existing.getBusinessServiceNumbers(), newBusinessServiceNumbers);
    }


    private boolean hasGroupChanged(final Appservice existing, final SnowDataRequestDTO.AppServiceDTO dto) {
        if ((dto.group() == null || dto.group().isBlank()) && existing.getChangeGroup() == null) {
            return false;
        }
        if (dto.group() == null || existing.getChangeGroup() == null) {
            return true;
        }
        return !Objects.equals(existing.getChangeGroup().getSysId(), dto.group());
    }

    private boolean hasOwnedByChanged(final Appservice existing, final SnowDataRequestDTO.AppServiceDTO dto) {
        if ((dto.ownedBy() == null || dto.ownedBy().isBlank()) && existing.getOwnedBy() == null) {
            return false;
        }
        if (dto.ownedBy() == null || existing.getOwnedBy() == null) {
            return true;
        }
        return !Objects.equals(existing.getOwnedBy().getSysId(), dto.ownedBy());
    }

    private boolean hasServiceOwnerDelegateChanged(final Appservice existing, final SnowDataRequestDTO.AppServiceDTO dto) {
        if ((dto.serviceOwnerDelegate() == null || dto.serviceOwnerDelegate().isBlank()) && existing.getServiceOwnerDelegate() == null) {
            return false;
        }
        if (dto.serviceOwnerDelegate() == null || existing.getServiceOwnerDelegate() == null) {
            return true;
        }
        return !Objects.equals(existing.getServiceOwnerDelegate().getSysId(), dto.serviceOwnerDelegate());
    }

    private boolean hasServerAssignmentsChanged(final Appservice existing, final SnowDataRequestDTO.AppServiceDTO dto, final Map<String, Server> serversBySnowSysIdMap) {
        // Bestehende Server-IDs sammeln
        final Set<Long> existingServerIds = existing.getServers().stream()
                .map(Server::getId)
                .collect(Collectors.toSet());

        // Früher Abbruch wenn beide leer sind
        if ((dto.cis() == null || dto.cis().isEmpty()) && existingServerIds.isEmpty()) {
            return false;
        }

        // Neue Server-IDs aus Snow-Daten ermitteln
        final Set<Long> incomingServerIds = new HashSet<>();
        if (dto.cis() != null) {
            for (final String ciSysId : dto.cis()) {
                final Server server = serversBySnowSysIdMap.get(ciSysId);
                if (server != null) {
                    incomingServerIds.add(server.getId());
                }
            }
        }

        return !existingServerIds.equals(incomingServerIds);
    }

    private void updateServerAssignments(final Appservice appService, final List<String> ciSysIds, final Map<String, Server> serversBySnowSysIdMap) {
        // Neue Server-IDs aus Snow-Daten ermitteln
        Set<Long> incomingServerIds = new HashSet<>();
        if (ciSysIds != null && !ciSysIds.isEmpty()) {
            for (final String ciSysId : ciSysIds) {
                final Server server = serversBySnowSysIdMap.get(ciSysId);
                if (server != null) {
                    incomingServerIds.add(server.getId());
                }
            }
        }

        // Schutzzeit für neue Beziehungen
        OffsetDateTime twoHoursAgo = OffsetDateTime.now().minusHours(2);

        // ALLE created_at Timestamps für diesen AppService auf einmal laden (Performance!)
        Map<Long, OffsetDateTime> assignmentCreatedAtMap = getAssignmentCreatedAtMap(appService.getId());

        // Aktuelle Server als Set für schnelle Lookups
        Set<Server> currentServers = new HashSet<>(appService.getServers());

        // Server die entfernt werden sollen
        Set<Server> serversToRemove = new HashSet<>();

        // Server die hinzugefügt werden sollen
        Set<Server> serversToAdd = new HashSet<>();

        // Bestehende Server durchgehen und prüfen welche entfernt werden sollen
        for (Server existingServer : currentServers) {
            if (!incomingServerIds.contains(existingServer.getId())) {
                // Server ist NICHT in Snow-Daten - prüfen ob noch innerhalb Schutzzeit
                OffsetDateTime assignmentCreatedAt = assignmentCreatedAtMap.get(existingServer.getId());

                if (assignmentCreatedAt != null && assignmentCreatedAt.isAfter(twoHoursAgo)) {
                    // Noch innerhalb der Schutzzeit (< 2 Stunden alt) - behalten
                    log.debug("Protected recent assignment: AppService={}, Server={}, created={}",
                            appService.getNumber(), existingServer.getName(), assignmentCreatedAt);
                } else {
                    // Älter als 2 Stunden und nicht in Snow - wird gelöscht
                    serversToRemove.add(existingServer);
                    log.debug("Removing assignment (not in Snow data): AppService={}, Server={}",
                            appService.getNumber(), existingServer.getName());
                }
            }
        }

        // Map für schnelleren Lookup erstellen: serverId -> Server
        Map<Long, Server> serverByIdMap = serversBySnowSysIdMap.values().stream()
                .collect(Collectors.toMap(Server::getId, Function.identity(), (existing, replacement) -> existing));

        // Neue Server aus Snow-Daten hinzufügen (die noch nicht in der Beziehung sind)
        for (Long serverId : incomingServerIds) {
            Server server = serverByIdMap.get(serverId);

            if (server != null && !currentServers.contains(server)) {
                serversToAdd.add(server);
                log.debug("Adding new assignment from Snow: AppService={}, Server={}",
                        appService.getNumber(), server.getName());
            }
        }

        // Nur tatsächliche Änderungen durchführen
        appService.getServers().removeAll(serversToRemove);
        appService.getServers().addAll(serversToAdd);
    }

    private EnvironmentType parseEnvironmentType(final String environment) {
        if (environment == null) {
            return null;
        }
        if ("Production".equalsIgnoreCase(environment) || "P".equalsIgnoreCase(environment)) {
            return EnvironmentType.P;
        }
        if ("Development".equalsIgnoreCase(environment) || "C".equalsIgnoreCase(environment)) {
            return EnvironmentType.C;
        }
        if ("Test".equalsIgnoreCase(environment) || "K".equalsIgnoreCase(environment)) {
            return EnvironmentType.K;
        }
        if ("Training".equalsIgnoreCase(environment) || "S".equalsIgnoreCase(environment)) {
            return EnvironmentType.S;
        }
        try {
            return EnvironmentType.valueOf(environment.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Fallback oder Logging
            return null;
        }
    }

    /**
     * Entfernt Server aus einem AppService, die nicht mehr existieren.
     * Beziehungen, die jünger als 2 Stunden sind, werden geschützt und nicht gelöscht.
     */
    private void removeInvalidServersWithProtection(Appservice appService) {
        OffsetDateTime twoHoursAgo = OffsetDateTime.now().minusHours(2);

        // ALLE created_at Timestamps für diesen AppService auf einmal laden
        Map<Long, OffsetDateTime> assignmentCreatedAtMap = getAssignmentCreatedAtMap(appService.getId());

        // Server die entfernt werden sollen
        Set<Server> serversToRemove = new HashSet<>();

        for (Server server : appService.getServers()) {
            if (!serverRepository.existsById(server.getId())) {
                // Server existiert nicht mehr - prüfen ob die Beziehung jünger als 2 Stunden ist
                OffsetDateTime assignmentCreatedAt = assignmentCreatedAtMap.get(server.getId());

                if (assignmentCreatedAt != null && assignmentCreatedAt.isAfter(twoHoursAgo)) {
                    // Beziehung ist noch zu neu - behalten (Schutz vor Timing-Problem)
                    log.debug("Protected recent assignment from deletion: AppService={}, Server={}, created={}",
                            appService.getNumber(), server.getName(), assignmentCreatedAt);
                } else {
                    // Beziehung ist alt genug - kann gelöscht werden
                    serversToRemove.add(server);
                    log.debug("Removing invalid server assignment: AppService={}, Server={}",
                            appService.getNumber(), server.getName());
                }
            }
        }

        // Nur tatsächliche Änderungen durchführen
        appService.getServers().removeAll(serversToRemove);
    }

    /**
     * Lädt alle created_at Timestamps für einen AppService in einer Query.
     * @return Map mit server_id -> created_at
     */
    private Map<Long, OffsetDateTime> getAssignmentCreatedAtMap(Long appserviceId) {
        Map<Long, OffsetDateTime> result = new HashMap<>();

        try {
            List<Object[]> rows = serverRepository.findAllAssignmentCreatedAtByAppserviceId(appserviceId);

            for (Object[] row : rows) {
                Long serverId = ((Number) row[0]).longValue();

                if (row[1] instanceof java.sql.Timestamp) {
                    OffsetDateTime createdAt = ((java.sql.Timestamp) row[1]).toLocalDateTime().atOffset(ZoneOffset.UTC);
                    result.put(serverId, createdAt);
                } else if (row[1] instanceof java.time.LocalDateTime) {
                    OffsetDateTime createdAt = ((java.time.LocalDateTime) row[1]).atOffset(ZoneOffset.UTC);
                    result.put(serverId, createdAt);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch assignment created_at map for appservice={}", appserviceId, e);
        }

        return result;
    }
}
