package de.muenchen.mcmp.clients.loadbalancer;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the Spring Boot 4 / Jackson 3 upgrade, which made {@code null} into a
 * primitive a hard deserialization failure. The BIG-IP EAI payload omits {@code interval} for
 * some monitor types and can send {@code null} for {@code redirect80}/{@code waf.enabled}, so
 * those fields were boxed. {@code port} stayed required, but is now validated with a real
 * (previously no-op-on-primitive) {@code @NotNull} instead of crashing the JSON parser.
 */
class LoadBalancerDTOTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void monitorDTO_nullInterval_deserializesToNull() {
        String json = """
                {"type": "https", "interval": null}
                """;

        LoadBalancerDTO.MonitorDTO monitor = MAPPER.readValue(json, LoadBalancerDTO.MonitorDTO.class);

        assertNull(monitor.interval());
    }

    @Test
    void wafDTO_nullEnabled_defaultsToFalse() {
        String json = """
                {"enabled": null, "status": "disabled"}
                """;

        LoadBalancerDTO.WafDTO waf = MAPPER.readValue(json, LoadBalancerDTO.WafDTO.class);

        assertEquals(Boolean.FALSE, waf.enabled());
    }

    @Test
    void virtualServerDTO_nullRedirect80_defaultsToFalse() {
        String json = """
                {"addresses": ["10.0.0.1"], "listen": "listen1", "forward": "fwd1",
                 "port": 443, "waf": {"enabled": false, "status": "off"},
                 "persistence": "none", "redirect80": null}
                """;

        LoadBalancerDTO.VirtualServerDTO vs = MAPPER.readValue(json, LoadBalancerDTO.VirtualServerDTO.class);

        assertEquals(Boolean.FALSE, vs.redirect80());
    }

    @Test
    void virtualServerDTO_nullPort_failsBeanValidationInsteadOfJsonParser() {
        String json = """
                {"addresses": ["10.0.0.1"], "listen": "listen1", "forward": "fwd1",
                 "port": null, "waf": {"enabled": false, "status": "off"},
                 "persistence": "none", "redirect80": false}
                """;

        // Deserialization itself must not throw now that port is boxed...
        LoadBalancerDTO.VirtualServerDTO vs = MAPPER.readValue(json, LoadBalancerDTO.VirtualServerDTO.class);
        assertNull(vs.port());

        // ...but bean validation must still reject a missing port.
        Set<ConstraintViolation<LoadBalancerDTO.VirtualServerDTO>> violations = VALIDATOR.validate(vs);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("port")),
                "missing port must be rejected by @NotNull validation");
    }

    @Test
    void poolMemberDTO_nullPort_failsBeanValidation() {
        String json = """
                {"ip": "10.0.0.5", "port": null}
                """;

        LoadBalancerDTO.PoolMemberDTO member = MAPPER.readValue(json, LoadBalancerDTO.PoolMemberDTO.class);
        assertNull(member.port());

        Set<ConstraintViolation<LoadBalancerDTO.PoolMemberDTO>> violations = VALIDATOR.validate(member);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("port")));
    }

    @Test
    void poolMemberDTO_validPort_passesBeanValidation() {
        String json = """
                {"ip": "10.0.0.5", "port": 8080}
                """;

        LoadBalancerDTO.PoolMemberDTO member = MAPPER.readValue(json, LoadBalancerDTO.PoolMemberDTO.class);

        Set<ConstraintViolation<LoadBalancerDTO.PoolMemberDTO>> violations = VALIDATOR.validate(member);
        assertFalse(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("port")));
    }
}
