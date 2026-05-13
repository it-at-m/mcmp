package de.muenchen.mcmp.backup;

import lombok.Builder;
import java.time.OffsetDateTime;

@Builder
public record BackupDTO(
    Long serverId,
    String backupType,
    String backupServer,
    String clientServer,
    String saveSetName,
    String saveTimeString,
    OffsetDateTime saveTime,
    String ssretentString,
    OffsetDateTime ssretent,
    String ssid,
    String cloneId,
    String pool,
    Long totalsize,
    String runtime
) {}

