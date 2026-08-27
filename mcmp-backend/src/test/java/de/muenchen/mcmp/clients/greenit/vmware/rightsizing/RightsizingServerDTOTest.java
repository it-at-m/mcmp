package de.muenchen.mcmp.clients.greenit.vmware.rightsizing;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the Spring Boot 4 / Jackson 3 upgrade: {@code id}/{@code num_cpu}/
 * {@code memory_mb} were primitives, so a {@code null} crashed the JSON parser instead of being
 * rejected as a clean validation error. They are now boxed with a real {@code @NotNull} (the
 * previous {@code @NotNull} on {@code id} was a no-op on a primitive) since these values drive
 * a server lookup and resource-recommendation update in {@link
 * de.muenchen.mcmp.clients.greenit.GreenITService}, where silently defaulting to {@code 0} would
 * be worse than rejecting the request.
 */
class RightsizingServerDTOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void nullId_deserializesThenFailsValidation() {
        String json = """
                {"id": null, "num_cpu": 4, "memory_mb": 8192}
                """;

        RightsizingServerDTO dto = MAPPER.readValue(json, RightsizingServerDTO.class);

        Set<ConstraintViolation<RightsizingServerDTO>> violations = VALIDATOR.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("id")));
    }

    @Test
    void nullNumCpuOrMemoryMb_failsValidation() {
        String json = """
                {"id": 1, "num_cpu": null, "memory_mb": null}
                """;

        RightsizingServerDTO dto = MAPPER.readValue(json, RightsizingServerDTO.class);

        Set<ConstraintViolation<RightsizingServerDTO>> violations = VALIDATOR.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("numCpu")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("memoryMb")));
    }

    @Test
    void validPayload_passesValidation() {
        String json = """
                {"id": 1, "num_cpu": 4, "memory_mb": 8192}
                """;

        RightsizingServerDTO dto = MAPPER.readValue(json, RightsizingServerDTO.class);

        assertEquals(1L, dto.id());
        assertEquals(4, dto.numCpu());
        assertEquals(8192, dto.memoryMb());
        assertFalse(VALIDATOR.validate(dto).stream().anyMatch(v ->
                v.getPropertyPath().toString().equals("id")
                        || v.getPropertyPath().toString().equals("numCpu")
                        || v.getPropertyPath().toString().equals("memoryMb")));
    }
}
