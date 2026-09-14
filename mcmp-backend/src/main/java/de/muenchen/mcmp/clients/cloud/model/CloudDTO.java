package de.muenchen.mcmp.clients.cloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Builder
public record CloudDTO(
        @JsonProperty("cloud") String cloud,
        @JsonProperty("cloud_type") String cloudType,
        @JsonProperty("servers") List<ServerDTO> servers
) {
    public CloudDTO {
        Objects.requireNonNull(cloud);
        Objects.requireNonNull(cloudType);
        servers = Objects.requireNonNullElseGet(servers, Collections::emptyList);
    }

    /**
     * Builds a <code>cloud.CloudDTO</code> suitable for persisting via
     * <code>cloud.CloudService</code>.
     *
     * @return A new <code>cloud.CloudDTO</code> object.
     */
    public de.muenchen.mcmp.cloud.CloudDTO build() {
        return de.muenchen.mcmp.cloud.CloudDTO.builder()
                .id(null)
                .name(cloud)
                .fqdn(cloud)
                .serverGui(null)
                .cloudType(cloudType)
                .apiDescription(cloudType + " " + cloud)
                .apiUsername(null)
                .apiPassword(null)
                .apiEndpoint(cloud)
                .enabled(true)
                .locked(false)
                .configInfobloxId(null)
                .configBaasId(null)
                .greenItEnabled(false)
                .build();
    }
}
