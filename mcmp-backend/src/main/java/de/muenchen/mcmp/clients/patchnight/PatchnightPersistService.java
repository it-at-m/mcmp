package de.muenchen.mcmp.clients.patchnight;

import de.muenchen.mcmp.server.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatchnightPersistService {

    private static final int MAX_ATTEMPTS = 3;

    private final PatchnightPersistAttemptService attemptService;

    /**
     * Persists patchnight changes for each server independently.
     * - Reloads the server from DB on every attempt
     * - Retries up to MAX_ATTEMPTS
     * - Continues on failure and logs failed servers
     */
    public void persistWithReloadAndRetry(
            List<Server> changedServers,
            Map<String, PatchnightDataDTO.ServerDTO> serverDtoByKey
    ) {
        int successCount = 0;
        List<String> failedServers = new ArrayList<>();

        for (Server server : changedServers) {
            boolean persisted = persistSingleServerWithRetry(server.getId(), server.getFqdn(), serverDtoByKey);
            if (persisted) {
                successCount++;
            } else {
                failedServers.add(formatServerRef(server.getId(), server.getFqdn()));
            }
        }

        int failedCount = failedServers.size();
        log.info("Patchnight persist finished: total={}, success={}, failed={}",
                changedServers.size(), successCount, failedCount);

        if (failedCount > 0) {
            log.warn("Servers not persisted (after {} attempts): {}", MAX_ATTEMPTS, String.join(", ", failedServers));
        }
    }

    /**
     * @return true if persisted successfully, false if all attempts failed
     */
    private boolean persistSingleServerWithRetry(
            Long serverId,
            String serverFqdn,
            Map<String, PatchnightDataDTO.ServerDTO> serverDtoByKey
    ) {
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                // Each attempt runs in a REQUIRES_NEW transaction inside the attemptService
                attemptService.persistOneAttempt(serverId, serverDtoByKey);

                if (attempt > 1) {
                    log.info("Server {} persisted successfully on attempt {}/{}",
                            formatServerRef(serverId, serverFqdn), attempt, MAX_ATTEMPTS);
                }
                return true;

            } catch (OptimisticLockingFailureException e) {
                lastException = e;
                log.warn("Optimistic lock while persisting server {} (attempt {}/{}). Retrying...",
                        formatServerRef(serverId, serverFqdn), attempt, MAX_ATTEMPTS);
                backoff(attempt);

            } catch (RuntimeException e) {
                // You may narrow this down to only retry specific transient exceptions if desired
                lastException = e;
                log.warn("Error while persisting server {} (attempt {}/{}). Retrying...",
                        formatServerRef(serverId, serverFqdn), attempt, MAX_ATTEMPTS, e);
                backoff(attempt);
            }
        }

        // After MAX_ATTEMPTS: do not throw, just log and continue
        log.error("Server {} could not be persisted after {} attempts. Skipping. Last error:",
                formatServerRef(serverId, serverFqdn), MAX_ATTEMPTS, lastException);
        return false;
    }

    /**
     * Small linear backoff to reduce immediate collision probability under high concurrency.
     */
    private void backoff(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String formatServerRef(Long serverId, String serverFqdn) {
        String safeId = (serverId == null) ? "<null>" : String.valueOf(serverId);
        String safeFqdn = (serverFqdn == null || serverFqdn.isBlank()) ? "<unknown-fqdn>" : serverFqdn;
        return "id=" + safeId + "/fqdn=" + safeFqdn;
    }
}