package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.ChangeStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ChangeStatusConverter extends BaseEnumConverter<ChangeStatus> {

    public ChangeStatusConverter() {
        super(ChangeStatus.class);
    }
}
