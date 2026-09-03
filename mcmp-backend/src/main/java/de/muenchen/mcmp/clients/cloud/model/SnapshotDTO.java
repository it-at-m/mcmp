package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record SnapshotDTO(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("create_time") OffsetDateTime createTime,
        @JsonProperty("quiesced") Boolean quiesced,
        @JsonProperty("state") String state,
        @JsonProperty("replay_supported") Boolean replaySupported
) {
    public SnapshotDTO {
        // supply database defaults for missing values
        if (quiesced == null) quiesced = false;
        if (replaySupported == null) replaySupported = false;
    }
}
