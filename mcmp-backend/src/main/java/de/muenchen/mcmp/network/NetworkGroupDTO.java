package de.muenchen.mcmp.network;

import de.muenchen.mcmp.appservice.AppserviceSummaryDTO;
import de.muenchen.mcmp.types.EnvironmentType;
import lombok.Builder;

import java.util.Set;

@Builder
public record NetworkGroupDTO(
        Long id,
        String name,
        boolean application,
        boolean database,
        boolean storage,
        boolean restrict,
        Set<AppserviceSummaryDTO> appservices,
        EnvironmentType environment
) {
}
