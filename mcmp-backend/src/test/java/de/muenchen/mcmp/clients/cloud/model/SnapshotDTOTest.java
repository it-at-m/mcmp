package de.muenchen.mcmp.clients.cloud.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SnapshotDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final OffsetDateTime BASIC_OFFSET_DATETIME =
            OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

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
        assertEquals(BASIC_OFFSET_DATETIME, snapshot.createTime());
        assertEquals(true, snapshot.quiesced());
        assertEquals("poweredOn", snapshot.state());
        assertEquals(true, snapshot.replaySupported());
    }

    @Test
    void testEmptyDeserialization() {
        assertDoesNotThrow(() -> MAPPER.readValue("{}", SnapshotDTO.class));
    }

    // Keep this test in sync with database nullability constraints
    // and default values if possible
    @Test
    void testDeserializationDefaults() {
        final var snapshot = MAPPER.readValue("{}", SnapshotDTO.class);
        assertEquals(false, snapshot.quiesced());
        assertEquals(false, snapshot.replaySupported());
    }
}
