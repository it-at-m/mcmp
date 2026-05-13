package de.muenchen.mcmp.clients.patchnight;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record PatchnightDataDTO(
        @JsonProperty("server")
        @Valid
        List<ServerDTO> servers
) {

    @Builder
    public record  ServerDTO (

        @JsonProperty("env")
        String environment,

        @NotNull
        @JsonProperty("name")
        String name,

        @NotNull
        @JsonProperty("include")
        Boolean include,

        @JsonProperty("start_date")
        OffsetDateTime startDate,

        @JsonProperty("end_date")
        OffsetDateTime endDate,

        @JsonProperty("exitcode")
        Short exitcode,

        @JsonProperty("exitstring")
        String exitstring
    ) {
    }
}




