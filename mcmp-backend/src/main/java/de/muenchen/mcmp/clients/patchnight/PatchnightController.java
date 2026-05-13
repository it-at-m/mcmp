package de.muenchen.mcmp.clients.patchnight;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@AllArgsConstructor
@RequestMapping("/patchnight")
@Slf4j
public class PatchnightController {

    private final ServerRepository serverRepository;
    private final PatchnightPersistService patchnightPersistService;
    private final PatchnightDataApplier patchnightDataApplier;

    @GetMapping("/ping")
    public String ping() {
        return "Patchnight EAI API is accessible";
    }

    @PostMapping("/update")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<String> updatePatchnightData(@Valid @RequestBody final PatchnightDataDTO patchnightData) {
        List<PatchnightDataDTO.ServerDTO> incomingServers = (patchnightData.servers() == null) ? List.of() : patchnightData.servers();

        log.info("Received patchnight data with {} servers", incomingServers.size());

        // 1) Alle Server aus den DTO in eine Map einlesen (key = name)
        // Nur Server mit nicht-null und nicht-blank Namen aufnehmen
        // Server die mehrfach vorkommen werden komplett ignoriert
        Map<String, PatchnightDataDTO.ServerDTO> serverDTOMap = new LinkedHashMap<>();
        Set<String> duplicateServers = new HashSet<>();

        // Ersten Pass: Duplikate identifizieren
        Map<String, Long> serverCounts = incomingServers.stream()
                .filter(serverDTO -> serverDTO.name() != null && !serverDTO.name().isBlank())
                .collect(Collectors.groupingBy(
                        PatchnightDataDTO.ServerDTO::name,
                        Collectors.counting()
                ));

        // Zweiter Pass: Nur eindeutige Server hinzufügen
        incomingServers.stream()
                .filter(serverDTO -> serverDTO.name() != null && !serverDTO.name().isBlank())
                .forEach(serverDTO -> {
                    String serverName = serverDTO.name();
                    Long count = serverCounts.get(serverName);

                    if (count > 1) {
                        duplicateServers.add(serverName);
                    } else {
                        serverDTOMap.put(serverName, serverDTO);
                    }
                });

        // Logging für duplikate Server
        for (String duplicateServer : duplicateServers) {
            log.warn("Server '{}' ist mehrfach im Import vorhanden und wird komplett ignoriert.", duplicateServer);
        }

        if (!duplicateServers.isEmpty()) {
            log.warn("Insgesamt {} Server wurden aufgrund von Duplikaten komplett ignoriert: {}",
                    duplicateServers.size(), String.join(", ", duplicateServers));
        }

        log.info("Filtered ServerDTO map contains {} valid servers", serverDTOMap.size());

        // 2) Alle Server aus der Datenbank einlesen
        List<Server> allServers = serverRepository.findAll();

        // Liste für geänderte Server
        List<Server> changedServers = new ArrayList<>();

        // 3) Durch alle Server der DB nacheinander verarbeiten
        for (Server server : allServers) {
            PatchnightDataDTO.ServerDTO serverDTO = serverDTOMap.get(server.getFqdn());

            boolean changed = patchnightDataApplier.applyAndDetectChanges(server, serverDTO);
            if (changed) {
                changedServers.add(server);
            }
        }

        // Nur geänderte Server in der Datenbank speichern
        if (!changedServers.isEmpty()) {
            patchnightPersistService.persistWithReloadAndRetry(changedServers, serverDTOMap);
            log.info("Updated {} servers with changed patchnight data (reload+retry)", changedServers.size());
        } else {
            log.info("No servers required updates");
        }

        log.info("Patchnight data processing completed for {} total servers", allServers.size());
        return ResponseEntity.ok("Patchnight data processed successfully");
    }
}