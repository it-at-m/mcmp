package de.muenchen.mcmp.server;

import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;
import lombok.Builder;
import jakarta.validation.constraints.NotNull;

@Builder
public record ServerListDTO(
        @NotNull Long id,
        @NotNull String name,
        @NotNull String powerState,
        String os,
        ServerKind serverKind,
        ServerType serverType,
        boolean hasWarnings
) {
}
