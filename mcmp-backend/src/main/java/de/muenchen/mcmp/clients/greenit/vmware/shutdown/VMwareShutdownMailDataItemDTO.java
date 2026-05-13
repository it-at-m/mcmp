package de.muenchen.mcmp.clients.greenit.vmware.shutdown;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VMwareShutdownMailDataItemDTO(
        @JsonProperty("vmName")
        String vmName,

        @JsonProperty("startTime")
        String startTime,

        @JsonProperty("currentCPU")
        Integer currentCPU,

        @JsonProperty("currentRAM")
        Integer currentRAM,

        @JsonProperty("service")
        String service,

        @JsonProperty("changeNr")
        String changeNr,

        @JsonProperty("status")
        String status
) {
}