package de.muenchen.mcmp.network;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceRepository;
import de.muenchen.mcmp.clients.infoblox.NetworkRequestDTO;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.infobloxConfig.InfobloxConfig;
import de.muenchen.mcmp.infobloxConfig.InfobloxConfigDTO;
import de.muenchen.mcmp.infobloxConfig.InfobloxConfigService;
import de.muenchen.mcmp.types.EnvironmentType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class NetworkService {

    private final NetworkRepository networkRepository;
    private final NetworkGroupRepository networkGroupRepository;
    private final NetworkMapper networkMapper;
    private final InfobloxConfigService infobloxConfigService;

    private final AppserviceRepository appserviceRepository;

    public List<NetworkDTO> getAllNetworks() {
        final List<Network> networks = networkRepository.findAll();

        final Map<Long, String> infobloxDescriptionMap = infobloxConfigService.findAll()
                .stream()
                .collect(Collectors.toMap(
                        AbstractEntity::getId,
                        InfobloxConfig::getApiDescription,
                        (existing, replacement) -> existing
                ));

        return networks.stream().map(network -> {
            final NetworkDTO baseDto = networkMapper.toDTO(network);
            final String infobloxDescription = infobloxDescriptionMap.get(network.getInfobloxId());

            return NetworkDTO.builder()
                    .id(baseDto.id())
                    .broadcast(baseDto.broadcast())
                    .cidr(baseDto.cidr())
                    .comment(baseDto.comment())
                    .dnsPrimary(baseDto.dnsPrimary())
                    .dnsSecondary(baseDto.dnsSecondary())
                    .environment(baseDto.environment())
                    .gateway(baseDto.gateway())
                    .infobloxId(baseDto.infobloxId())
                    .ipAddress(baseDto.ipAddress())
                    .name(baseDto.name())
                    .netmask(baseDto.netmask())
                    .networkGroupId(baseDto.networkGroupId())
                    .networktyp(baseDto.networktyp())
                    .referat(baseDto.referat())
                    .vlan(baseDto.vlan())
                    .mcmpStatus(baseDto.mcmpStatus())
                    .mcmpNetworkTyp(baseDto.mcmpNetworkTyp())
                    .mcmpNetworkGroup(baseDto.mcmpNetworkGroup())
                    .infoblox(infobloxDescription)  // apiDescription hier setzen
                    .build();
        }).toList();
    }

    public List<NetworkGroupDTO> getAllNetworkGroups() {
        return networkMapper.toDTos(networkGroupRepository.findAllWithAppservices());
    }


    public List<NetworkGroupDTO> findAvailableNetworkGroupsForAppservice(Long appserviceId, Boolean database) {
        return networkMapper.toDTos(networkGroupRepository.findAvailableNetworkGroupsForAppservice(appserviceId, database));
    }

    public boolean isAllowedNetworkGroupForAppservice(Long networkGroupId, Long appserviceId, Boolean database){
        final List<NetworkGroupDTO> networkGroups = findAvailableNetworkGroupsForAppservice(appserviceId, database);
        return networkGroups.stream().anyMatch(group -> Objects.equals(group.id(), networkGroupId));
    }

    @Transactional
    public NetworkGroupDTO updateNetworkGroup(final NetworkGroupDTO networkGroupDTO) {
        final NetworkGroup existingGroup = networkGroupRepository.findById(networkGroupDTO.id()).orElseThrow(() ->
                new NoSuchElementException("Network group with ID " + networkGroupDTO.id() + " does not exist."));
        final NetworkGroup updatedGroup = networkMapper.toGroupEntity(networkGroupDTO);
        updatedGroup.setVersion(existingGroup.getVersion());
        updatedGroup.setCreatedAt(existingGroup.getCreatedAt());
        updatedGroup.setUpdatedAt(new Date());
        return networkMapper.toGroupDTO(networkGroupRepository.save(updatedGroup));
    }

    @Transactional
    public NetworkGroupDTO createNetworkGroup(final NetworkGroupDTO networkGroupDTO) {
        if (networkGroupDTO.id() != null && networkGroupRepository.existsById(networkGroupDTO.id())) {
            throw new IllegalArgumentException("Network group with ID " + networkGroupDTO.id() + " already exists.");
        }
        return networkMapper.toGroupDTO(networkGroupRepository.save(networkMapper.toGroupEntity(networkGroupDTO)));
    }

    @Transactional
    public void deleteNetworkGroup(final Long id) {
        if (!networkGroupRepository.existsById(id)) {
            throw new NoSuchElementException("Network group with ID " + id + " does not exist.");
        }
        networkGroupRepository.deleteById(id);
    }

    @Transactional
    public void assignNetworkToGroup(final Long networkId, final Long groupId) {
        final Network network = networkRepository.findById(networkId).orElseThrow(() ->
                new NoSuchElementException("Network with ID " + networkId + " does not exist."));

        if (groupId == null || groupId <= 0) {
            network.setNetworkGroupId(null);
            networkRepository.save(network);
            return;
        }

        networkGroupRepository.findById(groupId).orElseThrow(() ->
                new NoSuchElementException("Network group with ID " + groupId + " does not exist."));

        network.setNetworkGroupId(groupId);
        networkRepository.save(network);
    }

    @Transactional
    public void assignAppservicesToNetworkGroup(final Long networkGroupId, final List<Long> appserviceIds) {
        final NetworkGroup networkGroup = networkGroupRepository.findById(networkGroupId).orElseThrow(() ->
                new IllegalArgumentException("Network group with ID " + networkGroupId + " does not exist."));

        Set<Appservice> appservicesToAdd = new LinkedHashSet<>(appserviceRepository.findAllById(appserviceIds));
        networkGroup.setAppservices(appservicesToAdd);
        networkGroupRepository.save(networkGroup);
    }

    @Transactional
    public void processNetworkList(List<NetworkRequestDTO> networkRequests) {
        final Map<String, Long> apiEndpointMap = buildApiEndpointMap(networkRequests);
        Map<String, NetworkGroup> existingNetworkGroups = buildNetworkGroupMap();
        final Map<String, Network> existingNetworks = buildNetworkMap();

        final Set<String> importedNetworkGroupKeys = new HashSet<>();
        final Set<String> importedNetworkCidrs = new HashSet<>();

        final List<NetworkGroup> networkGroupsToSave = new ArrayList<>();
        final List<Network> networksToSave = new ArrayList<>();

        processNetworkGroups(networkRequests, existingNetworkGroups, importedNetworkGroupKeys, networkGroupsToSave);

        if (!networkGroupsToSave.isEmpty()) {
            List<NetworkGroup> savedGroups = networkGroupRepository.saveAll(networkGroupsToSave);
            // Map komplett neu aufbauen nach dem Speichern
            existingNetworkGroups = buildNetworkGroupMap();
            log.info("Saved {} network groups", savedGroups.size());
        }

        processNetworks(networkRequests, apiEndpointMap, existingNetworkGroups, existingNetworks,
                importedNetworkCidrs, networksToSave);

        if (!networksToSave.isEmpty()) {
            networkRepository.saveAll(networksToSave);
            log.info("Saved {} networks", networksToSave.size());
        }

        deleteObsoleteEntities(existingNetworkGroups, existingNetworks, importedNetworkGroupKeys, importedNetworkCidrs);
    }


    private Map<String, Long> buildApiEndpointMap(final List<NetworkRequestDTO> networkRequests) {
        final Map<String, Long> apiEndpointMap = new HashMap<>();
        for (final NetworkRequestDTO request : networkRequests) {
            if (!apiEndpointMap.containsKey(request.apiEndpoint())) {
                final var infobloxConfigDTO = infobloxConfigService.findConfigByApiEndpoint(request.apiEndpoint());
                apiEndpointMap.put(request.apiEndpoint(), infobloxConfigDTO.map(InfobloxConfigDTO::id).orElse(null));
            }
        }
        return apiEndpointMap;
    }

    private Map<String, NetworkGroup> buildNetworkGroupMap() {
        return networkGroupRepository.findAll().stream()
                .collect(Collectors.toMap(
                        group -> buildNetworkGroupKey(group.getName(), group.getEnvironment()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private String buildNetworkGroupKey(String name, EnvironmentType environment) {
        return name + ":" + (environment != null ? environment.toString() : "null");
    }

    private Map<String, Network> buildNetworkMap() {
        return networkRepository.findAll().stream()
                .collect(Collectors.toMap(Network::getCidr, Function.identity(), (existing, replacement) -> existing));
    }

    private void processNetworkGroups(final List<NetworkRequestDTO> networkRequests,
                                      final Map<String, NetworkGroup> existingNetworkGroups,
                                      final Set<String> importedNetworkGroupKeys,
                                      final List<NetworkGroup> networkGroupsToSave) {

        final Map<String, Map<String, Set<String>>> groupedByNameAndEnv = networkRequests.stream()
                .filter(request -> request.mcmpNetworkGroup() != null && !request.mcmpNetworkGroup().isBlank())
                .collect(Collectors.groupingBy(
                        NetworkRequestDTO::mcmpNetworkGroup,
                        Collectors.groupingBy(
                                request -> request.environment() != null ? request.environment() : "null",
                                Collectors.mapping(NetworkRequestDTO::mcmpNetworkTyp,
                                        Collectors.filtering(Objects::nonNull, Collectors.toSet()))
                        )
                ));

        for (final Map.Entry<String, Map<String, Set<String>>> groupEntry : groupedByNameAndEnv.entrySet()) {
            final String groupName = groupEntry.getKey();
            final Map<String, Set<String>> environmentTypes = groupEntry.getValue();

            for (final Map.Entry<String, Set<String>> envEntry : environmentTypes.entrySet()) {
                final String environmentStr = envEntry.getKey();
                final Set<String> types = envEntry.getValue();
                final EnvironmentType environment = EnvironmentType.fromString(environmentStr.equals("null") ? null : environmentStr);
                final String networkGroupKey = buildNetworkGroupKey(groupName, environment);
                importedNetworkGroupKeys.add(networkGroupKey);

                NetworkGroup networkGroup = existingNetworkGroups.get(networkGroupKey);
                boolean isNew = false;

                if (networkGroup == null) {
                    networkGroup = new NetworkGroup();
                    networkGroup.setName(groupName);
                    networkGroup.setEnvironment(environment);
                    isNew = true;
                    log.debug("Creating new NetworkGroup: {} with environment: {}", groupName, environment);
                }

                final boolean application = types.contains("application");
                final boolean database = types.contains("database");
                final boolean storage = types.contains("storage");
                final boolean restrict = types.contains("restrict");
                if (isNew || hasNetworkGroupChanges(networkGroup, application, database, storage, restrict, environment)) {
                    networkGroup.setApplication(application);
                    networkGroup.setDatabase(database);
                    networkGroup.setStorage(storage);
                    networkGroup.setRestrict(restrict);
                    if (environment != null) {
                        networkGroup.setEnvironment(environment);
                    }
                    networkGroupsToSave.add(networkGroup);
                    log.debug("NetworkGroup {} will be saved/updated", networkGroup.getName());
                }
            }
        }
    }


    private boolean hasNetworkGroupChanges(NetworkGroup networkGroup, boolean application, boolean database, boolean storage, boolean restrict, EnvironmentType environment) {
        return networkGroup.isApplication() != application ||
               networkGroup.isDatabase() != database ||
               networkGroup.isStorage() != storage ||
               networkGroup.isRestrict() != restrict ||
               !Objects.equals(networkGroup.getEnvironment(), environment);
    }

    private void processNetworks(final List<NetworkRequestDTO> networkRequests,
                                 final Map<String, Long> apiEndpointMap,
                                 final Map<String, NetworkGroup> existingNetworkGroups,
                                 final Map<String, Network> existingNetworks,
                                 final Set<String> importedNetworkCidrs,
                                 final List<Network> networksToSave) {

        for (final NetworkRequestDTO request : networkRequests) {
            if (request.cidr() == null || request.cidr().isBlank()) {
                continue;
            }

            importedNetworkCidrs.add(request.cidr());

            final Long infobloxConfigId = apiEndpointMap.get(request.apiEndpoint());
            Network network = existingNetworks.get(request.cidr());
            boolean isNew = false;

            if (network == null) {
                Optional<Network> newNetwork = createNetworkFromRequest(request, infobloxConfigId);
                if (newNetwork.isEmpty()) {
                    continue;
                }
                network = newNetwork.get();
                isNew = true;
            }

            Long networkGroupId = null;
            if (request.mcmpNetworkGroup() != null && !request.mcmpNetworkGroup().isBlank()) {
                final EnvironmentType requestEnvironment = EnvironmentType.fromString(request.environment());
                final String networkGroupKey = buildNetworkGroupKey(request.mcmpNetworkGroup(), requestEnvironment);
                NetworkGroup networkGroup = existingNetworkGroups.get(networkGroupKey);
                if (networkGroup != null) {
                    networkGroupId = networkGroup.getId();
                }
            }

            if (isNew || hasNetworkChanges(network, request, networkGroupId)) {
                updateNetworkFromRequest(network, request, networkGroupId);
                networksToSave.add(network);
            }
        }
    }

    private boolean hasNetworkChanges(final Network network, final NetworkRequestDTO request, final Long networkGroupId) {
        final String vlanString = formatVlans(request);

        return !Objects.equals(network.getIpAddress(), request.ipAddress()) ||
               !Objects.equals(network.getNetmask(), request.netmask()) ||
               !Objects.equals(network.getGateway(), request.gateway()) ||
               !Objects.equals(network.getBroadcast(), request.broadcast()) ||
               !Objects.equals(network.getDnsPrimary(), request.dnsPrimary()) ||
               !Objects.equals(network.getDnsSecondary(), request.dnsSecondary()) ||
               !Objects.equals(network.getName(), request.name()) ||
               !Objects.equals(network.getReferat(), request.referat()) ||
               !Objects.equals(network.getEnvironment(), EnvironmentType.fromString(request.environment())) ||
               !Objects.equals(network.getNetworktyp(), request.networktype()) ||
               !Objects.equals(network.getComment(), request.comment()) ||
               !Objects.equals(network.getNetworkGroupId(), networkGroupId) ||
               !Objects.equals(network.getMcmpStatus(), request.mcmpStatus()) ||
               !Objects.equals(network.getMcmpNetworkTyp(), request.mcmpNetworkTyp()) ||
               !Objects.equals(network.getMcmpNetworkGroup(), request.mcmpNetworkGroup()) ||
               !Objects.equals(network.getVlan(), vlanString);
    }

    private void deleteObsoleteEntities(final Map<String, NetworkGroup> existingNetworkGroups,
                                        final Map<String, Network> existingNetworks,
                                        final Set<String> importedNetworkGroupKeys,
                                        final Set<String> importedNetworkCidrs) {

        final List<String> networksToDelete = existingNetworks
                .keySet()
                .stream()
                .filter(cidr -> !importedNetworkCidrs.contains(cidr))
                .toList();

        if (!networksToDelete.isEmpty()) {
            final List<Network> networks = networksToDelete
                    .stream()
                    .map(existingNetworks::get)
                    .filter(Objects::nonNull)
                    .toList();
            networkRepository.deleteAll(networks);
            log.info("Deleted {} obsolete networks", networks.size());
        }

        final List<String> networkGroupsToDelete = existingNetworkGroups
                .keySet()
                .stream()
                .filter(key -> key != null && !importedNetworkGroupKeys.contains(key))
                .toList();

        if (!networkGroupsToDelete.isEmpty()) {
            final List<NetworkGroup> networkGroups = networkGroupsToDelete.stream()
                    .map(existingNetworkGroups::get)
                    .filter(Objects::nonNull)
                    .toList();
            networkGroupRepository.deleteAll(networkGroups);
            log.info("Deleted {} obsolete network groups", networkGroups.size());
        }
    }

    private void updateNetworkFromRequest(final Network network, final NetworkRequestDTO request, final Long networkGroupId) {
        final String vlans = formatVlans(request);

        network.setIpAddress(request.ipAddress());
        network.setNetmask(request.netmask());
        network.setGateway(request.gateway());
        network.setBroadcast(request.broadcast());
        network.setDnsPrimary(request.dnsPrimary());
        network.setDnsSecondary(request.dnsSecondary());
        network.setName(request.name());
        network.setReferat(request.referat());
        network.setEnvironment(EnvironmentType.fromString(request.environment()));
        network.setNetworktyp(request.networktype());
        network.setComment(request.comment());
        network.setNetworkGroupId(networkGroupId);
        network.setMcmpStatus(request.mcmpStatus());
        network.setMcmpNetworkTyp(request.mcmpNetworkTyp());
        network.setMcmpNetworkGroup(request.mcmpNetworkGroup());
        network.setVlan(vlans);
    }

    private Optional<Network> createNetworkFromRequest(final NetworkRequestDTO request, final Long infobloxConfigId) {
        if (infobloxConfigId == null || infobloxConfigId <= 0 ||
            request.cidr() == null || request.cidr().isBlank() ||
            request.name() == null || request.name().isBlank()) {
            return Optional.empty();
        }

        final Network network = new Network();
        network.setInfobloxId(infobloxConfigId);
        network.setCidr(request.cidr());
        network.setNetworkGroupId(null);

        updateNetworkFromRequest(network, request, null);
        return Optional.of(network);
    }

    private String formatVlans(final NetworkRequestDTO request) {
        if (request.vlans() == null || request.vlans().isEmpty()) {
            return null;
        }
        final List<Integer> sortedVlans = request.vlans().stream()
                .distinct()
                .sorted()
                .toList();
        if (sortedVlans.isEmpty()) {
            return null;
        }
        final List<String> ranges = new ArrayList<>();
        int rangeStart = sortedVlans.getFirst();
        int rangeEnd = sortedVlans.getFirst();
        for (int i = 1; i < sortedVlans.size(); i++) {
            int currentVlan = sortedVlans.get(i);
            if (currentVlan == rangeEnd + 1) {
                rangeEnd = currentVlan;
            } else {
                ranges.add(formatRange(rangeStart, rangeEnd));
                rangeStart = currentVlan;
                rangeEnd = currentVlan;
            }
        }
        ranges.add(formatRange(rangeStart, rangeEnd));
        return String.join(",", ranges);
    }

    private String formatRange(int start, int end) {
        if (start == end) {
            return String.valueOf(start);
        }
        return start + "-" + end;
    }
}