package de.muenchen.mcmp.clients.greenit.vmware.rightsizing;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RightsizingRecommendationsDTO(
    @JsonProperty("id")
    @NotNull
    long id,

    @JsonProperty("name")
    String name,

    @JsonProperty("currentCPU")
    Integer currentCPU,

    @JsonProperty("newCPU")
    Integer newCPU,

    @JsonProperty("currentRAM")
    Integer currentRAM,

    @JsonProperty("newRAM")
    Integer newRAM
) {}
