package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerStatusType;
import de.muenchen.mcmp.types.CloudType;
import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ServerDTO(
        @JsonProperty("server_kind") ServerKind serverKind,
        @JsonProperty("server_type") ServerType serverType,
        @JsonProperty("name") String name,
        @JsonProperty("uuid") String uuid,
        @JsonProperty("instance_uuid") String instanceUuid,
        @JsonProperty("vm_id") String vmId,
        @JsonProperty("cluster") String cluster,
        @JsonProperty("host") String host,
        @JsonProperty("location") String location,
        @JsonProperty("power_state") String powerState,
        @JsonProperty("memory_mb") Integer memoryMB,
        @JsonProperty("num_cpu") Integer numCPU,
        @JsonProperty("num_cores_per_socket") Integer numCoresPerSocket,
        @JsonProperty("num_of_threads") Integer num_of_threads,
        @JsonProperty("memory_hot_add_enabled") Boolean memoryHotAddEnabled,
        @JsonProperty("cpu_hot_add_enabled") Boolean cpuHotAddEnabled,
        @JsonProperty("cpu_hot_remove_enabled") Boolean cpuHotRemoveEnabled,
        @JsonProperty("cpu_topology") String cpuTopology,
        @JsonProperty("vmx_version") String vmxVersion,
        @JsonProperty("overall_status") ServerStatusType overallStatus,
        @JsonProperty("config_status") ServerStatusType configStatus,
        @JsonProperty("guest_config_id") String guestConfigId,
        @JsonProperty("guest_config_full_name") String guestConfigFullName,
        @JsonProperty("guest_tools_id") String guestToolsId,
        @JsonProperty("guest_tools_full_name") String guestToolsFullName,
        @JsonProperty("guest_tools_state") String guestToolsState,
        @JsonProperty("guest_tools_running_status") String guestToolsRunningStatus,
        @JsonProperty("guest_tools_version_status") String guestToolsVersionStatus,
        @JsonProperty("guest_tools_version_status2") String guestToolsVersionStatus2,
        @JsonProperty("guest_tools_install_type") String guestToolsInstallType,
        @JsonProperty("guest_tools_version") String guestToolsVersion,
        @JsonProperty("guest_tools_family") String guestToolsFamily,
        @JsonProperty("guest_tools_hostname") String guestToolsHostname,
        @JsonProperty("guest_tools_ip_address") String guestToolsIpAddress,
        @JsonProperty("guest_tools_architecture") String guestToolsArchitecture,
        @JsonProperty("guest_tools_bitness") String guestToolsBitness,
        @JsonProperty("guest_tools_build_number") String guestToolsBuildNumber,
        @JsonProperty("guest_tools_cpe_string") String guestToolsCpeString,
        @JsonProperty("guest_tools_distro_addl_version") String guestToolsDistroAddlVersion,
        @JsonProperty("guest_tools_distro_name") String guestToolsDistroName,
        @JsonProperty("guest_tools_distro_version") String guestToolsDistroVersion,
        @JsonProperty("guest_tools_family_name") String guestToolsFamilyName,
        @JsonProperty("guest_tools_kernel_version") String guestToolsKernelVersion,
        @JsonProperty("guest_tools_pretty_name") String guestToolsPrettyName,
        @JsonProperty("boot_time") OffsetDateTime bootTime,
        @JsonProperty("hot_plug_memory_limit") Long hotPlugMemoryLimit,
        @JsonProperty("hot_plug_memory_increment_size") Long hotPlugMemoryIncrementSize,
        @JsonProperty("dn") String dn,
        @JsonProperty("association") String association,
        @JsonProperty("memory_speed") Integer memorySpeed,
        @JsonProperty("mfg_time") OffsetDateTime mfgTime,
        @JsonProperty("model") String model,
        @JsonProperty("num_of_adaptors") Integer numOfAdaptors,
        @JsonProperty("num_of_cores_enabled") Integer numOfCoresEnabled,
        @JsonProperty("num_of_eth_host_ifs") Integer numOfEthHostIfs,
        @JsonProperty("num_of_fc_host_ifs") Integer numOfFcHostIfs,
        @JsonProperty("oper_state") String operState,
        @JsonProperty("ucsm_chassis_id") Integer chassisId,
        @JsonProperty("ucsm_chassis_slot_id") Integer slotId,
        @JsonProperty("ucsm_server_id") Integer serverId,
        @JsonProperty("available_memory") Integer availableMemory,
        @JsonProperty("vendor") String vendor,
        @JsonProperty("vid") String vid,
        @JsonProperty("memory_allocation_expandable_reservation") Boolean memoryAllocationExpandableReservation,
        @JsonProperty("memory_allocation_limit") Long memoryAllocationLimit,
        @JsonProperty("memory_allocation_overhead_limit") Long memoryAllocationOverheadLimit,
        @JsonProperty("memory_allocation_reservation") Long memoryAllocationReservation,
        @JsonProperty("cpu_allocation_expandable_reservation") Boolean cpuAllocationExpandableReservation,
        @JsonProperty("cpu_allocation_limit") Long cpuAllocationLimit,
        @JsonProperty("cpu_allocation_overhead_limit") Long cpuAllocationOverheadLimit,
        @JsonProperty("cpu_allocation_reservation") Long cpuAllocationReservation,
        @JsonProperty("disks") List<DiskDTO> disks,
        @JsonProperty("mount_points") List<MountPointDTO> mountPoints,
        @JsonProperty("nics") List<NicDTO> nics,
        @JsonProperty("snapshots") List<SnapshotDTO> snapshots
) {
    public ServerDTO {
        if (uuid == null)
            throw new NullPointerException("a server UUIDs is required.");

        if (name == null || name.isBlank()) {
            name = uuid;
        } else {
            name = name.trim();
        }

        powerState = switch (powerState != null ? powerState : "") {
            case "poweredOn", "running", "on", "up" -> "poweredOn";
            case "poweredOff", "stopped", "off", "down" -> "poweredOff";
            default -> "unknown";
        };

        /* remove various nulls */
        if (serverKind == null) serverKind = ServerKind.UNKNOWN;
        if (serverType == null) serverType = ServerType.UNKNOWN;
        if (memoryMB == null) memoryMB = 0;
        if (numCPU == null) numCPU = 0;
        if (memoryHotAddEnabled == null) memoryHotAddEnabled = false;
        if (cpuHotAddEnabled == null) cpuHotAddEnabled = false;
        if (cpuHotRemoveEnabled == null) cpuHotRemoveEnabled = false;
        if (overallStatus == null) overallStatus = ServerStatusType.gray;
        if (configStatus == null) configStatus = ServerStatusType.gray;

        if (disks == null) disks = Collections.emptyList();
        else disks.removeIf(Objects::isNull);
        if (mountPoints == null) mountPoints = Collections.emptyList();
        else mountPoints.removeIf(Objects::isNull);
        if (nics == null) nics = Collections.emptyList();
        else nics.removeIf(Objects::isNull);
        if (snapshots == null) snapshots = Collections.emptyList();
        else snapshots.removeIf(Objects::isNull);
    }

    /**
     * Determines if this DTO contains changes that warrant writing
     * to the database, based on an existing server entity.
     *
     * <p>
     * A false return value does not necessarily imply that the
     * DTO's data is identical to the server entity.
     * </p>
     *
     * @param existing The server entity to compare.
     * @return True if changes should be written.
     */
    public boolean hasChanges(final Server existing) {
        return !Objects.equals(existing.getName(), name)
                || !Objects.equals(existing.getInstanceUuid(), instanceUuid)
                || !Objects.equals(existing.getVmId(), vmId)
                || !Objects.equals(existing.getCluster(), cluster)
                || !Objects.equals(existing.getHost(), host)
                || !Objects.equals(existing.getLocation(), location)
                || !Objects.equals(existing.getPowerState(), powerState)
                || !Objects.equals(existing.getMemoryMb(), memoryMB)
                || !Objects.equals(existing.getNumCpu(), numCPU)
                || !Objects.equals(existing.getNumCoresPerSocket(), numCoresPerSocket)
                || !Objects.equals(existing.getMemoryHotAddEnabled(), memoryHotAddEnabled)
                || !Objects.equals(existing.getCpuHotAddEnabled(), cpuHotAddEnabled)
                || !Objects.equals(existing.getCpuHotRemoveEnabled(), cpuHotRemoveEnabled)
                || !Objects.equals(existing.getCpuTopology(), cpuTopology)
                || !Objects.equals(existing.getVmxVersion(), vmxVersion)
                || !Objects.equals(existing.getGuestConfigId(), guestConfigId)
                || !Objects.equals(existing.getGuestConfigFullName(), guestConfigFullName)
                || !Objects.equals(existing.getGuestToolsId(), guestToolsId)
                || !Objects.equals(existing.getGuestToolsFullName(), guestToolsFullName)
                || !Objects.equals(existing.getGuestToolsState(), guestToolsState)
                || !Objects.equals(existing.getGuestToolsRunningStatus(), guestToolsRunningStatus)
                || !Objects.equals(existing.getGuestToolsVersionStatus(), guestToolsVersionStatus)
                || !Objects.equals(existing.getGuestToolsVersionStatus2(), guestToolsVersionStatus2)
                || !Objects.equals(existing.getGuestToolsInstallType(), guestToolsInstallType)
                || !Objects.equals(existing.getGuestToolsVersion(), guestToolsVersion)
                || !Objects.equals(existing.getGuestToolsFamily(), guestToolsFamily)
                || !Objects.equals(existing.getGuestToolsHostname(), guestToolsHostname)
                || !Objects.equals(existing.getGuestToolsIpAddress(), guestToolsIpAddress)
                || !Objects.equals(existing.getGuestToolsArchitecture(), guestToolsArchitecture)
                || !Objects.equals(existing.getGuestToolsBitness(), guestToolsBitness)
                || !Objects.equals(existing.getGuestToolsBuildNumber(), guestToolsBuildNumber)
                || !Objects.equals(existing.getGuestToolsCpeString(), guestToolsCpeString)
                || !Objects.equals(existing.getGuestToolsDistroAddlVersion(), guestToolsDistroAddlVersion)
                || !Objects.equals(existing.getGuestToolsDistroName(), guestToolsDistroName)
                || !Objects.equals(existing.getGuestToolsDistroVersion(), guestToolsDistroVersion)
                || !Objects.equals(existing.getGuestToolsFamilyName(), guestToolsFamilyName)
                || !Objects.equals(existing.getGuestToolsKernelVersion(), guestToolsKernelVersion)
                || !Objects.equals(existing.getGuestToolsPrettyName(), guestToolsPrettyName)
                || !Objects.equals(existing.getHotPlugMemoryLimit(), hotPlugMemoryLimit)
                || !Objects.equals(existing.getHotPlugMemoryIncrementSize(), hotPlugMemoryIncrementSize)
                || !Objects.equals(existing.getDn(), dn)
                || !Objects.equals(existing.getAssociation(), association)
                || !Objects.equals(existing.getMemorySpeed(), memorySpeed)
                || !Objects.equals(existing.getModel(), model)
                || !Objects.equals(existing.getNumOfAdaptors(), numOfAdaptors)
                || !Objects.equals(existing.getNumOfCoresEnabled(), numOfCoresEnabled)
                || !Objects.equals(existing.getNumOfEthHostIfs(), numOfEthHostIfs)
                || !Objects.equals(existing.getNumOfFcHostIfs(), numOfFcHostIfs)
                || !Objects.equals(existing.getOperState(), operState)
                || !Objects.equals(existing.getUcsmChassisId(), chassisId)
                || !Objects.equals(existing.getUcsmChassisSlotId(), slotId)
                || !Objects.equals(existing.getUcsmServerId(), serverId)
                || !Objects.equals(existing.getVendor(), vendor)
                || !Objects.equals(existing.getVid(), vid)
                || !Objects.equals(existing.getServerKind(), serverKind)
                || !Objects.equals(existing.getServerType(), serverType)
                || !Objects.equals(existing.getMemoryAllocationExpandableReservation(), memoryAllocationExpandableReservation)
                || !Objects.equals(existing.getMemoryAllocationLimit(), memoryAllocationLimit)
                || !Objects.equals(existing.getMemoryAllocationOverheadLimit(), memoryAllocationOverheadLimit)
                || !Objects.equals(existing.getMemoryAllocationReservation(), memoryAllocationReservation)
                || !Objects.equals(existing.getCpuAllocationExpandableReservation(), cpuAllocationExpandableReservation)
                || !Objects.equals(existing.getCpuAllocationLimit(), cpuAllocationLimit)
                || !Objects.equals(existing.getCpuAllocationOverheadLimit(), memoryAllocationOverheadLimit)
                || !Objects.equals(existing.getCpuAllocationReservation(), cpuAllocationReservation);
    }

    /**
     * Applies the DTO's data to an existing server entity.
     *
     * @param existing The entity to be modified.
     */
    public void applyChanges(final Server existing) {
        existing.setName(name);
        existing.setInstanceUuid(instanceUuid);
        existing.setVmId(vmId);
        existing.setCluster(cluster);
        existing.setHost(host);
        existing.setLocation(location);
        existing.setPowerState(powerState);

        if (!Objects.equals(existing.getMemoryMb(), memoryMB)) {
            if (existing.getMemoryMbChangeDate() != null) {
                existing.setMemoryMbChangeDatePrev(existing.getMemoryMbChangeDate());
            }
            existing.setMemoryMbChangeDate(OffsetDateTime.now());
            existing.setMemoryMbPrev(existing.getMemoryMb());
            existing.setMemoryMb(memoryMB);
        }

        if (!Objects.equals(existing.getNumCpu(), numCPU)) {
            if (existing.getNumCpuChangeDate() != null) {
                existing.setNumCpuChangeDatePrev(existing.getNumCpuChangeDate());
            }
            existing.setNumCpuChangeDate(OffsetDateTime.now());
            existing.setNumCpuPrev(existing.getNumCpu());
            existing.setNumCpu(numCPU);
        }

        existing.setNumCoresPerSocket(numCoresPerSocket);
        existing.setMemoryHotAddEnabled(Boolean.TRUE.equals(memoryHotAddEnabled));
        existing.setCpuHotAddEnabled(Boolean.TRUE.equals(cpuHotAddEnabled));
        existing.setCpuHotRemoveEnabled(Boolean.TRUE.equals(cpuHotRemoveEnabled));
        existing.setCpuTopology(cpuTopology);
        existing.setVmxVersion(vmxVersion);
        existing.setOverallStatus(overallStatus);
        existing.setConfigStatus(configStatus);
        existing.setGuestConfigId(guestConfigId);
        existing.setGuestConfigFullName(guestConfigFullName);
        existing.setGuestToolsId(guestToolsId);
        existing.setGuestToolsFullName(guestToolsFullName);
        existing.setGuestToolsState(guestToolsState);
        existing.setGuestToolsRunningStatus(guestToolsRunningStatus);
        existing.setGuestToolsVersionStatus(guestToolsVersionStatus);
        existing.setGuestToolsVersionStatus2(guestToolsVersionStatus2);
        existing.setGuestToolsInstallType(guestToolsInstallType);
        existing.setGuestToolsVersion(guestToolsVersion);
        existing.setGuestToolsFamily(guestToolsFamily);
        existing.setGuestToolsHostname(guestToolsHostname);
        existing.setGuestToolsIpAddress(guestToolsIpAddress);
        existing.setGuestToolsArchitecture(guestToolsArchitecture);
        existing.setGuestToolsBitness(guestToolsBitness);
        existing.setGuestToolsBuildNumber(guestToolsBuildNumber);
        existing.setGuestToolsCpeString(guestToolsCpeString);
        existing.setGuestToolsDistroAddlVersion(guestToolsDistroAddlVersion);
        existing.setGuestToolsDistroName(guestToolsDistroName);
        existing.setGuestToolsDistroVersion(guestToolsDistroVersion);
        existing.setGuestToolsFamilyName(guestToolsFamilyName);
        existing.setGuestToolsKernelVersion(guestToolsKernelVersion);
        existing.setGuestToolsPrettyName(guestToolsPrettyName);
        existing.setBootTime(bootTime);
        existing.setHotPlugMemoryLimit(hotPlugMemoryLimit);
        existing.setHotPlugMemoryIncrementSize(hotPlugMemoryIncrementSize);
        existing.setDn(dn);
        existing.setAssociation(association);
        existing.setMemorySpeed(memorySpeed);
        existing.setMfgTime(mfgTime);
        existing.setModel(model);
        existing.setNumOfAdaptors(numOfAdaptors);
        existing.setNumOfCoresEnabled(numOfCoresEnabled);
        existing.setNumOfEthHostIfs(numOfEthHostIfs);
        existing.setNumOfFcHostIfs(numOfFcHostIfs);
        existing.setOperState(operState);
        existing.setUcsmChassisId(chassisId);
        existing.setUcsmChassisSlotId(slotId);
        existing.setUcsmServerId(serverId);
        existing.setMemoryMbAvailable(availableMemory);
        existing.setVendor(vendor);
        existing.setVid(vid);
        existing.setServerKind(serverKind);
        existing.setServerType(serverType);
        existing.setMemoryAllocationExpandableReservation(memoryAllocationExpandableReservation);
        existing.setMemoryAllocationReservation(memoryAllocationReservation);
        existing.setMemoryAllocationLimit(memoryAllocationLimit);
        existing.setMemoryAllocationOverheadLimit(memoryAllocationOverheadLimit);
        existing.setCpuAllocationExpandableReservation(cpuAllocationExpandableReservation);
        existing.setCpuAllocationLimit(cpuAllocationLimit);
        existing.setCpuAllocationOverheadLimit(cpuAllocationOverheadLimit);
        existing.setCpuAllocationReservation(cpuAllocationReservation);
    }

    /**
     * Builds a new server entity which can be persisted to the
     * Database.
     *
     * @param cloud The cloud entity the server belongs to.
     * @return A new server entity.
     */
    public Server build(final Cloud cloud) {
        final Server server = new Server();
        server.setCloud(cloud);
        server.setUuid(uuid);

        if (cloud.getCloudType() == CloudType.UCS_CIMC) {
            server.setFqdn(normalizeFQDN(cloud.getApiEndpoint()));
        } else {
            server.setFqdn(name);
        }

        applyChanges(server);
        return server;
    }

    /**
     * Returns a normalized name by removing any single-character
     * suffixes from the shortname.
     *
     * <p>
     * Example: <code>cwik102m.example.org</code> becomes
     * <code>dcwik102.example.org</code>
     * </p>
     */
    public static String normalizeFQDN(String fqdn) {
        int dotIndex = fqdn.indexOf('.');
        if (dotIndex == -1)
            return fqdn; // no domain, return as is

        String hostname = fqdn.substring(0, dotIndex);
        String domain = fqdn.substring(dotIndex);

        if (!Character.isDigit(hostname.charAt(dotIndex - 1))) {
            return hostname.substring(0, hostname.length() - 1) + domain;
        } else {
            return fqdn;
        }
    }
}
