package de.muenchen.mcmp.clients.greenit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record SendMailRequestDTO(
        @JsonProperty("dateTime")
        @NotNull
        @JsonDeserialize(using = BerlinDateTimeDeserializer.class)
        OffsetDateTime startTime
) {
}
