package de.muenchen.mcmp.clients.cloud.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DiskDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void testRegularDeserialization() {
        final var json = """
                {
                  "vdisk_key": 1,
                  "file_name": "example-disk.qcow2"
                }
                """;

        final var disk = MAPPER.readValue(json, DiskDTO.class);
        assertEquals(1, disk.vdiskKey());
        assertEquals("example-disk.qcow2", disk.fileName());
    }

    @Test
    void testRejectsEmpty() {
        assertThrows(ValueInstantiationException.class, () -> MAPPER.readValue("{}", DiskDTO.class));
    }
}
