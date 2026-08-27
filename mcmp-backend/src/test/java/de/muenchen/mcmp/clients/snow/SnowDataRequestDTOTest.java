package de.muenchen.mcmp.clients.snow;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the Spring Boot 4 / Jackson 3 upgrade: {@code csw_enforced} used to be a
 * primitive {@code boolean} which crashed the JSON parser when SNOW omitted the field. It is
 * now boxed and normalized to {@code false} in the record's compact constructor, matching the
 * {@code NOT NULL} default on {@code Appservice.cswEnforced}.
 */
class SnowDataRequestDTOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void appServiceDTO_nullCswEnforced_defaultsToFalse() {
        String json = """
                {"sys_id": "sys1", "name": "AppService1", "csw_enforced": null}
                """;

        SnowDataRequestDTO.AppServiceDTO dto = MAPPER.readValue(json, SnowDataRequestDTO.AppServiceDTO.class);

        assertEquals(Boolean.FALSE, dto.cswEnforced());
    }

    @Test
    void appServiceDTO_explicitCswEnforced_isPreserved() {
        String json = """
                {"sys_id": "sys1", "name": "AppService1", "csw_enforced": true}
                """;

        SnowDataRequestDTO.AppServiceDTO dto = MAPPER.readValue(json, SnowDataRequestDTO.AppServiceDTO.class);

        assertEquals(Boolean.TRUE, dto.cswEnforced());
    }
}
