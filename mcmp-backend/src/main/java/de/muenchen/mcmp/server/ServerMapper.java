package de.muenchen.mcmp.server;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceNameAndSysId;
import de.muenchen.mcmp.appservice.AppserviceNameAndSysIdDTO;
import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.job.ActiveGreenItJob;
import de.muenchen.mcmp.job.ActiveGreenItJobDTO;
import de.muenchen.mcmp.types.EnvironmentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ServerMapper {
    @Mapping(target = "appserviceNames", source = "appservices", qualifiedByName = "appservicesToNames")
    ServerListExtendedDTO toDTO(final Server server);

    @Mapping(target = "cloud", expression = "java(mapCloud(server))")
    @Mapping(target = "bootTime", source = "bootTime", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "memoryMbChangeDate", source = "memoryMbChangeDate", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "numCpuChangeDate", source = "numCpuChangeDate", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "memoryMbChangeDatePrev", source = "memoryMbChangeDatePrev", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "numCpuChangeDatePrev", source = "numCpuChangeDatePrev", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "snowServerLastDiscovered", source = "snowServerLastDiscovered", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "snowInstanceLastDiscovered", source = "snowInstanceLastDiscovered", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "patchnightStartDate", source = "patchnightStartDate", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "patchnightEndDate", source = "patchnightEndDate", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "patchnightExitcodeChangeDate", source = "patchnightExitcodeChangeDate", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "tempPrivilegesExpiresAt", source = "tempPrivilegesExpiresAt", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "maintenanceModeExpiresAt", source = "maintenanceModeExpiresAt", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "patchnightEnvironment", source = "patchnightEnvironment", qualifiedByName = "stringToEnvironmentType")
    @Mapping(target = "greenItShutdownChangeRejectedDate", source = "greenItShutdownChangeRejectedDate", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "greenItRightsizingChangeRejectedDate", source = "greenItRightsizingChangeRejectedDate", qualifiedByName = "instantToOffsetDateTime")
    @Mapping(target = "mfgTime", source = "mfgTime", qualifiedByName = "instantToOffsetDateTime")
    ServerFullDTO toFullDTO(final ServerWithPermissions server);

    ServerFullDTO toFullDTOFromSever(final Server server);

    @Mapping(target = "appservices", ignore = true)
    ServerFullDTO toFullDTOWithoutAppservices(final Server server);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "memoryMbRecommended", ignore = true)
    @Mapping(target = "numCpuRecommended", ignore = true)
    @Mapping(target = "appservices", ignore = true)
    Server toEntity(final ServerFullDTO serverFullDTO);

    @Named("appservicesToNames")
    default String appservicesToNames(Set<Appservice> appservices) {
        if (appservices == null || appservices.isEmpty()) {
            return null;
        }
        return appservices.stream()
                .map(appservice -> appservice.getName()) // Annahme: Appservice hat getName() Methode
                .collect(Collectors.joining(", "));
    }

    @Named("instantToOffsetDateTime")
    default OffsetDateTime instantToOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC);
    }

    default Cloud mapCloud(ServerWithPermissions s) {
        if (s == null) return null;
        Cloud c = new Cloud();
        c.setId(s.getCloudId());
        c.setName(s.getCloudName());
        c.setFqdn(s.getCloudFqdn());
        c.setCloudType(s.getCloudType());
        c.setServerGui(s.getCloudServerGui());
        return c;
    }

    default ServerFullDTO toFullDTOWithAppservices(final ServerWithPermissions server, final List<AppserviceNameAndSysId> appservices, final List<ActiveGreenItJob> activeGreenItJobs) {
        var dto = toFullDTO(server);

        List<AppserviceNameAndSysIdDTO> appserviceDTOs = appservices.stream()
                .map(appservice -> new AppserviceNameAndSysIdDTO(
                        appservice.getId(),
                        appservice.getName(),
                        appservice.getSysId()))
                .toList();

        List<ActiveGreenItJobDTO> jobDTOs = activeGreenItJobs.stream()
                .map(job -> new ActiveGreenItJobDTO(
                        job.getActionTitle(),
                        job.getChangeStartDate().atOffset(ZoneOffset.UTC),
                        job.getChangeNumber(),
                        job.getChangeLink()))
                .toList();

        return ServerFullDTO.builder()
                .id(dto.id())
                .cloud(dto.cloud())
                .uuid(dto.uuid())
                .instanceUuid(dto.instanceUuid())
                .vmId(dto.vmId())
                .cluster(dto.cluster())
                .host(dto.host())
                .location(dto.location())
                .name(dto.name())
                .powerState(dto.powerState())
                .memoryMb(dto.memoryMb())
                .memoryMbPrev(dto.memoryMbPrev())
                .memoryMbChangeDate(dto.memoryMbChangeDate())
                .memoryMbChangeDatePrev(dto.memoryMbChangeDatePrev())
                .numCpu(dto.numCpu())
                .numCpuPrev(dto.numCpuPrev())
                .numCpuChangeDate(dto.numCpuChangeDate())
                .numCpuChangeDatePrev(dto.numCpuChangeDatePrev())
                .numCoresPerSocket(dto.numCoresPerSocket())
                .memoryHotAddEnabled(dto.memoryHotAddEnabled())
                .cpuHotAddEnabled(dto.cpuHotAddEnabled())
                .cpuHotRemoveEnabled(dto.cpuHotRemoveEnabled())
                .cpuTopology(dto.cpuTopology())
                .vmxVersion(dto.vmxVersion())
                .overallStatus(dto.overallStatus())
                .configStatus(dto.configStatus())
                .configEqualsTools(dto.configEqualsTools())
                .guestConfigId(dto.guestConfigId())
                .guestConfigFullName(dto.guestConfigFullName())
                .guestToolsId(dto.guestToolsId())
                .guestToolsFullName(dto.guestToolsFullName())
                .guestToolsState(dto.guestToolsState())
                .guestToolsRunningStatus(dto.guestToolsRunningStatus())
                .guestToolsVersionStatus(dto.guestToolsVersionStatus())
                .guestToolsVersionStatus2(dto.guestToolsVersionStatus2())
                .guestToolsInstallType(dto.guestToolsInstallType())
                .guestToolsVersion(dto.guestToolsVersion())
                .guestToolsFamily(dto.guestToolsFamily())
                .guestToolsHostname(dto.guestToolsHostname())
                .guestToolsIpAddress(dto.guestToolsIpAddress())
                .guestToolsArchitecture(dto.guestToolsArchitecture())
                .guestToolsBitness(dto.guestToolsBitness())
                .guestToolsBuildNumber(dto.guestToolsBuildNumber())
                .guestToolsCpeString(dto.guestToolsCpeString())
                .guestToolsDistroAddlVersion(dto.guestToolsDistroAddlVersion())
                .guestToolsDistroName(dto.guestToolsDistroName())
                .guestToolsDistroVersion(dto.guestToolsDistroVersion())
                .guestToolsFamilyName(dto.guestToolsFamilyName())
                .guestToolsKernelVersion(dto.guestToolsKernelVersion())
                .guestToolsPrettyName(dto.guestToolsPrettyName())
                .vdisks(dto.vdisks())
                .vdisksCapacityInBytes(dto.vdisksCapacityInBytes())
                .bootTime(dto.bootTime())
                .roleLinux(dto.roleLinux())
                .roleWindows(dto.roleWindows())
                .roleOracle(dto.roleOracle())
                .roleNonOracle(dto.roleNonOracle())
                .patchnightGroup(dto.patchnightGroup())
                .patchnightTime(dto.patchnightTime())
                .serverInfosOwnerMail(dto.serverInfosOwnerMail())
                .serverInfosTicketNo(dto.serverInfosTicketNo())
                .tetrationAgentInstalled(dto.tetrationAgentInstalled())
                .managed(dto.managed())
                .fqdn(dto.fqdn())
                .foremanId(dto.foremanId())
                .foremanSource(dto.foremanSource())
                .dbOracle(dto.dbOracle())
                .dbMariadb(dto.dbMariadb())
                .dbHana(dto.dbHana())
                .dbMysql(dto.dbMysql())
                .dbMssql(dto.dbMssql())
                .dbPostgres(dto.dbPostgres())
                .dbMongodb(dto.dbMongodb())
                .dbAdabas(dto.dbAdabas())
                .memoryMbRecommended(dto.memoryMbRecommended())
                .numCpuRecommended(dto.numCpuRecommended())
                .snowServerName(dto.snowServerName())
                .snowServerSysId(dto.snowServerSysId())
                .snowServerSysClass(dto.snowServerSysClass())
                .snowServerHardwareStatus(dto.snowServerHardwareStatus())
                .snowServerLastDiscovered(dto.snowServerLastDiscovered())
                .snowInstanceName(dto.snowInstanceName())
                .snowInstanceSysId(dto.snowInstanceSysId())
                .snowInstanceSysClass(dto.snowInstanceSysClass())
                .snowInstanceLastDiscovered(dto.snowInstanceLastDiscovered())
                .patchnightIncluded(dto.patchnightIncluded())
                .patchnightEnvironment(dto.patchnightEnvironment())
                .patchnightStartDate(dto.patchnightStartDate())
                .patchnightEndDate(dto.patchnightEndDate())
                .patchnightExitcode(dto.patchnightExitcode())
                .patchnightExitstring(dto.patchnightExitstring())
                .patchnightChangeNumber(dto.patchnightChangeNumber())
                .patchnightChangeSysId(dto.patchnightChangeSysId())
                .canEdit(dto.canEdit())
                .appservices(appserviceDTOs)
                .maintenanceMode(dto.maintenanceMode())
                .maintenanceModeExpiresAt(dto.maintenanceModeExpiresAt())
                .hasTempAdminPrivileges(dto.hasTempAdminPrivileges())
                .hasTempRootPrivileges(dto.hasTempRootPrivileges())
                .tempPrivilegesExpiresAt(dto.tempPrivilegesExpiresAt())
                .runningJobsCount(dto.runningJobsCount())
                .runningGreenItCount(dto.runningGreenItCount())
                .numberOfAssignedAppservices(dto.numberOfAssignedAppservices())
                .patchnightExitcodeChangeDate(dto.patchnightExitcodeChangeDate())
                .hotPlugMemoryLimit(dto.hotPlugMemoryLimit())
                .hotPlugMemoryIncrementSize(dto.hotPlugMemoryIncrementSize())
                .operatingsystem(dto.operatingsystem())
                .activeGreenItJobs(jobDTOs)
                .serverKind(dto.serverKind())
                .serverType(dto.serverType())
                .greenItShutdownChangePending(dto.greenItShutdownChangePending())
                .greenItShutdownChangeRejectedDate(dto.greenItShutdownChangeRejectedDate())
                .greenItRightsizingChangePending(dto.greenItRightsizingChangePending())
                .greenItRightsizingChangeRejectedDate(dto.greenItRightsizingChangeRejectedDate())
                .dn(dto.dn())
                .association(dto.association())
                .memorySpeed(dto.memorySpeed())
                .memoryMbAvailable(dto.memoryMbAvailable())
                .mfgTime(dto.mfgTime())
                .model(dto.model())
                .numOfAdaptors(dto.numOfAdaptors())
                .numOfCoresEnabled(dto.numOfCoresEnabled())
                .numOfEthHostIfs(dto.numOfEthHostIfs())
                .numOfFcHostIfs(dto.numOfFcHostIfs())
                .operState(dto.operState())
                .ucsmChassisId(dto.ucsmChassisId())
                .ucsmChassisSlotId(dto.ucsmChassisSlotId())
                .ucsmServerId(dto.ucsmServerId())
                .vendor(dto.vendor())
                .vid(dto.vid())
                .os(dto.os())
                .serverCustomAttributes(dto.serverCustomAttributes())
                .locked(dto.locked())
                .cpuUtil(dto.cpuUtil())
                .memUsedPercent(dto.memUsedPercent())
                .memoryAllocationExpandableReservation(dto.memoryAllocationExpandableReservation())
                .memoryAllocationLimit(dto.memoryAllocationLimit())
                .memoryAllocationOverheadLimit(dto.memoryAllocationOverheadLimit())
                .memoryAllocationReservation(dto.memoryAllocationReservation())
                .cpuAllocationExpandableReservation(dto.cpuAllocationExpandableReservation())
                .cpuAllocationLimit(dto.cpuAllocationLimit())
                .cpuAllocationOverheadLimit(dto.cpuAllocationOverheadLimit())
                .cpuAllocationReservation(dto.cpuAllocationReservation())
                .build();
    }

    @Named("stringToEnvironmentType")
    default EnvironmentType stringToEnvironmentType(String env) {
        if (env == null) return null;
        try {
            return EnvironmentType.valueOf(env.toUpperCase());  // Annahme: Strings sind in Großbuchstaben
        } catch (IllegalArgumentException e) {
            return null;  // Oder Standardwert
        }
    }

    default Map<String, String> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse server_custom_attributes JSON", e);
        }
    }

}
