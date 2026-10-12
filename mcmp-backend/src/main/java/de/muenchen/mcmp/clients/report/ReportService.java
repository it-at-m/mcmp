package de.muenchen.mcmp.clients.report;

import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.cloud.CloudRepository;
import de.muenchen.mcmp.cloud.CloudService;
import de.muenchen.mcmp.ip.IPService;
import de.muenchen.mcmp.nic.Nic;
import de.muenchen.mcmp.nic.NicRepository;
import de.muenchen.mcmp.portgroup.PortGroup;
import de.muenchen.mcmp.portgroup.PortGroupRepository;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import de.muenchen.mcmp.server.ServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ServerRepository serverRepository;
    private final CloudRepository cloudRepository;
    private final NicRepository nicRepository;
    private final PortGroupRepository portGroupRepository;
    private final IPService ipService;

    @Transactional(readOnly = true)
    public List<ReportServerDTO> buildReport() {
        final Map<Long, List<String>> ipsByNicId = ipService.getIPsGroupedByNicId();
        final Map<Long, PortGroup> portGroupsById = portGroupRepository.findAll().stream().collect(Collectors.toMap(PortGroup::getId, Function.identity()));
        final Map<Long, List<Nic>> nicsByServerId = nicRepository.findAll().stream().collect(Collectors.groupingBy(Nic::getServerId));
        return serverRepository.findAll().stream()
                .sorted(Comparator.comparing(Server::getName, Comparator.nullsLast(String::compareTo)))
                .map(server -> toReportServerDTO(server, nicsByServerId, portGroupsById, ipsByNicId))
                .toList();
    }

    private ReportServerDTO toReportServerDTO(final Server server,
                                              final Map<Long, List<Nic>> nicsByServerId,
                                              final Map<Long, PortGroup> portGroupsById,
                                              final Map<Long, List<String>> ipsByNicId) {
        final List<ReportNicDTO> nics = nicsByServerId.getOrDefault(server.getId(), List.of()).stream()
                .sorted(Comparator.comparing(Nic::getDevice, Comparator.nullsLast(String::compareTo)))
                .map(nic -> toReportNicDTO(nic, portGroupsById, ipsByNicId))
                .toList();

        final Cloud cloud = server.getCloud();

        return new ReportServerDTO(
                cloud != null ? new ReportCloudDTO(cloud.getFqdn(), cloud.getCloudType()) : null,
                server.getName(),
                server.getFqdn(),
                server.getGuestToolsHostname(),
                server.getUuid(),
                server.getInstanceUuid(),
                server.getServerType().toString(),
                server.getPowerState(),
                server.getVmId(),
                server.getCluster(),
                server.getOperatingsystem(),
                server.getGuestConfigFullName(),
                server.getGuestToolsFullName(),
                server.getForemanId(),
                server.getForemanSource(),
                nics);
    }

    private ReportNicDTO toReportNicDTO(final Nic nic,
                                        final Map<Long, PortGroup> portGroupsById,
                                        final Map<Long, List<String>> ipsByNicId) {
        final PortGroup portGroup = nic.getPortGroup() != null
                ? portGroupsById.get(nic.getPortGroup().getId())
                : null;

        return new ReportNicDTO(
                nic.getDevice(),
                nic.getMacAddress(),
                nic.getConnected(),
                nic.getToolsConnected(),
                nic.getToolsIpAddress(),
                portGroup != null ? portGroup.getName() : null,
                portGroup != null ? portGroup.getVlan() : null,
                ipsByNicId.getOrDefault(nic.getId(), List.of()));
    }
}
