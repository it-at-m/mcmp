package de.muenchen.mcmp.clients.cloud.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NicDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void testRegularDeserialization() {
        final var json = """
                {
                  "vnic_key": 1,
                  "mac_address": "AA:AA:AA:AA:AA:AA"
                }
                """;

        final var nic = MAPPER.readValue(json, NicDTO.class);
        assertEquals(1, nic.vNicKey());
        assertEquals("AA:AA:AA:AA:AA:AA", nic.macAddress());
    }

    @Test
    void testRejectsEmpty() {
        assertThrows(ValueInstantiationException.class, () -> MAPPER.readValue("{}", NicDTO.class));
    }

    @Test
    void testDefaultValues() {
        final var nic = MAPPER.readValue("{ \"vnic_key\": 1 }", NicDTO.class);
        assertEquals(false, nic.connected());
        assertEquals(false, nic.toolsConnected());
    }
}
