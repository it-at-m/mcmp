package de.muenchen.mcmp.clients.db.oracle;

import de.muenchen.mcmp.database.DatabasePdbInstanceRepository;
import de.muenchen.mcmp.database.DatabasePdbInstanceServerDTO;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.muenchen.mcmp.security.HasApiRole;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/db/oracle")
@Slf4j
public class OracleDBController {

    private final DatabasePdbInstanceRepository databasePdbInstanceRepository;
    private final OracleImportAsyncService oracleImportAsyncService;

    /**
     * Simple health check endpoint for the Oracle DB EAI API.
     *
     * @return a static string confirming that the endpoint is reachable
     */
    @HasApiRole
    @GetMapping("/ping")
    public String ping() {
        log.debug("Health check request received for Oracle DB API endpoint");
        return "Oracle DB EAI API is accessible";
    }

    /**
     * Returns all Oracle servers for EAI processing.
     *
     * @return {@link ResponseEntity} containing the list of Oracle servers
     */
    @HasApiRole
    @GetMapping("/servers")
    public ResponseEntity<List<DatabasePdbInstanceServerDTO>> getAllOracleServers() {
        log.info("Received request to fetch all Oracle servers for EAI");
        return ResponseEntity.ok(databasePdbInstanceRepository.findManagedPoweredOnOracleServerPdbInstances());
    }

    /**
     * Imports Oracle database metadata, instance details, users, and tablespaces.
     *
     * @param importDTO the imported Oracle JSON payload
     */
    @HasApiRole
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void importOracleDatabases(@Valid @RequestBody final OracleDTO importDTO) {
        int dbCount = (importDTO != null && importDTO.databases() != null)
                ? importDTO.databases().size() : 0;

        log.info("Received Oracle DB import request with {} databases. Delegating to async service.", dbCount);

        if (dbCount == 0) {
            log.warn("Import database list is empty, nothing to do.");
            return;
        }

        oracleImportAsyncService.importAsync(importDTO);
    }
}
