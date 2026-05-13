package de.muenchen.mcmp.clients.foreman;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PartitionDTO (
    @JsonProperty("partition")
    String partition,

    @JsonProperty("mount_point")
    String mountPoint,

    @JsonProperty("filesystem")
    String filesystem,

    @JsonProperty("parttype")
    String partType ,

    @JsonProperty("partuuid")
    String partUUID,

    @JsonProperty("size_bytes")
    Long sizeBytes,

    @JsonProperty("uuid")
    String uuid
) {}
