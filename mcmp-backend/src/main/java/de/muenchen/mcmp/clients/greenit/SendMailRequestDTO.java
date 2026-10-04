package de.muenchen.mcmp.clients.greenit;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record SendMailRequestDTO(
        @JsonProperty("dateTime")
        @NotNull
        @JsonDeserialize(using = BerlinDateTimeDeserializer.class)
        OffsetDateTime startTime
) {
}
