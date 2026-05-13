package de.muenchen.mcmp.clients.greenit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GreenITResponseDTO(
        @JsonProperty("job_id")
        Long jobId,

        @JsonProperty("message")
        String message
) {
}
