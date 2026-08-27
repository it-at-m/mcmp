package de.muenchen.mcmp.clients.foreman;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression test for the Spring Boot 4 / Jackson 3 upgrade: the "*_db"/"linux"/"windows"/
 * "tetration_agent_is_installed" feature-detection flags were primitive {@code boolean}s that
 * crashed the JSON parser when Foreman omitted them. They are now boxed and normalized to
 * {@code false} in the record's compact constructor, since {@link ForemanImportService} uses
 * them directly in conditionals (e.g. {@code hostDTO.linux() && ...}) which would otherwise NPE
 * on an unboxed null.
 */
class HostDTOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void nullBooleanFlags_defaultToFalse() {
        String json = """
                {
                  "name": "host1",
                  "oracle_db": null,
                  "mssql_db": null,
                  "maria_db": null,
                  "mysql_db": null,
                  "mongo_db": null,
                  "adabas_db": null,
                  "postgres_db": null,
                  "linux": null,
                  "windows": null,
                  "tetration_agent_is_installed": null
                }
                """;

        HostDTO host = MAPPER.readValue(json, HostDTO.class);

        assertFalse(host.oracleDb());
        assertFalse(host.mssqlDb());
        assertFalse(host.mariaDb());
        assertFalse(host.mysqlDb());
        assertFalse(host.mongoDb());
        assertFalse(host.adabasDb());
        assertFalse(host.postgresDb());
        assertFalse(host.linux());
        assertFalse(host.windows());
        assertFalse(host.tetrationAgentIsInstalled());
    }

    @Test
    void explicitTrueFlags_arePreserved() {
        String json = """
                {"name": "host1", "linux": true, "oracle_db": true}
                """;

        HostDTO host = MAPPER.readValue(json, HostDTO.class);

        assertEquals(Boolean.TRUE, host.linux());
        assertEquals(Boolean.TRUE, host.oracleDb());
        assertFalse(host.windows());
    }
}
