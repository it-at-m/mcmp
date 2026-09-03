package de.muenchen.mcmp.clients.cloud.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void testRegularDeserialization() {
        final var json = """
                {
                  "cloud": "example",
                  "cloud_type": "PROXMOX"
                }
                """;

        final var cloud = MAPPER.readValue(json, CloudDTO.class);
        assertEquals("example", cloud.cloud());
        assertEquals("PROXMOX", cloud.cloudType());
    }
}
