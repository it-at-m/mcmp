package de.muenchen.mcmp.clients.foreman;

import de.muenchen.mcmp.server.matching.ServerMatcher;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/foreman")
public class ForemanController implements DisposableBean {

    private final ForemanServerCache foremanServerCache;
    private final ForemanImportService foremanImportService;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ForemanImportThread");
        thread.setDaemon(false);
        return thread;
    });

    @Override
    public void destroy() throws Exception {
        importExecutor.shutdown();
        if (!importExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            importExecutor.shutdownNow();
            log.warn("ForEman import executor did not terminate gracefully");
        }
    }

    /**
     * Health check endpoint to verify API accessibility.
     *
     * @return confirmation message
     */
    @GetMapping("/ping")
    public String ping() {
        return "Foreman EAI API is accessible";
    }

    /**
     * Processes and imports Foreman host data into the local server database.
     * Matches Foreman hosts to existing servers and updates their properties.
     * Servers not present in the import data will have their Foreman-related fields reset.
     *
     * @param foremanDataDTO the Foreman data containing host information
     */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public void processForemanData(@Valid @RequestBody final ForemanDataDTO foremanDataDTO) {
        int importSize = foremanDataDTO.hosts() != null ? foremanDataDTO.hosts().size() : 0;
        log.info("Received Foreman data with {} hosts. Processing in background.", importSize);

        if (importSize == 0) {
            log.warn("Import list is empty. Aborting to prevent mass-reset.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try (ForemanServerCache cache = foremanServerCache.loadForResource()) {
                log.info("Foreman server cache loaded for import operation");

                final List<ServerMatcher<HostDTO>> strategies = createForemanStrategies(cache);

                final Set<Long> processedServerIDs = new HashSet<>();

                for (final HostDTO hostDTO : foremanDataDTO.hosts()) {
                    try {
                        foremanImportService.processHostInNewTransaction(hostDTO, processedServerIDs, strategies);
                    } catch (Exception e) {
                        log.error("Error processing host {}: {}",
                                hostDTO.name(),
                                e.getMessage(), e);
                    }
                }

                // Reset servers not in import
                foremanImportService.resetUnprocessedServers(processedServerIDs);

                log.info("Successfully processed Foreman data");
            } catch (Exception e) {
                log.error("Critical error during Foreman data processing", e);
            }
        }, importExecutor);
    }

    /**
     * Initializes Foreman matching strategies using the provided cache.
     */
    private List<ServerMatcher<HostDTO>> createForemanStrategies(ForemanServerCache cache) {
        List<ServerMatcher<HostDTO>> strategies = List.of(
                cache.createForemanSourceIdMatcher(
                        host -> host.id() != null ? host.id() : null,
                        HostDTO::source
                ),
                cache.createInstanceUuidMatcher(HostDTO::instanceUuid),
                cache.createUuidMatcher(HostDTO::serialnumber),
                cache.createMacAddressMatcher(HostDTO::mac)
                //,cache.createIpAddressMatcher(HostDTO::ip)
        );
        log.debug("Initialized {} Foreman matching strategies", strategies.size());
        return strategies;
    }
}