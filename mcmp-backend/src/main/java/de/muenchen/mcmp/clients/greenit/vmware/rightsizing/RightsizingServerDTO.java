package de.muenchen.mcmp.clients.greenit.vmware.rightsizing;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record RightsizingServerDTO(
        @JsonProperty("id")
        @NotNull
        Long id,

        @JsonProperty("num_cpu")
        @NotNull
        Integer numCpu,

        @JsonProperty("memory_mb")
        @NotNull
        Integer memoryMb)
{
}
