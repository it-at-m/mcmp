package de.muenchen.mcmp.appservice;

import de.muenchen.mcmp.common.OffsetBasedPageRequest;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.UserRoles;
import de.muenchen.mcmp.server.ServerListDTO;
import de.muenchen.mcmp.server.ServerListExtendedDTO;
import de.muenchen.mcmp.server.ServerService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AppserviceService {
    private final AppserviceRepository repository;
    private final AppserviceMapper appserviceMapper;
    private final ServerService serverService;

    public AppserviceService(AppserviceRepository repository, AppserviceMapper appserviceMapper, @Lazy ServerService serverService) {
        this.repository = repository;
        this.appserviceMapper = appserviceMapper;
        this.serverService = serverService;
    }

    public List<AppserviceDTO> getAppservices() {
        return repository.findAll().stream().map(appserviceMapper::toDto).toList();
    }

    public Appservice getAppservice(final Long id) {
        return repository.findVisibleAppserviceById(
                AuthUtils.getUsername(),
                AuthUtils.getCurrentUserRoles().hasAdminRole(),
                AuthUtils.getCurrentUserRoles().hasReadonlyRole(),
                AuthUtils.getCurrentUserRoles().hasSecurityRole(),
                AuthUtils.getCurrentUserRoles().hasOperatorRole(),
                AuthUtils.getCurrentUserRoles().hasNetworkRole(),
                id);
    }

    public AppserviceDTO getVisibleAppservice(final Long id) {
        final Appservice appservice = getAppservice(id);
        final List<ServerListExtendedDTO> servers = serverService.findServersByAppserviceId(id);
        return appserviceMapper.toDtoWithServers(appservice, servers);
    }

    public AppserviceDTO updateAppserviceVcenterc(
            final Long id, final Boolean enableVcenterc) {
        Appservice appservice = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Appservice with ID " + id + " does not exist."));
        appservice.setEnableVcenterc(enableVcenterc);
        return appserviceMapper.toDto(repository.save(appservice));
    }

    public Page<AppserviceListDTO> getVisibleAppservices(
            final int offset, int limit, final String sortOrder, final String search) {
        final int MAX_PAGE_SIZE = 10000;
        if (limit == -1 || limit > MAX_PAGE_SIZE) {
            limit = MAX_PAGE_SIZE;
        }
        final String safeSortOrder = ("desc".equalsIgnoreCase(sortOrder)) ? "desc" : "asc";

        final String searchLowerCase = search == null || search.isBlank() ? null : search.trim().toLowerCase();
        final List<String> termsList = new ArrayList<>();
        if (searchLowerCase != null && !searchLowerCase.isBlank()) {
            for (final String term : searchLowerCase.split("[^a-z0-9]+")) {
                if (!term.isBlank()) {
                    termsList.add("%" + term + "%");
                }
            }
        }
        final String[] terms = termsList.isEmpty() ? new String[]{"%%"} : termsList.toArray(new String[0]);

        final Page<AppserviceList> appserviceListDTOPage = repository.findVisibleAppservices(
                AuthUtils.getUsername(),
                AuthUtils.getCurrentUserRoles().hasAdminRole(),
                AuthUtils.getCurrentUserRoles().hasReadonlyRole(),
                AuthUtils.getCurrentUserRoles().hasLinuxRole(),
                AuthUtils.getCurrentUserRoles().hasWindowsRole(),
                AuthUtils.getCurrentUserRoles().hasOracleRole(),
                AuthUtils.getCurrentUserRoles().hasNonOracleRole(),
                AuthUtils.getCurrentUserRoles().hasSecurityRole(),
                AuthUtils.getCurrentUserRoles().hasOperatorRole(),
                AuthUtils.getCurrentUserRoles().hasNetworkRole(),
                searchLowerCase,
                terms,
                safeSortOrder,
                new OffsetBasedPageRequest(offset, limit)
        );
        return appserviceListDTOPage.map(this::mapProjectionToDTO);
    }

    public AppserviceListDTO mapProjectionToDTO(final AppserviceList appserviceList) {
        return AppserviceListDTO.builder()
                .id(appserviceList.getId())
                .name(appserviceList.getName())
                .hasServers(appserviceList.getHasServers())
                .enableVcenterc(appserviceList.getEnableVcenterc())
                .environment(appserviceList.getEnvironment())
                .build();
    }

    public List<AppserviceNameAndSysId> getAppservicesByServerId(final Long serverId) {
        return repository.findAppservicesByServerId(serverId);
    }

    public Appservice findByNumber (String number) {
        return repository.findByNumber(number);
    }
}
