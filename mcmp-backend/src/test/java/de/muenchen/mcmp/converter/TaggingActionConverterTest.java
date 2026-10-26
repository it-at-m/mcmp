package de.muenchen.mcmp.converter;

import de.muenchen.mcmp.types.TaggingAction;
import de.muenchen.mcmp.types.TaggingStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TaggingActionConverterTest {

    @Test
    void persistsUppercaseLabelsToMatchPostgresEnum() {
        var converter = new TaggingActionConverter();

        assertEquals("SERVER", converter.convertToDatabaseColumn(TaggingAction.SERVER));
        assertEquals("KUBERNETES_NAMESPACE", converter.convertToDatabaseColumn(TaggingAction.KUBERNETES_NAMESPACE));
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void readsLegacyLowercaseAndNewUppercaseValues() {
        var converter = new TaggingActionConverter();

        assertEquals(TaggingAction.SERVER, converter.convertToEntityAttribute("SERVER"));
        assertEquals(TaggingAction.SERVER, converter.convertToEntityAttribute("server"));
    }

    @Test
    void otherConvertersKeepLowercaseByDefault() {
        var converter = new TaggingStatusConverter();

        assertEquals("successful", converter.convertToDatabaseColumn(TaggingStatus.SUCCESSFUL));
    }
}