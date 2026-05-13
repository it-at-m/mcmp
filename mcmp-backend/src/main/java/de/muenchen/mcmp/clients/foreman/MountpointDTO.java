package de.muenchen.mcmp.clients.foreman;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record MountpointDTO(
        @JsonProperty("mount_point")
        String mountPoint,
        String filesystem,
        String device,
        List<String> options,
        @JsonProperty("size_bytes")
        Long sizeBytes,
        @JsonProperty("used_bytes")
        Long usedBytes,
        @JsonProperty("available_bytes")
        Long availableBytes
) {}
