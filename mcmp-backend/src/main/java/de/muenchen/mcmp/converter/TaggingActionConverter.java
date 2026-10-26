package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.TaggingAction;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaggingActionConverter extends BaseEnumConverter<TaggingAction> {

    public TaggingActionConverter() {
        super(TaggingAction.class, Case.UPPER);
    }
}
