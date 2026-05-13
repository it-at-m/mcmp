package de.muenchen.mcmp.clients.patchnight;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatchnightPersistAttemptService {

    private final ServerRepository serverRepository;
    private final PatchnightDataApplier patchnightDataApplier;

    /**
     * Performs a single persistence attempt in its own transaction.
     * Always reloads the entity from DB to avoid working on stale instances.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistOneAttempt(Long serverId, Map<String, PatchnightDataDTO.ServerDTO> serverDtoByKey) {
        Server freshServer = serverRepository.findById(serverId)
                .orElseThrow(() -> new IllegalStateException("Server not found (id=" + serverId + ")"));

        PatchnightDataDTO.ServerDTO serverDto = serverDtoByKey.get(freshServer.getFqdn());
        patchnightDataApplier.apply(freshServer, serverDto);

        // saveAndFlush ensures DB write happens inside this transaction (fail fast)
        serverRepository.saveAndFlush(freshServer);
    }
}