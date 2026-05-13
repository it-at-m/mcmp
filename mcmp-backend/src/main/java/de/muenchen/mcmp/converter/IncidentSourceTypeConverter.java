package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.IncidentSourceType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IncidentSourceTypeConverter extends BaseEnumConverter<IncidentSourceType> {

    public IncidentSourceTypeConverter() {
        super(IncidentSourceType.class);
    }
}
