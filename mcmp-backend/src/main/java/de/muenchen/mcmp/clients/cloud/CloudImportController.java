package de.muenchen.mcmp.clients.cloud;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/cloud")
@Slf4j
public class CloudImportController {

    private final CloudImportAsyncService cloudService;

    @GetMapping("/ping")
    public String ping() {
        log.debug("Health check request received for Cloud API endpoint");
        return "Cloud EAI API is accessible";
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void processCloudData(@Valid @RequestBody final CloudImportDTO cloudDTO) {
        int importSize = (cloudDTO != null && cloudDTO.servers() != null) ? cloudDTO.servers().size() : 0;
        log.info("Received Cloud data with {} servers. Processing in background.", importSize);

        if (importSize == 0) {
            log.warn("Import list is empty.");
            return;
        }

        try {
            cloudService.importAsync(cloudDTO);
            log.debug("Cloud import task successfully submitted to background queue. Task is now pending in the execution queue.");
        } catch (Exception e) {
            log.error("Cloud import task submission failed. error='{}'. The request was received and validated but could not be queued for background processing.", e.getMessage(), e);
        }
    }
}
