package de.muenchen.mcmp.clients.greenit.vmware.rightsizing;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RightsizingServerDTO(
        @JsonProperty("id")
        @NotNull
        long id,

        @JsonProperty("num_cpu")
        int numCpu,

        @JsonProperty("memory_mb")
        int memoryMb)
{
}
