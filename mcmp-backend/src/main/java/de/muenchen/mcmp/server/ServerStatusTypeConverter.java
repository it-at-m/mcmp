package de.muenchen.mcmp.server;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ServerStatusTypeConverter implements AttributeConverter<ServerStatusType, String> {

    @Override
    public String convertToDatabaseColumn(ServerStatusType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ServerStatusType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ServerStatusType.valueOf(dbData.toLowerCase());
    }
}

