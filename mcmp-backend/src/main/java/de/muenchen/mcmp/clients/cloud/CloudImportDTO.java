package de.muenchen.mcmp.clients.cloud;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record CloudImportDTO(
        @JsonProperty("cloud") String cloud,
        @JsonProperty("cloud_type") String cloudType,
        @JsonProperty("servers") List<Server> servers
) {
    public record Server(
            @JsonProperty("server_kind") String serverKind,
            @JsonProperty("server_type") String serverType,
            @JsonProperty("name") String name,
            @JsonProperty("uuid") String uuid,
            @JsonProperty("instance_uuid") String instanceUuid,
            @JsonProperty("vm_id") String vmId,
            @JsonProperty("cluster") String cluster,
            @JsonProperty("host") String host,
            @JsonProperty("location") String location,
            @JsonProperty("power_state") String powerState,
            @JsonProperty("memory_mb") Long memoryMB,
            @JsonProperty("num_cpu") Integer numCPU,
            @JsonProperty("num_cores_per_socket") Integer numCoresPerSocket,
            @JsonProperty("num_of_threads") Integer num_of_threads,
            @JsonProperty("memory_hot_add_enabled") Boolean memoryHotAddEnabled,
            @JsonProperty("cpu_hot_add_enabled") Boolean cpuHotAddEnabled,
            @JsonProperty("cpu_hot_remove_enabled") Boolean cpuHotRemoveEnabled,
            @JsonProperty("cpu_topology") String cpuTopology,
            @JsonProperty("vmx_version") String vmxVersion,
            @JsonProperty("overall_status") String overallStatus,
            @JsonProperty("config_status") String configStatus,
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
            @JsonProperty("boot_time") String bootTime,
            @JsonProperty("hot_plug_memory_limit") Long hotPlugMemoryLimit,
            @JsonProperty("hot_plug_memory_increment_size") Long hotPlugMemoryIncrementSize,
            @JsonProperty("dn") String dn,
            @JsonProperty("association") String association,
            @JsonProperty("memory_speed") Integer memorySpeed,
            @JsonProperty("mfg_time") String mfgTime,
            @JsonProperty("model") String model,
            @JsonProperty("num_of_adaptors") Integer numOfAdaptors,
            @JsonProperty("num_of_cores_enabled") Integer numOfCoresEnabled,
            @JsonProperty("num_of_eth_host_ifs") Integer numOfEthHostIfs,
            @JsonProperty("num_of_fc_host_ifs") Integer numOfFcHostIfs,
            @JsonProperty("oper_state") String operState,
            @JsonProperty("ucsm_chassis_id") Integer chassisId,
            @JsonProperty("ucsm_chassis_slot_id") Integer slotId,
            @JsonProperty("ucsm_server_id") Integer serverId,
            @JsonProperty("available_memory") Long availableMemory,
            @JsonProperty("vendor") String vendor,
            @JsonProperty("vid") String vid
    ) {
    }
}
