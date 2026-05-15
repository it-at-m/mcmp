package de.muenchen.mcmp.server;

import de.muenchen.mcmp.appservice.AppserviceService;
import de.muenchen.mcmp.common.OffsetBasedPageRequest;
import de.muenchen.mcmp.config.app.AppConfigCacheService;
import de.muenchen.mcmp.job.ActiveGreenItJob;
import de.muenchen.mcmp.job.JobRepository;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import de.muenchen.mcmp.types.SystemMode;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class ServerService {

    private final ServerRepository repository;
    private final ServerMapper serverMapper;
    private final AppserviceService appserviceService;
    private final ServerRepository serverRepository;
    private final JobRepository jobRepository;
    private final AppConfigCacheService appConfigCacheService;

    public Page<ServerListDTO> getVisibleServers(final int offset, final int limit,
                                                 final String sortBy, final String sortOrder, final String search,
                                                 final List<String> status, final String os) {
        final Pageable pageable = (limit == -1) ? Pageable.unpaged() : new OffsetBasedPageRequest(offset, limit);
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        final OsFilter osFilter = new OsFilter(os);
        String cleanedSearch = null;
        if (search != null) {
            cleanedSearch = search.trim()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
        }
        return repository.findVisibleServers(
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasLinuxRole(),
                userRoles.hasWindowsRole(),
                userRoles.hasOracleRole(),
                userRoles.hasNonOracleRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole(),
                userRoles.hasNetworkRole(),
                cleanedSearch,
                status,
                osFilter.isLinux(),
                osFilter.isMngLinux(),
                osFilter.isWindows(),
                osFilter.isMngWindows(),
                osFilter.isWindowsClient(),
                osFilter.isOracle(),
                osFilter.isNonOracle(),
                osFilter.isUnmanaged(),
                sortBy,
                sortOrder,
                pageable
        ).map(this::mapProjectionToDTO);
    }

    public List<ServerListExtendedDTO> findServersByAppserviceId(final Long appserviceId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return repository.findServersByAppserviceId(
                appserviceId,
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasLinuxRole(),
                userRoles.hasWindowsRole(),
                userRoles.hasOracleRole(),
                userRoles.hasNonOracleRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole(),
                userRoles.hasNetworkRole()
        ).stream().map(this::mapExtendedProjectionToDTO).toList();
    }

    public List<ServerFullDTO> findFullServersByAppserviceId(final Long appserviceId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return repository.findFullServersByAppserviceId(
                        appserviceId,
                        userRoles.getUsername(),
                        userRoles.hasAdminRole(),
                        userRoles.hasReadonlyRole(),
                        userRoles.hasLinuxRole(),
                        userRoles.hasWindowsRole(),
                        userRoles.hasOracleRole(),
                        userRoles.hasNonOracleRole(),
                        userRoles.hasSecurityRole(),
                        userRoles.hasOperatorRole(),
                        userRoles.hasNetworkRole()
                ).stream()
                .map(serverMapper::toFullDTOWithoutAppservices)
                .toList();
    }

    private ServerListDTO mapProjectionToDTO(final ServerList serverList) {
        return ServerListDTO.builder()
                .id(serverList.getId())
                .name(serverList.getName())
                .powerState(serverList.getPowerState())
                .os(serverList.getOS())
                .serverKind(serverList.getServerKind())
                .serverType(serverList.getServerType())
                .hasWarinings(serverList.getHasWarnings())
                .build();
    }

    private ServerListExtendedDTO mapExtendedProjectionToDTO(final ServerListExtended serverListExtended) {
        return ServerListExtendedDTO.builder()
                .id(serverListExtended.getId())
                .name(serverListExtended.getName())
                .powerState(serverListExtended.getPowerState())
                .os(serverListExtended.getOS())
                .appserviceNames(serverListExtended.getAppserviceNames())
                .numCpu(serverListExtended.getNumCpu())
                .memoryMb(serverListExtended.getMemoryMb())
                .vdisksCapacityInBytes(serverListExtended.getVdisksCapacityInBytes())
                .serverKind(serverListExtended.getServerKind())
                .serverType(serverListExtended.getServerType())
                .managed(serverListExtended.getManaged())
                .canEdit(serverListExtended.getCanEdit())
                .build();
    }

    public ServerFullDTO getServerById(final Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        final OffsetDateTime metricsTo = OffsetDateTime.now().withSecond(0).withNano(0);
        final OffsetDateTime metricsFrom = metricsTo.minusMinutes(1);
        final var serverWithPermissions = repository.findServerWithPermissions(
                serverId,
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasReadonlyRole(),
                userRoles.hasLinuxRole(),
                userRoles.hasWindowsRole(),
                userRoles.hasOracleRole(),
                userRoles.hasNonOracleRole(),
                userRoles.hasSecurityRole(),
                userRoles.hasOperatorRole(),
                userRoles.hasNetworkRole(),
                appConfigCacheService.isMaintenanceMode(),
                metricsFrom,
                metricsTo);
        var server = serverWithPermissions.orElseThrow(() -> {
            log.warn("Server not found or not accessible. serverId={}, username={}", serverId, userRoles.getUsername());
            return new EntityNotFoundException("Server nicht gefunden.");
        });
        var appservices = appserviceService.getAppservicesByServerId(serverId);
        List<ActiveGreenItJob> activeGreenItJobs;
        if (server.getRunningGreenItCount() == null || server.getRunningGreenItCount() < 1) {
            activeGreenItJobs = new ArrayList<>();
        } else {
            activeGreenItJobs = jobRepository.findActiveGreenItJobsByServerId(serverId);
        }
        return serverMapper.toFullDTOWithAppservices(server, appservices, activeGreenItJobs);
    }

    public boolean canUserEditServer(final Long serverId) {
        final UserRoles userRoles = AuthUtils.getCurrentUserRoles();
        return repository.canUserEditServer( serverId,
                userRoles.getUsername(),
                userRoles.hasAdminRole(),
                userRoles.hasLinuxRole(),
                userRoles.hasWindowsRole(),
                userRoles.hasOracleRole(),
                userRoles.hasNonOracleRole(),
                appConfigCacheService.isMaintenanceMode());
    }

    public List<Server> findServersByMacAddress(final String macAddress) {
        if (macAddress == null || macAddress.isBlank()) {
            return List.of();
        }
        return repository.findServersByMacAddress(macAddress.toLowerCase());
    }

    public List<Server> findServersByIpAddress(final String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return List.of();
        }
        return repository.findServersByIpAddress(ipAddress);
    }

    public List<Server> findServersByUuid(final String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return List.of();
        }
        return repository.findServersByUuid(uuid);
    }

    public List<Server> findServersByInstanceUuid(final String instanceUuid) {
        if (instanceUuid == null || instanceUuid.isBlank()) {
            return List.of();
        }
        return repository.findServersByInstanceUuid(instanceUuid);
    }

    public List<Server> findServersByForemanId(final String foremanId) {
        if (foremanId == null || foremanId.isBlank()) {
            return List.of();
        }
        return repository.findServersByForemanId(foremanId);
    }

    public List<Server> findServersByServerSysId(final String serverSysId) {
        if (serverSysId == null || serverSysId.isBlank()) {
            return List.of();
        }
        return repository.findServersByServerSysId(serverSysId);
    }

    public List<Server> findServersByInstanceSysId(final String instanceSysId) {
        if (instanceSysId == null || instanceSysId.isBlank()) {
            return List.of();
        }
        return repository.findServersByInstanceSysId(instanceSysId);
    }

    public List<Server> findAll() {
        return repository.findAll();
    }

    public List<Server> findByFqdnIn(final List<String> fqdns) {
        if (fqdns == null || fqdns.isEmpty()) {
            return List.of();
        }
        final List<String> upperCaseFqdns = fqdns.stream()
                .filter(fqdn -> fqdn != null && !fqdn.isBlank())
                .map(String::toUpperCase)
                .toList();
        if (upperCaseFqdns.isEmpty()) {
            return List.of();
        }
        return repository.findByFqdnInIgnoreCase(upperCaseFqdns);
    }

    public List<Server> findByMaintenanceModeTrue() {
        return repository.findByMaintenanceModeTrue();
    }

    @Transactional
    public void updateMaintenanceMode(final Long serverId, final boolean maintenanceMode, final OffsetDateTime expiresAt) {
        repository.updateMaintenanceMode(serverId, maintenanceMode, expiresAt);
    }

    @Transactional
    public void updateRessourceRecommendations(final Long serverId, final int numCpu, final int memoryMb) {
        repository.updateRessourceRecommendations(serverId, numCpu, memoryMb);
    }

    public Optional<Server> findById(Long id) {
        return repository.findById(id);
    }

    public Server save(Server server) {
        return repository.save(server);
    }

    public List<ServerFullDTO> findAllPatchnightErrorServers() {
        return repository.findByPatchnightExitcodeNot((short) 0).stream().map(serverMapper::toFullDTOWithoutAppservices).toList();
    }


    /**
     * Saves server data in a NEW transaction, independent of any existing transaction.
     * This ensures that even if the calling transaction rolls back, this save is committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Server saveForemanDataInNewTransaction(Server server) {
        log.debug("Saving server {} in new transaction", server.getName());

        try {
            Server saved = serverRepository.save(server);
            serverRepository.flush(); // Force immediate flush
            log.debug("Server {} saved successfully", saved.getName());
            return saved;
        } catch (Exception e) {
            log.error("Error saving server {}: {}", server.getName(), e.getMessage(), e);
            throw e;
        }
    }

    public List<Server> findServersByForemanSourceAndId(final String source, final Long foremanId) {
        return serverRepository.findByForemanSourceAndForemanId(source, foremanId);
    }

    public Optional<Server> findServerByVcenterShortCodeAndUuidOptional(String vcenterShortCode, String uuid) {
        return serverRepository.findServerByVcenterShortCodeAndUuidOptional(vcenterShortCode, uuid);
    }

    public List<ServerAutocompleteDTO> searchServersForAutocomplete(final String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return serverRepository.findForAutocomplete(query);
    }
}
