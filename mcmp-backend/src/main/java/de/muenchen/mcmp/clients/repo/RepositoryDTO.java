package de.muenchen.mcmp.clients.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record RepositoryDTO(
        MetadataDTO metadata,
        List<RepositoryEntryDTO> repositories
) {

    @Builder
    public record MetadataDTO(
            String name,
            String version,
            @JsonProperty("commit_id")
            String commitId,
            @JsonProperty("commit_time")
            OffsetDateTime commitTime,
            boolean modified,
            @JsonProperty("go_version")
            String goVersion,
            String fqdn,
            @JsonProperty("start_time")
            OffsetDateTime startTime,
            @JsonProperty("end_time")
            OffsetDateTime endTime,
            String duration,
            String status
    ) {
    }

    @Builder
    public record RepositoryEntryDTO(
            @JsonProperty("name")
            String name,
            @JsonProperty("url")
            String url
    ) {
    }
}