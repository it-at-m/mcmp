package de.muenchen.mcmp.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record MetadataDTO(
        @JsonProperty("name")
        String name,

        @JsonProperty("version")
        String version,

        @JsonProperty("commit_id")
        String commitId,

        @JsonProperty("commit_time")
        OffsetDateTime commitTime,

        @JsonProperty("modified")
        boolean modified,

        @JsonProperty("go_version")
        String goVersion,

        @JsonProperty("fqdn")
        String fqdn,

        @JsonProperty("start_time")
        OffsetDateTime startTime,

        @JsonProperty("end_time")
        OffsetDateTime endTime,

        @JsonProperty("duration")
        String duration,

        @JsonProperty("status")
        String status
) {
}
