package de.muenchen.mcmp.clients.checkmk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


/**
 * Service class responsible for managing Checkmk performance data imports.
 * This service provides asynchronous support for data import operations to ensure non-blocking
 * execution of potentially time-intensive tasks. It delegates the actual processing
 * of the data to the {@link CheckMkImportService}, while handling any errors gracefully
 * by logging them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckMkService {

    private final CheckMkImportService checkMkImportService;

    /**
     * Initiates the asynchronous import of Checkmk performance data.
     * The provided data is processed in the background and logged for errors if any occur during the import.
     *
     * @param checkMkDTO the Checkmk data to be imported. Contains a map of hostnames and
     *                   their corresponding performance metrics, including CPU utilization
     *                   and memory usage percentage. Must not be null.
     */
    @Async
    public void importAsync(final CheckMkDTO checkMkDTO) {
        try {
            checkMkImportService.importCheckMkData(checkMkDTO);
        } catch (Exception e) {
            log.error("Error importing CheckMk data: {}", e.getMessage(), e);
        }
    }
}