package de.muenchen.mcmp.server;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.cloud.Cloud;
import de.muenchen.mcmp.common.AbstractEntity;
import de.muenchen.mcmp.repository.Repository;
import de.muenchen.mcmp.types.EnvironmentType;
import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerRightsizingType;
import de.muenchen.mcmp.types.ServerType;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "server",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_server_foreman_source_id",
                        columnNames = {"foreman_source", "foreman_id"}
                )
        })
@DynamicUpdate
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Server extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "cloud_id", nullable = false)
    @ToString.Exclude
    private Cloud cloud;

    @Column(name = "uuid", nullable = false, columnDefinition = "text")
    private String uuid;

    @Column(name = "instance_uuid", columnDefinition = "text")
    private String instanceUuid;

    @Column(name = "vm_id", columnDefinition = "text")
    private String vmId;

    @Column(name = "cluster", columnDefinition = "text")
    private String cluster;

    @Column(name = "host", columnDefinition = "text")
    private String host;

    @Column(name = "location", columnDefinition = "text")
    private String location;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "power_state", nullable = false, columnDefinition = "text")
    private String powerState;

    @Column(name = "memory_mb", nullable = false)
    private Integer memoryMb;

    @Column(name = "memory_mb_prev")
    private Integer memoryMbPrev;

    @Column(name = "memory_mb_change_date")
    private OffsetDateTime memoryMbChangeDate;

    @Column (name = "memory_mb_change_date_prev")
    private OffsetDateTime memoryMbChangeDatePrev;

    @Generated
    @Column(name = "memory_mb_rightsizing", insertable = false, updatable = false)
    @ColumnTransformer(write = "?::server_rightsizing_type")
    @Enumerated(EnumType.STRING)
    private ServerRightsizingType memoryMbRightsizing;

    @Column(name = "num_cpu", nullable = false)
    private Integer numCpu;

    @Column(name = "num_cpu_prev")
    private Integer numCpuPrev;

    @Column(name = "num_cpu_change_date")
    private OffsetDateTime numCpuChangeDate;

    @Column(name = "num_cpu_change_date_prev")
    private OffsetDateTime numCpuChangeDatePrev;

    @Generated
    @Column(name = "num_cpu_rightsizing", insertable = false, updatable = false)
    @ColumnTransformer(write = "?::server_rightsizing_type")
    @Enumerated(EnumType.STRING)
    private ServerRightsizingType numCpuRightsizing;

    @Column(name = "num_cores_per_socket")
    private Integer numCoresPerSocket;

    @ColumnDefault("false")
    @Column(name = "memory_hot_add_enabled", nullable = false)
    private Boolean memoryHotAddEnabled = false;

    @ColumnDefault("false")
    @Column(name = "cpu_hot_add_enabled", nullable = false)
    private Boolean cpuHotAddEnabled = false;

    @ColumnDefault("false")
    @Column(name = "cpu_hot_remove_enabled", nullable = false)
    private Boolean cpuHotRemoveEnabled = false;

    @Column(name = "cpu_topology", columnDefinition = "text")
    private String cpuTopology;

    @Column(name = "vmx_version", columnDefinition = "text")
    private String vmxVersion;

    @Column(name = "overall_status", nullable = false, length = 6)
    @ColumnTransformer(write = "?::server_status_type")
    private ServerStatusType overallStatus = ServerStatusType.gray;

    @Column(name = "config_status", nullable = false, length = 6)
    @ColumnTransformer(write = "?::server_status_type")
    private ServerStatusType configStatus = ServerStatusType.gray;

    @ColumnDefault("false")
    @Column(name = "config_equals_tools", nullable = false)
    private Boolean configEqualsTools = false;

    @Column(name = "guest_config_id", columnDefinition = "text")
    private String guestConfigId;

    @Column(name = "guest_config_full_name", columnDefinition = "text")
    private String guestConfigFullName;

    @Column(name = "guest_tools_id", columnDefinition = "text")
    private String guestToolsId;

    @Column(name = "guest_tools_full_name", columnDefinition = "text")
    private String guestToolsFullName;

    @Column(name = "guest_tools_state", columnDefinition = "text")
    private String guestToolsState;

    @Column(name = "guest_tools_running_status", columnDefinition = "text")
    private String guestToolsRunningStatus;

    @Column(name = "guest_tools_version_status", columnDefinition = "text")
    private String guestToolsVersionStatus;

    @Column(name = "guest_tools_version_status2", columnDefinition = "text")
    private String guestToolsVersionStatus2;

    @Column(name = "guest_tools_install_type", columnDefinition = "text")
    private String guestToolsInstallType;

    @Column(name = "guest_tools_version", columnDefinition = "text")
    private String guestToolsVersion;

    @Column(name = "guest_tools_family", columnDefinition = "text")
    private String guestToolsFamily;

    @Column(name = "guest_tools_hostname", columnDefinition = "text")
    private String guestToolsHostname;

    @Column(name = "guest_tools_ip_address", columnDefinition = "text")
    private String guestToolsIpAddress;

    @Column(name = "guest_tools_architecture", columnDefinition = "text")
    private String guestToolsArchitecture;

    @Column(name = "guest_tools_bitness", columnDefinition = "text")
    private String guestToolsBitness;

    @Column(name = "guest_tools_build_number", columnDefinition = "text")
    private String guestToolsBuildNumber;

    @Column(name = "guest_tools_cpe_string", columnDefinition = "text")
    private String guestToolsCpeString;

    @Column(name = "guest_tools_distro_addl_version", columnDefinition = "text")
    private String guestToolsDistroAddlVersion;

    @Column(name = "guest_tools_distro_name", columnDefinition = "text")
    private String guestToolsDistroName;

    @Column(name = "guest_tools_distro_version", columnDefinition = "text")
    private String guestToolsDistroVersion;

    @Column(name = "guest_tools_family_name", columnDefinition = "text")
    private String guestToolsFamilyName;

    @Column(name = "guest_tools_kernel_version", columnDefinition = "text")
    private String guestToolsKernelVersion;

    @Column(name = "guest_tools_pretty_name", columnDefinition = "text")
    private String guestToolsPrettyName;

    @ColumnDefault("0")
    @Column(name = "vdisks")
    private Short vdisks;

    @ColumnDefault("0")
    @Column(name = "vdisks_capacity_in_bytes")
    private Long vdisksCapacityInBytes;

    @Column(name = "boot_time")
    private OffsetDateTime bootTime;

    @ColumnDefault("false")
    @Column(name = "role_linux", nullable = false)
    private Boolean roleLinux = false;

    @ColumnDefault("false")
    @Column(name = "role_windows", nullable = false)
    private Boolean roleWindows = false;

    @ColumnDefault("false")
    @Column(name = "role_oracle", nullable = false)
    private Boolean roleOracle = false;

    @ColumnDefault("false")
    @Column(name = "role_non_oracle", nullable = false)
    private Boolean roleNonOracle = false;

    @Column(name = "server_infos_owner_mail", columnDefinition = "text")
    private String serverInfosOwnerMail;

    @Column(name = "server_infos_ticket_no", columnDefinition = "text")
    private String serverInfosTicketNo;

    @ColumnDefault("false")
    @Column(name = "tetration_agent_installed", nullable = false)
    private Boolean tetrationAgentInstalled = false;

    @ColumnDefault("false")
    @Column(name = "managed", nullable = false)
    private Boolean managed = false;

    @Column(name = "fqdn", columnDefinition = "text")
    private String fqdn;

    @Column(name = "foreman_id")
    private Long foremanId;

    @Column(name = "foreman_source", columnDefinition = "text")
    private String foremanSource;

    @ColumnDefault("false")
    @Column(name = "db_oracle", nullable = false)
    private Boolean dbOracle = false;

    @ColumnDefault("false")
    @Column(name = "db_mariadb", nullable = false)
    private Boolean dbMariadb = false;

    @ColumnDefault("false")
    @Column(name = "db_hana", nullable = false)
    private Boolean dbHana = false;

    @ColumnDefault("false")
    @Column(name = "db_mysql", nullable = false)
    private Boolean dbMysql = false;

    @ColumnDefault("false")
    @Column(name = "db_mssql", nullable = false)
    private Boolean dbMssql = false;

    @ColumnDefault("false")
    @Column(name = "db_postgres", nullable = false)
    private Boolean dbPostgres = false;

    @ColumnDefault("false")
    @Column(name = "db_mongodb", nullable = false)
    private Boolean dbMongodb = false;

    @ColumnDefault("false")
    @Column(name = "db_adabas", nullable = false)
    private Boolean dbAdabas = false;

    @ColumnDefault("0")
    @Column(name = "memory_mb_recommended")
    private Integer memoryMbRecommended;

    @ColumnDefault("0")
    @Column(name = "num_cpu_recommended")
    private Integer numCpuRecommended;

    @ManyToMany(mappedBy = "servers")
    @ToString.Exclude
    private Set<Appservice> appservices = new LinkedHashSet<>();

    @ManyToMany(mappedBy = "servers")
    @ToString.Exclude
    private Set<Repository> repositories = new LinkedHashSet<>();

    @Column(name = "snow_server_name", columnDefinition = "text")
    private String snowServerName;

    @Column(name = "snow_server_sys_id", columnDefinition = "text")
    private String snowServerSysId;

    @Column(name = "snow_server_sys_class", columnDefinition = "text")
    private String snowServerSysClass;

    @Column(name = "snow_server_hardware_status", columnDefinition = "text")
    private String snowServerHardwareStatus;

    @Column(name = "snow_server_last_discovered")
    private OffsetDateTime snowServerLastDiscovered;

    @Column(name = "snow_instance_name", columnDefinition = "text")
    private String snowInstanceName;

    @Column(name = "snow_instance_sys_id", columnDefinition = "text")
    private String snowInstanceSysId;

    @Column(name = "snow_instance_sys_class", columnDefinition = "text")
    private String snowInstanceSysClass;

    @Column(name = "snow_instance_last_discovered")
    private OffsetDateTime snowInstanceLastDiscovered;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "patchnight_included", nullable = false)
    private Boolean patchnightIncluded = false;

    @Column(name = "patchnight_environment", columnDefinition = "environment_type")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EnvironmentType patchnightEnvironment;

    @Column(name = "patchnight_group", columnDefinition = "text")
    private String patchnightGroup;

    @Column(name = "patchnight_time", columnDefinition = "text")
    private String patchnightTime;

    @Column(name = "patchnight_start_date")
    private OffsetDateTime patchnightStartDate;

    @Column(name = "patchnight_end_date")
    private OffsetDateTime patchnightEndDate;

    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    @Column(name = "patchnight_exitcode_change_date")
    private OffsetDateTime patchnightExitcodeChangeDate;

    @Column(name = "patchnight_exitcode")
    private Short patchnightExitcode;

    @Column(name = "patchnight_exitstring", columnDefinition = "text")
    private String patchnightExitstring;

    @Column(name = "patchnight_change_number", columnDefinition = "text")
    private String patchnightChangeNumber;

    @Column(name = "patchnight_change_sys_id", columnDefinition = "text")
    private String patchnightChangeSysId;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "locked", nullable = false)
    private Boolean locked = false;

    @Column(name = "operatingsystem", columnDefinition = "text")
    private String operatingsystem;

    @Column(name = "hot_plug_memory_limit")
    private Long hotPlugMemoryLimit;

    @Column(name = "hot_plug_memory_increment_size")
    private Long hotPlugMemoryIncrementSize;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "maintenance_mode", nullable = false)
    private Boolean maintenanceMode = false;

    @Column(name = "maintenance_mode_expires_at")
    private OffsetDateTime maintenanceModeExpiresAt;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "greenit_shutdown_change_pending", nullable = false)
    private Boolean greenItShutdownChangePending = false;

    @Column(name = "greenit_shutdown_change_rejected_date")
    private OffsetDateTime greenItShutdownChangeRejectedDate;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "greenit_rightsizing_change_pending", nullable = false)
    private Boolean greenItRightsizingChangePending = false;

    @Column(name = "greenit_rightsizing_change_rejected_date")
    private OffsetDateTime greenItRightsizingChangeRejectedDate;

    @Column(name = "dn", length = Integer.MAX_VALUE)
    private String dn;

    @Column(name = "association", length = Integer.MAX_VALUE)
    private String association;

    @Column(name = "memory_speed")
    private Integer memorySpeed;

    @Column(name = "memory_mb_available")
    private Integer memoryMbAvailable;

    @Column(name = "mfg_time")
    private OffsetDateTime mfgTime;

    @Column(name = "model", length = Integer.MAX_VALUE)
    private String model;

    @Column(name = "num_of_adaptors")
    private Integer numOfAdaptors;

    @Column(name = "num_of_cores_enabled")
    private Integer numOfCoresEnabled;

    @Column(name = "num_of_eth_host_ifs")
    private Integer numOfEthHostIfs;

    @Column(name = "num_of_fc_host_ifs")
    private Integer numOfFcHostIfs;

    @Column(name = "oper_state", length = Integer.MAX_VALUE)
    private String operState;

    @Column(name = "ucsm_chassis_id")
    private Integer ucsmChassisId;

    @Column(name = "ucsm_chassis_slot_id")
    private Integer ucsmChassisSlotId;

    @Column(name = "ucsm_server_id")
    private Integer ucsmServerId;

    @Column(name = "vendor", length = Integer.MAX_VALUE)
    private String vendor;

    @Column(name = "vid", length = Integer.MAX_VALUE)
    private String vid;

    @ColumnDefault("'UNKNOWN'")
    @Column(name = "server_kind", columnDefinition = "server_kind not null")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ServerKind serverKind = ServerKind.UNKNOWN;

    @ColumnDefault("'UNKNOWN'")
    @Column(name = "server_type", columnDefinition = "server_type not null")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ServerType serverType = ServerType.UNKNOWN;

    @Column(name = "memory_allocation_expandable_reservation")
    private Boolean memoryAllocationExpandableReservation;

    @Column(name = "memory_allocation_limit")
    private Long memoryAllocationLimit;

    @Column(name = "memory_allocation_overhead_limit")
    private Long memoryAllocationOverheadLimit;

    @Column(name = "memory_allocation_reservation")
    private Long memoryAllocationReservation;

    @Column(name = "cpu_allocation_expandable_reservation")
    private Boolean cpuAllocationExpandableReservation;

    @Column(name = "cpu_allocation_limit")
    private Long cpuAllocationLimit;

    @Column(name = "cpu_allocation_overhead_limit")
    private Long cpuAllocationOverheadLimit;

    @Column(name = "cpu_allocation_reservation")
    private Long cpuAllocationReservation;
}
