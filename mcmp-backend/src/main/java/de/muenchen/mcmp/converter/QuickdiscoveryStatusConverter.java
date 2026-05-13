package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.QuickdiscoveryStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class QuickdiscoveryStatusConverter extends BaseEnumConverter<QuickdiscoveryStatus> {

    public QuickdiscoveryStatusConverter() {
        super(QuickdiscoveryStatus.class);
    }
}
