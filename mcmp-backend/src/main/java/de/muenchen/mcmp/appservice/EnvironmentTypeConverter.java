package de.muenchen.mcmp.appservice;

import de.muenchen.mcmp.types.EnvironmentType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EnvironmentTypeConverter implements AttributeConverter<EnvironmentType, String> {

    @Override
    public String convertToDatabaseColumn(EnvironmentType attribute) {
        return attribute == null ? null : attribute.name().toUpperCase();
    }

    @Override
    public EnvironmentType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EnvironmentType.valueOf(dbData.toUpperCase());
    }
}
