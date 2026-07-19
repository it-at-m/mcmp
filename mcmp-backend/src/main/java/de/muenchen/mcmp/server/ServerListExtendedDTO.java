package de.muenchen.mcmp.server;

import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record ServerListExtendedDTO(
        @NotNull Long id,
        @NotNull String name,
        @NotNull String powerState,
        String os,
        String appserviceNames,
        Integer numCpu,
        Integer memoryMb,
        Long vdisksCapacityInBytes,
        ServerKind serverKind,
        ServerType serverType,
        Boolean managed,
        Boolean canEdit,
        Boolean hasWarnings
) {
}
