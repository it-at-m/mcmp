package de.muenchen.mcmp.clients.greenit.vmware.shutdown;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VMwareShutdownMailResponseDTO(
        @JsonProperty("message")
        String message,

        @JsonProperty("data")
        List<VMwareShutdownMailDataItemDTO> data
) {
}
