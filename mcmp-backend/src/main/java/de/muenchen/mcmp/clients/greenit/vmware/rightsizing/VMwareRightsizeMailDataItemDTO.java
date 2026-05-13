package de.muenchen.mcmp.clients.greenit.vmware.rightsizing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VMwareRightsizeMailDataItemDTO(
        @JsonProperty("vmName")
        String vmName,

        @JsonProperty("startTime")
        String startTime,

        @JsonProperty("currentCPU")
        Integer currentCPU,

        @JsonProperty("newCPU")
        Integer newCPU,

        @JsonProperty("currentRAM")
        Integer currentRAM,

        @JsonProperty("newRAM")
        Integer newRAM,

        @JsonProperty("service")
        String service,

        @JsonProperty("changeNr")
        String changeNr,

        @JsonProperty("status")
        String status
) {
}