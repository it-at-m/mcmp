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
        Boolean oracleDb,
        @JsonProperty("mssql_db")
        Boolean mssqlDb,
        @JsonProperty("maria_db")
        Boolean mariaDb,
        @JsonProperty("mysql_db")
        Boolean mysqlDb,
        @JsonProperty("mongo_db")
        Boolean mongoDb,
        @JsonProperty("adabas_db")
        Boolean adabasDb,
        @JsonProperty("postgres_db")
        Boolean postgresDb,
        Boolean linux,
        Boolean windows,
        @JsonProperty("tetration_agent_is_installed")
        Boolean tetrationAgentIsInstalled,
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
        List<String> repositories,
        @JsonProperty("lhm_managed")
        List<String> lhmManaged
) {
    public HostDTO {
        if (oracleDb == null) oracleDb = false;
        if (mssqlDb == null) mssqlDb = false;
        if (mariaDb == null) mariaDb = false;
        if (mysqlDb == null) mysqlDb = false;
        if (mongoDb == null) mongoDb = false;
        if (adabasDb == null) adabasDb = false;
        if (postgresDb == null) postgresDb = false;
        if (linux == null) linux = false;
        if (windows == null) windows = false;
        if (tetrationAgentIsInstalled == null) tetrationAgentIsInstalled = false;
    }
}


