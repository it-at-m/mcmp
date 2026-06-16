package de.muenchen.mcmp.clients.db.oracle;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.server.ServerDbDTO;
import de.muenchen.mcmp.security.HasApiRole;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/db/oracle")
@Slf4j
public class OracleDBController {

    private final ServerService serverService;

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
    public ResponseEntity<List<ServerDbDTO>> getAllOracleServers() {
        log.info("Received request to fetch all Oracle servers for EAI");
        return ResponseEntity.ok(serverService.findAllOracleServers());
    }
}
