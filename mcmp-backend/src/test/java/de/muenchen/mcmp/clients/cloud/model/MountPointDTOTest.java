package de.muenchen.mcmp.clients.cloud.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MountPointDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void testRegularDeserialization() {
        final var json = """
                {
                  "disk_path": "/",
                  "filesystem_type": "ext4"
                }
                """;

        final var mountPoint = MAPPER.readValue(json, MountPointDTO.class);
        assertEquals("/", mountPoint.diskPath());
        assertEquals("ext4", mountPoint.filesystemType());
    }

    @Test
    void testRejectsEmpty() {
        assertThrows(ValueInstantiationException.class, () -> MAPPER.readValue("{}", MountPointDTO.class));
    }
}
