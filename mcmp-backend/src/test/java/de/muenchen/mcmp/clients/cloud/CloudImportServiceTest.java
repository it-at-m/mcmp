package de.muenchen.mcmp.clients.cloud;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

public class CloudImportServiceTest {
    private static OffsetDateTime datetime(String input) {
        return LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atOffset(ZoneOffset.UTC);
    }

    private static Stream<Arguments> snapshotRetentionTimeTestData() {
        return Stream.of(
                Arguments.of("Retention is a simple hour offset",
                        "MCMP#name###12###",
                        datetime("2026-01-01 00:00"),
                        datetime("2026-01-01 12:00")),
                Arguments.of("Retention looks like date, but is an offset",
                        "MCMP#name###00008760###",
                        datetime("2026-01-01 00:00"),
                        datetime("2027-01-01 00:00")),
                Arguments.of("Retention is a deletion date",
                        "MCMP#name###20260131###",
                        datetime("2026-01-01 00:00"),
                        datetime("2026-01-31 00:00")),
                Arguments.of("Snapshot name does not include retention",
                        "freeform-snapshot-name",
                        datetime("2026-01-01 00:00"),
                        datetime("2026-01-06 00:00"))
        );
    }

    @ParameterizedTest(name = "''{0}''")
    @MethodSource("snapshotRetentionTimeTestData")
    public void testCalculateSnapshotRetentionTime(String ignored, String name, OffsetDateTime creation, OffsetDateTime expected) {
        Assertions.assertEquals(expected, CloudImportService.calculateSnapshotRetentionTime(name, creation));

    }

    private static Stream<Arguments> mgmtFqdnTestData() {
        return Stream.of(
                Arguments.of("FQDN with unnormalized shortname",
                        "dcwik102m.example.org", "dcwik102.example.org"),
                Arguments.of("FQDN with normalized shortname",
                        "dcwik102.example.org", "dcwik102.example.org")
        );
    }

    @ParameterizedTest(name = "''{0}''")
    @MethodSource("mgmtFqdnTestData")
    public void testNormalizeMgmtFQDN(String ignored, String name, String expected) {
        Assertions.assertEquals(expected, CloudImportService.normalizeMgmtFQDN(name));
    }
}