package de.muenchen.mcmp.storage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnifiedStorageItemListDto {
    private String uuid;
    private String name;
    private StorageType type;
    private String protocol;
    private String appserviceNames;
}
