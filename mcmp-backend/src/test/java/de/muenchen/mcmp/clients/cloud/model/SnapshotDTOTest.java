package de.muenchen.mcmp.clients.cloud.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

public class SnapshotDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static OffsetDateTime datetime(String input) {
        return LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atOffset(ZoneOffset.UTC);
    }

    @Test
    void testRegularDeserialization() {
        final var json = """
                {
                  "name":             "example",
                  "description":      "description",
                  "create_time":      "2026-01-01T00:00:00+00:00",
                  "quiesced":         true,
                  "state":            "poweredOn",
                  "replay_supported": true
                }
                """;

        var snapshot = MAPPER.readValue(json, SnapshotDTO.class);
        assertEquals("example", snapshot.name());
        assertEquals("description", snapshot.description());
        assertEquals(datetime("2026-01-01 00:00"), snapshot.createTime());
        assertEquals(true, snapshot.quiesced());
        assertEquals("poweredOn", snapshot.state());
        assertEquals(true, snapshot.replaySupported());
    }

    @Test
    void testRejectsMissingName() {
        assertThrows(ValueInstantiationException.class, () -> MAPPER.readValue("{}", SnapshotDTO.class));
    }

    // Keep this test in sync with database nullability constraints
    // and default values if possible
    @Test
    void testDeserializationDefaults() {
        final var snapshot = MAPPER.readValue("{ \"name\": \"example\" }", SnapshotDTO.class);
        assertEquals(false, snapshot.quiesced());
        assertEquals(false, snapshot.replaySupported());
    }

    @Test
    void testRetentionPeriodSimpleOffset() {
        assertEquals(
                datetime("2026-01-01 12:00"),
                new SnapshotDTO("MCMP#name###12###", null, datetime("2026-01-01 00:00"), null, null, null)
                        .retentionTime()
        );
    }

    @Test
    void testRetentionPeriodOffsetLooksLikeDateButIsNot() {
        assertEquals(
                datetime("2027-01-01 00:00"),
                new SnapshotDTO("MCMP#name###00008760###", null, datetime("2026-01-01 00:00"), null, null, null)
                        .retentionTime()
        );
    }

    @Test
    void testRetentionPeriodDate() {
        assertEquals(
                datetime("2026-01-31 00:00"),
                new SnapshotDTO("MCMP#name###20260131###", null, datetime("2026-01-01 00:00"), null, null, null)
                        .retentionTime()
        );
    }

    @Test
    void testRetentionPeriodMissing() {
        assertEquals(
                datetime("2026-01-06 00:00"),
                new SnapshotDTO("freeform", null, datetime("2026-01-01 00:00"), null, null, null)
                        .retentionTime()
        );
    }
}
