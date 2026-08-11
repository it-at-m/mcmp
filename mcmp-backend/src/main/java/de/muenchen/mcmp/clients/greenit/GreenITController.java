package de.muenchen.mcmp.clients.greenit;

import de.muenchen.mcmp.clients.greenit.vmware.rightsizing.*;
import de.muenchen.mcmp.clients.greenit.vmware.shutdown.VMwareShutdownMailResponseDTO;
import de.muenchen.mcmp.clients.greenit.vmware.shutdown.VMwareShutdownRequestDTO;
import de.muenchen.mcmp.security.HasApiRole;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing the GreenIT API endpoints.
 *
 * <p>This controller acts as the HTTP entry point for VMware optimization workflows provided by GreenIT,
 * such as rightsizing and shutdown requests. It also provides endpoints to trigger report emails
 * (including generated Excel attachments) for a given start time.</p>
 *
 * <p><b>Security:</b> All endpoints are protected by {@link HasApiRole}.</p>
 *
 * <p><b>Base path:</b> {@code /green-it}</p>
 *
 * @see GreenITService
 */
@RestController
@AllArgsConstructor
@RequestMapping("/green-it")
@Slf4j
public class GreenITController {

    private final GreenITService greenITService;

    /**
     * Simple health check endpoint for the GreenIT API.
     *
     * @return a static string confirming that the endpoint is reachable
     */
    @HasApiRole
    @GetMapping("/ping")
    public String ping() {
        log.debug("Health check request received for GreenIT API endpoint");
        return "GreenIT EAI API is accessible";
    }

    /**
     * Processes a VMware rightsizing request received from GreenIT.
     *
     * <p>The request payload is validated and then handed over to {@link GreenITService} for processing.
     * A successful response contains details about the created job.</p>
     *
     * @param vmwareRightsizeRequestDTO validated rightsizing request (server identification, start time, and target CPU/RAM)
     * @return {@link ResponseEntity} containing the processing result and the created job information
     */
    @HasApiRole
    @PostMapping("/vmware/rightsizing")
    public ResponseEntity<GreenITResponseDTO> processVMwareRightsize(@Valid @RequestBody final VMwareRightsizeRequestDTO vmwareRightsizeRequestDTO) {
        log.info("Received VMware rightsizing request: {}", vmwareRightsizeRequestDTO);
        final GreenITResponseDTO response = greenITService.processVmwareRightsizing(vmwareRightsizeRequestDTO);
        return ResponseEntity.ok(response);
    }

    @HasApiRole
    @PostMapping("/vmware/server/rightsizing")
    @ResponseStatus(HttpStatus.CREATED)
    public void processRightsizeRecommendations(@Valid @RequestBody final RightsizingServerListDTO rightsizingServerDTOList) {
        log.info("Received resource recommendation request: {}", rightsizingServerDTOList);
        greenITService.processRightsizeRecommendations(rightsizingServerDTOList);
    }

    @HasApiRole
    @GetMapping("/vmware/rightsizing/recommendations")
    public ResponseEntity<List<RightsizingRecommendationsDTO>> getRightsizeRecommendations() {
        log.info("Received request for Rightsizing recommendations");
        return ResponseEntity.ok(greenITService.getRightsizeRecommendations());
    }

    /**
     * Triggers sending the VMware rightsizing report email for the given start time.
     *
     * <p>The request payload is validated and then handed over to {@link GreenITService}, which generates
     * the report (including an Excel attachment) and sends the email.</p>
     *
     * @param sendMailRequestDTO validated request containing the report start time
     * @return {@link ResponseEntity} containing a message and the generated mail data items
     */
    @HasApiRole
    @PostMapping("/vmware/rightsizing/report-email")
    public ResponseEntity<VMwareRightsizeMailResponseDTO> sendVmwareRightsizingReportMail(@Valid @RequestBody final SendMailRequestDTO sendMailRequestDTO) {
        log.info("Received send VMware rightsizing mail request: {}", sendMailRequestDTO);
        final VMwareRightsizeMailResponseDTO response = greenITService.sendVmwareRightsizingMail(sendMailRequestDTO);
        return ResponseEntity.ok(response);
    }

    /**
     * Processes a VMware shutdown request received from GreenIT.
     *
     * <p>The request payload is validated and then handed over to {@link GreenITService} for processing.
     * A successful response contains details about the created job.</p>
     *
     * @param vmwareShutdownRequestDTO validated shutdown request (server identification and start time)
     * @return {@link ResponseEntity} containing the processing result and the created job information
     */
    @HasApiRole
    @PostMapping("/vmware/stop")
    public ResponseEntity<GreenITResponseDTO> processVMwareShutdown(@Valid @RequestBody final VMwareShutdownRequestDTO vmwareShutdownRequestDTO) {
        log.info("Received VMware shutdown request: {}", vmwareShutdownRequestDTO);
        final GreenITResponseDTO response = greenITService.processVmwareShutdown(vmwareShutdownRequestDTO);
        return ResponseEntity.ok(response);
    }

    /**
     * Triggers sending the VMware shutdown report email for the given start time.
     *
     * <p>The request payload is validated and then handed over to {@link GreenITService}, which generates
     * the report (including an Excel attachment) and sends the email.</p>
     *
     * @param sendMailRequestDTO validated request containing the report start time
     * @return {@link ResponseEntity} containing a message and the generated mail data items
     */
    @HasApiRole
    @PostMapping("/vmware/stop/report-email")
    public ResponseEntity<VMwareShutdownMailResponseDTO> sendVmwareShutdownReportMail(@Valid @RequestBody final SendMailRequestDTO sendMailRequestDTO) {
        log.info("Received send VMware shutdown mail request: {}", sendMailRequestDTO);
        final VMwareShutdownMailResponseDTO response = greenITService.sendVmwareShutdownMail(sendMailRequestDTO);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns GreenIT-relevant details for a specific server, including server metrics history.
     *
     * @param serverId the ID of the server
     * @return {@link ResponseEntity} containing server info and all associated metrics
     */
    @HasApiRole
    @GetMapping("/vmware/server/{serverId}")
    public ResponseEntity<GreenItServerDTO> getServerGreenItDetail(@PathVariable final Long serverId) {
        log.info("Received GreenIT detail request for server id: {}", serverId);
        final GreenItServerDTO response = greenITService.getServerGreenItDetail(serverId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the IDs of all servers that have metric data and belong to a GreenIT-enabled cloud.
     *
     * @return {@link ResponseEntity} containing the list of server IDs
     */
    @HasApiRole
    @GetMapping("/vmware/server")
    public ResponseEntity<List<Long>> getServerIdsWithMetrics() {
        log.info("Received request for server IDs with metrics and GreenIT enabled");
        return ResponseEntity.ok(greenITService.getServerIdsWithMetrics());
    }
}