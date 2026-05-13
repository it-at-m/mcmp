package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.TaggingStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaggingStatusConverter extends BaseEnumConverter<TaggingStatus> {

    public TaggingStatusConverter() {
        super(TaggingStatus.class);
    }
}
