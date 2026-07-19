package de.muenchen.mcmp.appservice;

import de.muenchen.mcmp.types.EnvironmentType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record AppserviceListDTO(
        @NotNull Long id,
        @NotNull String name,
        @NotNull Boolean hasServers,
        EnvironmentType environment,
        boolean isFavorite
) {
}
