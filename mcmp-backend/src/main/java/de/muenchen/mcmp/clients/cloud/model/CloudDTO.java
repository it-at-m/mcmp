package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record CloudDTO(
        @JsonProperty("cloud") String cloud,
        @JsonProperty("cloud_type") String cloudType,
        @JsonProperty("servers") List<ServerDTO> servers
) {
}
