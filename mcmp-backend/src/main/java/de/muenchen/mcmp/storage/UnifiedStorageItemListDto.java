package de.muenchen.mcmp.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    // Explicit @JsonProperty: Lombok's isFavorite() getter would otherwise be inferred by
    // Jackson as bean property "favorite" (stripping the "is" prefix), not "isFavorite".
    @JsonProperty("isFavorite")
    private boolean isFavorite;
}
