package de.muenchen.mcmp.storage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnifiedStorageItemListDto {
    private String uuid;
    private String name;
    private String path;
    private StorageType type;
    private StorageCategory storageCategory;
    private String protocol;
    private String appserviceNames;
}
