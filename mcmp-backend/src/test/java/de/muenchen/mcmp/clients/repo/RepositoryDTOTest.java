package de.muenchen.mcmp.clients.repo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for the Spring Boot 4 / Jackson 3 upgrade: {@code MetadataDTO.modified} was a
 * primitive {@code boolean} that crashed the JSON parser when the repo-scan tool omitted it.
 */
class RepositoryDTOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void metadataDTO_nullModified_doesNotThrow() {
        String json = """
                {"name": "repo1", "modified": null}
                """;

        RepositoryDTO.MetadataDTO metadata = MAPPER.readValue(json, RepositoryDTO.MetadataDTO.class);

        assertNull(metadata.modified());
    }
}
