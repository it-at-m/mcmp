package de.muenchen.mcmp.clients.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudImportAsyncService {

    private final CloudImportService cloudImportService;

    @Async
    public void importAsync(final CloudImportDTO cloudDTO) {
        try {
            cloudImportService.importCloudData(cloudDTO);
        } catch (Exception e) {
            log.error("Error importing Cloud data: {}", e.getMessage(), e);
        }
    }
}
