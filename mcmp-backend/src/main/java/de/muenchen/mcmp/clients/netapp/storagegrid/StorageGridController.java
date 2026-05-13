package de.muenchen.mcmp.clients.netapp.storagegrid;

import de.muenchen.mcmp.clients.netapp.ontap.OntapDTO;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/netapp/storagegrid")
@Slf4j
public class StorageGridController {

    private final StorageGridService storageGridService;

    @GetMapping("/ping")
    public String ping() {
        log.debug("Health check request received for StorageGrid API endpoint");
        return "StorageGrid EAI API is accessible";
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void processStorageGridData(@Valid @RequestBody final StorageGridDTO storageGridDTO) {
        int importSize = storageGridDTO.accounts() != null ? storageGridDTO.accounts().size() : 0;

        log.info(
                "StorageGrid import request received: cluster='{}', account={}, timestamp='{}'. " +
                        "Initiating background processing of StorageGrid data.",
                storageGridDTO.hostname(),
                importSize,
                System.currentTimeMillis()
        );

        if (importSize == 0) {
            log.warn(
                    "StorageGrid import validation failed: cluster='{}' contains zero Accounts. " +
                            "This request has been rejected to prevent unintended reset of cluster configuration. " +
                            "Ensure that the StorageGrid discovery tool is properly configured and has successfully " +
                            "enumerated all Accounts before retrying the import.",
                    storageGridDTO.hostname()
            );
            return;
        }

        try {
            storageGridService.importAsync(storageGridDTO);
            log.debug(
                    "StorageGrid import task successfully submitted to background queue: cluster='{}', accounts={}. Task is now pending in the execution queue.",
                    storageGridDTO.hostname(),
                    importSize
            );
        } catch (Exception e) {
            log.error(
                    "StorageGrid import task submission failed for cluster='{}': error='{}'. The request was received and validated but could not be queued for background processing.",
                    storageGridDTO.hostname(),
                    e.getMessage(),
                    e
            );
        }
    }
}
