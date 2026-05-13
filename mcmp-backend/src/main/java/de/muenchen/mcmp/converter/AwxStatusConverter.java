package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.AwxStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AwxStatusConverter extends BaseEnumConverter<AwxStatus> {

    public AwxStatusConverter() {
        super(AwxStatus.class);
    }
}