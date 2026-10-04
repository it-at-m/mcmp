package de.muenchen.mcmp.clients.greenit;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public class BerlinDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
        final String value = p.getString();
        try {
            final LocalDateTime localDateTime = LocalDateTime.parse(value, FORMATTER);
            return localDateTime.atZone(BERLIN).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return (OffsetDateTime) ctxt.handleWeirdStringValue(
                    OffsetDateTime.class,
                    value,
                    "Invalid dateTime format. Expected: dd.MM.yyyy HH:mm:ss, got: %s",
                    value
            );
        }
    }
}