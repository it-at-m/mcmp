package de.muenchen.mcmp.mountPoint;

import lombok.Builder;

@Builder
public record MountPointDTO(
    Long serverId,
    String diskPath,
    Long capacityInBytes,
    Long freeSpaceInBytes,
    String source,
    Boolean editable
) {}

