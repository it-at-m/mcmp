package de.muenchen.mcmp.clients.snow;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

@Builder
public record SnowDataRequestDTO(
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
        List<AppServiceDTO> appServices
) {

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

            @JsonProperty("sys_class_name")
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
            boolean cswEnforced,

            @JsonProperty("owned_by")
            String ownedBy,

            @JsonProperty("service_owner_delegate")
            String serviceOwnerDelegate,

            @JsonProperty("business_service_numbers")
            List<String> businessServiceNumbers,

            @JsonProperty("cis")
            List<String> cis
    ) {
    }
}