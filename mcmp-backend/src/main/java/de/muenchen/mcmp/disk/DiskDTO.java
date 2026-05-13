package de.muenchen.mcmp.disk;

import lombok.Builder;

@Builder
public record DiskDTO(
    Long serverId,
    Integer vdiskKey,
    Integer unitNumber,
    Long capacityInBytes,
    String vdiskId,
    String device
) {}

