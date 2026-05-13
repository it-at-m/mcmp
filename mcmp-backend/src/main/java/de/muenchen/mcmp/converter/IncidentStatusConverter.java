package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.IncidentStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IncidentStatusConverter extends BaseEnumConverter<IncidentStatus> {

    public IncidentStatusConverter() {
        super(IncidentStatus.class);
    }
}