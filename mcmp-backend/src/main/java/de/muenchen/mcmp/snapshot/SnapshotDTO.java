package de.muenchen.mcmp.snapshot;

import lombok.Builder;
import java.time.OffsetDateTime;

@Builder
public record SnapshotDTO(
    Long serverId,
    Integer snapshotId,
    String name,
    String description,
    OffsetDateTime createTime,
    boolean quiesced,
    String state,
    boolean replaySupported,
    OffsetDateTime retentionPeriod
) {}

