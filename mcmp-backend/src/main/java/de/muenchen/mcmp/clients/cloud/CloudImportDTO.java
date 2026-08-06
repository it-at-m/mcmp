package de.muenchen.mcmp.clients.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.server.ServerStatusType;
import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Builder
public record CloudImportDTO(
        @JsonProperty("cloud") String cloud,
        @JsonProperty("cloud_type") String cloudType,
        @JsonProperty("servers") List<Server> servers
) {
    public record Server(
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
            @JsonProperty("snapshots") List<Snapshot> snapshots
    ) {
        public Server {
            /* ensure we have a trimmed name */
            if (name == null || name.isBlank()) {
                name = uuid;
            } else {
                name = name.trim();
            }

            /* ensure we have a valid power state */
            switch (powerState) {
                case "poweredOn": // vmware
                case "running":   // proxmox
                case "on":        // ucs
                case "up":        // olvm
                    powerState = "poweredOn";
                    break;
                case "poweredOff": // vmware
                case "stopped":    // proxmox
                case "off":        // ucs
                case "down":       // olvm
                    powerState = "poweredOff";
                    break;
                default:
                    powerState = "unknown";
                    break;
            }

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
            if (snapshots == null) snapshots = Collections.emptyList();
        }
    }
