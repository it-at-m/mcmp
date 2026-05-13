package de.muenchen.mcmp.clients.foreman;

import lombok.Builder;

import java.util.List;

@Builder
public record ForemanDataDTO(
        List<HostDTO> hosts
) {}

