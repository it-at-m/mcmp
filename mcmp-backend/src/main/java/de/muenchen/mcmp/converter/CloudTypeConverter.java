package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.CloudType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CloudTypeConverter extends BaseEnumConverter<CloudType> {

    public CloudTypeConverter() {
        super(CloudType.class);
    }
}
