package de.muenchen.mcmp.clients.checkmk;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing REST endpoints for interacting with the Checkmk EAI API.
 * It includes functionalities for verifying API accessibility and processing
 * incoming data asynchronously.
 */
@RestController
@AllArgsConstructor
@RequestMapping("/checkmk")
@Slf4j
public class CheckMkController {

    private final CheckMkService checkMkService;

    /**
     * Endpoint to verify the accessibility of the Checkmk EAI API.
     *
     * @return a string message indicating that the Checkmk EAI API is accessible.
     */
    @GetMapping("/ping")
    public String ping() {
        log.debug("Health check request received for Checkmk API endpoint");
        return "Checkmk EAI API is accessible";
    }

    /**
     * Processes Checkmk data received via a POST request and initiates an asynchronous import task.
     * Validates and logs the received data, and delegates the processing to a background service.
     * If the provided data is invalid or empty, the method logs a warning and exits without further action.
     *
     * @param checkMkDTO the Checkmk data to be processed, represented as a DTO object containing host information.
     *                   This parameter must be valid and cannot be null. Validation is enforced on the request body.
     */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void processCheckMkData(@Valid @RequestBody final CheckMkDTO checkMkDTO) {
        int importSize = (checkMkDTO != null && checkMkDTO.hosts() != null) ? checkMkDTO.hosts().size() : 0;
        log.info("Received Checkmk data with {} hosts. Processing in background.", importSize);

        if (importSize == 0) {
            log.warn("Import list is empty.");
            return;
        }

        try {
            checkMkService.importAsync(checkMkDTO);
            log.debug("Checkmk import task successfully submitted to background queue. Task is now pending in the execution queue.");
        } catch (Exception e) {
            log.error("Checkmk import task submission failed. error='{}'. The request was received and validated but could not be queued for background processing.", e.getMessage(), e);
        }
    }
}
