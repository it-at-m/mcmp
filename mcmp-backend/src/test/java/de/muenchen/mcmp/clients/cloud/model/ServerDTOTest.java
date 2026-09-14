package de.muenchen.mcmp.clients.cloud.model;

import de.muenchen.mcmp.server.ServerStatusType;
import de.muenchen.mcmp.types.ServerKind;
import de.muenchen.mcmp.types.ServerType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ServerDTOTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static String minimalJSON() {
        return minimalJSON(new HashMap<>());
    }

    private static String minimalJSON(Map<String, Object> extraAttrs) {
        final var attrs = new HashMap<>(extraAttrs);
        attrs.putIfAbsent("uuid", "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA");
        return MAPPER.writeValueAsString(attrs);
    }

    @Test
    void testRegularDeserialization() {
        // Some attributes omitted for brevity
        final var json = """
                {
                  "server_kind": "VIRTUAL",
                  "server_type": "VM_PROXMOX",
                  "name": "vm.example.com",
                  "uuid": "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
                  "vm_id": "100",
                  "power_state": "poweredOn",
                  "memory_mb": 1024,
                  "num_cpu": 1
                }
                """;

        final var server = MAPPER.readValue(json, ServerDTO.class);
        assertEquals(ServerKind.VIRTUAL, server.serverKind());
        assertEquals(ServerType.VM_PROXMOX, server.serverType());
        assertEquals("vm.example.com", server.name());
        assertEquals("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA", server.uuid());
        assertEquals("100", server.vmId());
        assertEquals("poweredOn", server.powerState());
        assertEquals(1024, server.memoryMB());
        assertEquals(1, server.numCPU());
    }

    @Test
    void testRejectsMissingUUID() {
        assertThrows(ValueInstantiationException.class, () -> MAPPER.readValue("{}", ServerDTO.class));
    }

    // Keep this test in sync with database nullability constraints
    // and default values if possible
    @Test
    void testDeserializationDefaults() {
        final var server = MAPPER.readValue(minimalJSON(), ServerDTO.class);
        assertEquals(ServerKind.UNKNOWN, server.serverKind());
        assertEquals(ServerType.UNKNOWN, server.serverType());

        // Checks for name and uuid intentionally omitted
        // Keeping those as null is useful for filtering in the service

        assertEquals(0, server.memoryMB());
        assertEquals(0, server.numCPU());

        assertEquals(false, server.memoryHotAddEnabled());
        assertEquals(false, server.cpuHotAddEnabled());
        assertEquals(false, server.cpuHotRemoveEnabled());

        assertEquals(ServerStatusType.gray, server.overallStatus());
        assertEquals(ServerStatusType.gray, server.configStatus());

        assertEquals(List.of(), server.snapshots());
    }

    @Test
    void testDeserializeExplicitNullAttribute() {
        final var attrs = new HashMap<String, Object>();
        attrs.put("memory_mb", null);
        final var json = minimalJSON(attrs);
        final var server = MAPPER.readValue(json, ServerDTO.class);
        assertEquals(0, server.memoryMB());
    }

    @Test
    void testDeserializeExplicitNullInListAttribute() {
        final var snapshots = new ArrayList<SnapshotDTO>();
        snapshots.add(null);
        final var json = minimalJSON(Map.of("snapshots", snapshots));
        final var server = MAPPER.readValue(json, ServerDTO.class);
        assertEquals(List.of(), server.snapshots());
    }

    @Test
    void testDeserializationNameFallback() {
        final var server = MAPPER.readValue(minimalJSON(), ServerDTO.class);
        assertEquals(server.uuid(), server.name());
    }

    @Test
    void testPowerStateConversion() {
        final var mappings = Map.of(
                "poweredOn", "poweredOn",   // vmware
                "poweredOff", "poweredOff",
                "running", "poweredOn",     // proxmox
                "stopped", "poweredOff",
                "on", "poweredOn",          // ucs
                "off", "poweredOff",
                "up", "poweredOn",          // olvm
                "down", "poweredOff"
        );

        for (final var entry : mappings.entrySet()) {
            final var json = minimalJSON(Map.of("power_state", entry.getKey()));
            final var server = MAPPER.readValue(json, ServerDTO.class);
            assertEquals(entry.getValue(), server.powerState());
        }
    }

    @Test
    void testNormalizeFQDN() {
        assertEquals("dcwik102.example.org", ServerDTO.normalizeFQDN("dcwik102.example.org"));
        assertEquals("dcwik102.example.org", ServerDTO.normalizeFQDN("dcwik102m.example.org"));
    }
}
