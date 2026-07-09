package de.muenchen.mcmp.clients.foreman;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.util.List;

@Builder
public record HostDTO(
        Integer id,
        String source,
        String name,
        String fqdn,
        @JsonProperty("display_name")
        String displayName,
        String ip,
        String mac,
        @JsonProperty("architecture_name")
        String architectureName,
        @JsonProperty("operatingsystem_name")
        String operatingsystemName,
        @JsonProperty("operatingsystem_family")
        String operatingsystemFamily,
        @JsonProperty("operatingsystem_major")
        String operatingsystemMajor,
        @JsonProperty("subnet_name")
        String subnetName,
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("initiated_at")
        String initiatedAt,
        @JsonProperty("installed_at")
        String installedAt,
        String serialnumber,
        @JsonProperty("instance_uuid")
        String instanceUuid,
        @JsonProperty("compute_resource_name")
        String computeResourceName,
        List<InterfaceDTO> interfaces,
        @JsonProperty("lhm_pn_exitcode")
        String lhmPnExitcode,
        @JsonProperty("lhm_pn_exitstring")
        String lhmPnExitstring,
        @JsonProperty("oracle_db")
        boolean oracleDb,
        @JsonProperty("mssql_db")
        boolean mssqlDb,
        @JsonProperty("maria_db")
        boolean mariaDb,
        @JsonProperty("mysql_db")
        boolean mysqlDb,
        @JsonProperty("mongo_db")
        boolean mongoDb,
        @JsonProperty("adabas_db")
        boolean adabasDb,
        @JsonProperty("postgres_db")
        boolean postgresDb,
        boolean linux,
        boolean windows,
        @JsonProperty("tetration_agent_is_installed")
        boolean tetrationAgentIsInstalled,
        @JsonProperty("server_infos_owner_mail")
        String serverInfosOwnerMail,
        @JsonProperty("server_infos_ticketnr")
        String serverInfosTicketnr,
        @JsonProperty("patchnight_group")
        String patchnightGroup,
        @JsonProperty("patchnight_start_time")
        String patchnightStartTime,
        @JsonProperty("mysql_db_version")
        String mysqlDbVersion,
        @JsonProperty("maria_db_version")
        String mariaDbVersion,
        @JsonProperty("oracle_db_version")
        String oracleDbVersion,
        @JsonProperty("oracle_sid")
        String oracleSid,
        @JsonProperty("mountpoints")
        List<MountpointDTO> mountpoints,
        @JsonProperty("partitions")
        List<PartitionDTO> partitions,
        @JsonProperty("repositories")
        List<String> repositories
) {}


