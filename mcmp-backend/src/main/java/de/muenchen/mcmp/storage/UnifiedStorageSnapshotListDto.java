package de.muenchen.mcmp.storage;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UnifiedStorageSnapshotListDto {
    private UUID uuid;
    private String name;
    private String createTime;
}
