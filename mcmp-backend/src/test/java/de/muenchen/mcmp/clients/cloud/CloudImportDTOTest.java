package de.muenchen.mcmp.clients.cloud;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression tests for the Spring Boot 4 / Jackson 3 upgrade, which made {@code null} into a
 * primitive ({@code boolean}/{@code int}) a hard deserialization failure instead of a lenient
 * no-op.
 */
class CloudImportDTOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @ParameterizedTest(name = "quiesced={0}, replay_supported={1}")
    @MethodSource("nullBooleanCombinations")
    void snapshot_nullBooleans_defaultToFalse(String quiescedJson, boolean expectedQuiesced,
                                               String replaySupportedJson, boolean expectedReplaySupported) {
        String json = """
                {"name": "snap1", "quiesced": %s, "replay_supported": %s}
                """.formatted(quiescedJson, replaySupportedJson);

        CloudImportDTO.Snapshot snapshot = MAPPER.readValue(json, CloudImportDTO.Snapshot.class);

        assertEquals(expectedQuiesced, snapshot.quiesced());
        assertEquals(expectedReplaySupported, snapshot.replaySupported());
    }

    private static Stream<Arguments> nullBooleanCombinations() {
        return Stream.of(
                Arguments.of("null", false, "null", false),
                Arguments.of("null", false, "true", true),
                Arguments.of("false", false, "null", false)
        );
    }

    @ParameterizedTest(name = "power_state=''{0}'' -> ''{1}''")
    @MethodSource("powerStateTestData")
    void server_powerState_isNormalized(String rawPowerState, String expected) {
        String powerStateJson = rawPowerState == null ? "null" : "\"" + rawPowerState + "\"";
        String json = """
                {"uuid": "abc-123", "power_state": %s}
                """.formatted(powerStateJson);

        CloudImportDTO.Server server = MAPPER.readValue(json, CloudImportDTO.Server.class);

        assertEquals(expected, server.powerState());
    }

    private static Stream<Arguments> powerStateTestData() {
        return Stream.of(
                Arguments.of(null, "unknown"),
                Arguments.of("poweredOn", "poweredOn"),
                Arguments.of("running", "poweredOn"),
                Arguments.of("on", "poweredOn"),
                Arguments.of("up", "poweredOn"),
                Arguments.of("poweredOff", "poweredOff"),
                Arguments.of("stopped", "poweredOff"),
                Arguments.of("off", "poweredOff"),
                Arguments.of("down", "poweredOff"),
                Arguments.of("something-else", "unknown")
        );
    }

    @org.junit.jupiter.api.Test
    void server_blankName_fallsBackToUuid() {
        String json = """
                {"uuid": "abc-123", "name": "  "}
                """;

        CloudImportDTO.Server server = MAPPER.readValue(json, CloudImportDTO.Server.class);

        assertEquals("abc-123", server.name());
    }

    @org.junit.jupiter.api.Test
    void server_nullNumericAndBooleanFields_defaultInsteadOfThrowing() {
        String json = """
                {"uuid": "abc-123", "memory_mb": null, "num_cpu": null,
                 "memory_hot_add_enabled": null, "cpu_hot_add_enabled": null,
                 "cpu_hot_remove_enabled": null, "snapshots": null}
                """;

        CloudImportDTO.Server server = MAPPER.readValue(json, CloudImportDTO.Server.class);

        assertEquals(0, server.memoryMB());
        assertEquals(0, server.numCPU());
        assertFalse(server.memoryHotAddEnabled());
        assertFalse(server.cpuHotAddEnabled());
        assertFalse(server.cpuHotRemoveEnabled());
        assertEquals(0, server.snapshots().size());
    }
}
