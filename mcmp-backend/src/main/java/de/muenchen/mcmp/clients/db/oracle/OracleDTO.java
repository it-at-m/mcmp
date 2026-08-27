package de.muenchen.mcmp.clients.db.oracle;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.muenchen.mcmp.metadata.MetadataDTO;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record OracleDTO(
        MetadataDTO metadata,
        List<DatabaseEntryDTO> databases
) {

    @Builder
    public record DatabaseEntryDTO(
            String fqdn,
            String pdb,
            OffsetDateTime timestamp,
            List<QueryDataDTO> data
    ) {
    }

    @Builder
    public record QueryDataDTO(
            String queryName,
            List<JsonNode> rows
    ) {
    }

    @Builder
    public record InstanceInfoRowDTO(
            String characterset,
            @JsonProperty("database_type")
            String databaseType,
            @JsonProperty("host_name")
            String hostName,
            @JsonProperty("pdb_name")
            String pdbName,
            @JsonProperty("startup_time")
            String startupTime
    ) {
    }

    @Builder
    public record UserInfoRowDTO(
            @JsonProperty("account_status")
            String accountStatus,
            @JsonProperty("last_login")
            String lastLogin,
            String profile,
            String tablespaces,
            @JsonProperty("user_name")
            String userName
    ) {
    }

    @Builder
    public record TablespaceInfoRowDTO(
            @JsonProperty("data_max_in_b")
            Long dataMaxInB,
            @JsonProperty("data_used_in_b")
            Long dataUsedInB,
            @JsonProperty("tablespace_name")
            String tablespaceName,
            @JsonProperty("tablespace_type")
            String tablespaceType
    ) {
    }
}