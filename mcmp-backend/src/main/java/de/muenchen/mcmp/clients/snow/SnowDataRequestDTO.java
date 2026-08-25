package de.muenchen.mcmp.clients.snow;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
public record SnowDataRequestDTO(

        @JsonProperty("eai_info")
        @Valid
        EaiInfoDTO eaiInfo,

        @JsonProperty("users")
        @Valid
        List<UserDTO> users,

        @JsonProperty("groups")
        @Valid
        List<GroupDTO> groups,

        @JsonProperty("cis")
        @Valid
        List<CIDTO> cmdbCIs,

        @JsonProperty("app_services")
        @Valid
        List<AppServiceDTO> appServices,

        @JsonProperty("storage_server")
        @Valid
        List<StorageServerDTO> storageServers,

        @JsonProperty("storage_volumes")
        @Valid
        List<StorageVolumeDTO> storageVolumes,

        @JsonProperty("storage_qtrees")
        @Valid
        List<StorageQTreeDTO> storageQTrees,

        @JsonProperty("storage_accounts")
        @Valid
        List<StorageAccountDTO> storageAccounts,

        @JsonProperty("storage_buckets")
        @Valid
        List<StorageBucketDTO> storageBuckets,

        @JsonProperty("lb_services")
        @Valid
        List<LbServiceDTO> lbServices,

        @JsonProperty("kubernetes_clusters")
        @Valid
        List<KubernetesClusterDTO> kubernetesClusters,

        @JsonProperty("package_repositories")
        @Valid
        List<PackageRepositoryDTO> packageRepositories,

        @JsonProperty("database_instances")
        @Valid
        List<DatabaseInstanceDTO> databaseInstances,

        @JsonProperty("database_pdb_instances")
        @Valid
        List<DatabasePdbInstanceDTO> databasePdbInstances
) {

    @Builder
    public record EaiInfoDTO(
            @JsonProperty("name") String name,
            @JsonProperty("version") String version,
            @JsonProperty("commit_id") String commitId,
            @JsonProperty("modified") Boolean modified,
            @JsonProperty("go_version") String goVersion,
            @JsonProperty("fqdn") String fqdn,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            @JsonProperty("duration") String duration,
            @JsonProperty("status") String status
    ) {}

    @Builder
    public record UserDTO(
            @JsonProperty("sys_id")
            @NotNull
            String sysId,

            @JsonProperty("user_id")
            @NotNull
            String userId,

            @JsonProperty("department")
            String department,

            @JsonProperty("name")
            String name,

            @JsonProperty("email")
            String email
    ) {
    }

    @Builder
    public record GroupDTO(
            @JsonProperty("sys_id")
            @NotNull
            String sysId,

            @JsonProperty("name")
            @NotNull
            String name,

            @JsonProperty("manager")
            String manager,

            @JsonProperty("members")
            List<String> members
    ) {
    }

    @Builder
    public record CIDTO(
            @JsonProperty("name")
            String name,

            @JsonProperty("sys_id")
            @NotNull
            String sysId,

            @JsonProperty("serial_number")
            String serialNumber,

            @JsonProperty("sys_class")
            String sysClassName,

            @JsonProperty("ip_address")
            String ipAddress,

            @JsonProperty("fqdn")
            String fqdn,

            @JsonProperty("os")
            String os,

            @JsonProperty("os_version")
            String osVersion,

            @JsonProperty("hardware_status")
            String hardwareStatus,

            @JsonProperty("last_discovered")
            String lastDiscovered,

            @JsonProperty("vm_instance_uuid")
            String vmInstanceUUID,

            @JsonProperty("mac_address")
            String macAddress,

            @JsonProperty("server_sys_id")
            String serverSysId,

            @JsonProperty("locked_shutdown")
            Boolean lockedShutdown,

            @JsonProperty("shutdown_task_closed_at")
            String shutdownTaskClosedAt,

            @JsonProperty("locked_rightsize")
            Boolean lockedRightsize,

            @JsonProperty("rightsize_task_closed_at")
            String rightsizeTaskClosedAt
    ) {
    }

    @Builder
    public record AppServiceDTO(
            @JsonProperty("sys_id")
            @NotNull
            String sysId,

            @JsonProperty("name")
            @NotNull
            String name,

            @JsonProperty("number")
            String number,

            @JsonProperty("group")
            String group,

            @JsonProperty("used_for")
            String usedFor,

            @JsonProperty("environment")
            String environment,

            @JsonProperty("csw_enforced")
            Boolean cswEnforced,

            @JsonProperty("owned_by")
            String ownedBy,

            @JsonProperty("service_owner_delegate")
            String serviceOwnerDelegate,

            @JsonProperty("business_service_numbers")
            List<String> businessServiceNumbers,

            @JsonProperty("server_cis")
            List<String> cis
    ) {
        public AppServiceDTO {
            if (cswEnforced == null) cswEnforced = false;
        }
    }

    @Builder
    public record StorageServerDTO(
            @JsonProperty("name")
            String name,

            @JsonProperty("sys_id")
            @NotNull
            String sysId,

            @JsonProperty("serial_number")
            String serialNumber,

            @JsonProperty("sys_class")
            String sysClass,

            @JsonProperty("life_cycle_stage")
            String lifeCycleStage,

            @JsonProperty("life_cycle_stage_status")
            String lifeCycleStageStatus,

            @JsonProperty("last_discovered")
            String lastDiscovered
    ) {
    }

    @Builder
    public record StorageVolumeDTO(
            @JsonProperty("name")
            String name,

            @JsonProperty("sys_id")
            @NotNull
            String sysId,

            @JsonProperty("sys_class")
            String sysClass,

            @JsonProperty("life_cycle_stage")
            String lifeCycleStage,

            @JsonProperty("life_cycle_stage_status")
            String lifeCycleStageStatus,

            @JsonProperty("last_discovered")
            String lastDiscovered,

            @JsonProperty("volume_id")
            String volumeId,

            @JsonProperty("svm_uuid")
            String svmUUID,

            @JsonProperty("svm_sys_id")
            String svmSysId,

            @JsonProperty("app_service_number")
            List<String> appServiceNumber
    ) {
    }

    @Builder
    public record StorageQTreeDTO(
            @JsonProperty("name")
            String name,

            @JsonProperty("sys_id")
            @NotNull
            String sysId,

            @JsonProperty("sys_class")
            String sysClass,

            @JsonProperty("life_cycle_stage")
            String lifeCycleStage,

            @JsonProperty("life_cycle_stage_status")
            String lifeCycleStageStatus,

            @JsonProperty("last_discovered")
            String lastDiscovered,

            @JsonProperty("storage_type")
            String storageType,

            @JsonProperty("volume_id")
            String volumeId,

            @JsonProperty("qtree_id")
            String qtreeId,

            @JsonProperty("cluster_id")
            String clusterId,

            @JsonProperty("object_id")
            String objectId,

            @JsonProperty("svm_uuid")
            String svmUUID,

            @JsonProperty("svm_sys_id")
            String svmSysId,

            @JsonProperty("parent_id")
            String parentId,

            @JsonProperty("app_service_number")
            List<String> appServiceNumber
    ) {
    }

    @Builder
    public record StorageAccountDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("account_id") String accountId,
            @JsonProperty("app_service_number") List<String> appServiceNumber
    ) {}

    @Builder
    public record StorageBucketDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("account_id") String accountId,
            @JsonProperty("app_service_number") List<String> appServiceNumber
    ) {}

    @Builder
    public record LbServiceDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("last_discovered") String lastDiscovered,
            @JsonProperty("app_service_number") List<String> appServiceNumber
    ) {}

    @Builder
    public record KubernetesClusterDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("last_discovered") String lastDiscovered,
            @JsonProperty("k8s_uid") String k8sUid,
            @JsonProperty("environment") String environment,
            @JsonProperty("kubernetes_namespaces") List<KubernetesNamespaceDTO> kubernetesNamespaces
    ) {}

    @Builder
    public record KubernetesNamespaceDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("last_discovered") String lastDiscovered,
            @JsonProperty("k8s_uid") String k8sUid,
            @JsonProperty("environment") String environment,
            @JsonProperty("app_service_number") List<String> appServiceNumber
    ) {}

    @Builder
    public record PackageRepositoryDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("last_discovered") String lastDiscovered,
            @JsonProperty("app_service_number") List<String> appServiceNumber
    ) {}

    @Builder
    public record DatabaseInstanceDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("last_discovered") String lastDiscovered,
            @JsonProperty("version") String version,
            @JsonProperty("server_sys_id") List<String> serverSysIds,
            @JsonProperty("app_service_number") List<String> appServiceNumber
    ) {}

    @Builder
    public record DatabasePdbInstanceDTO(
            @JsonProperty("name") String name,
            @JsonProperty("sys_id") @NotNull String sysId,
            @JsonProperty("sys_class") String sysClass,
            @JsonProperty("last_discovered") String lastDiscovered,
            @JsonProperty("sid") String sid,
            @JsonProperty("db_instance_sys_id") List<String> databaseInstanceSysIds,
            @JsonProperty("app_service_number") List<String> appServiceNumber
    ) {}
}
