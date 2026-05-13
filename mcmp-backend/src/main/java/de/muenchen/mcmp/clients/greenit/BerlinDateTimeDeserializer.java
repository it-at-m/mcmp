package de.muenchen.mcmp.clients.greenit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public class BerlinDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        final String value = p.getText();
        try {
            final LocalDateTime localDateTime = LocalDateTime.parse(value, FORMATTER);
            return localDateTime.atZone(BERLIN).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            throw new IOException("Invalid dateTime format. Expected: dd.MM.yyyy HH:mm:ss, got: " + value, e);
        }
    }
}