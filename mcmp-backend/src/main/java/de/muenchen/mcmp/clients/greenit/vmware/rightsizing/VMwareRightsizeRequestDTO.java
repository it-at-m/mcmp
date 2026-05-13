package de.muenchen.mcmp.clients.greenit.vmware.rightsizing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.muenchen.mcmp.clients.greenit.BerlinDateTimeDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

public record VMwareRightsizeRequestDTO(
        @JsonProperty("cpu")
        @NotNull
        @Positive
        Integer cpu,

        @JsonProperty("ram")
        @NotNull
        @Positive
        Integer ram,

        @JsonProperty("dateTime")
        @NotNull
        @JsonDeserialize(using = BerlinDateTimeDeserializer.class)
        OffsetDateTime startTime,

        @JsonProperty("server_uuid")
        @NotBlank
        String serverUuid,

        @JsonProperty("vKenner")
        @NotBlank
        String vcenterShortCode
) {
}
