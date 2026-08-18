package de.muenchen.mcmp.clients.db.oracle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OracleImportAsyncService {

    private final OracleImportService oracleImportService;

    @Async
    public void importAsync(final OracleDTO importDTO) {
        try {
            oracleImportService.importData(importDTO);
        } catch (Exception e) {
            log.error("Error during asynchronous Oracle DB import: {}", e.getMessage(), e);
        }
    }
}