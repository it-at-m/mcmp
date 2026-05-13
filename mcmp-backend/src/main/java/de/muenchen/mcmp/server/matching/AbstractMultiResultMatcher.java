package de.muenchen.mcmp.server.matching;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMultiResultMatcher<T> {

    protected final ServerService serverService;

    protected abstract String getIdentifier(T data);
    protected abstract String getIdentifierType();
    protected abstract List<Server> findServers(String identifier);

    public Server match(T data) {
        String identifier = getIdentifier(data);
        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        final List<Server> servers = findServers(identifier);

        if (servers == null || servers.isEmpty()) {
            log.debug("No server found for {}: {}", getIdentifierType(), identifier);
            return null;
        }

        if (servers.size() == 1) {
            final Server server = servers.getFirst();
            log.debug("Found server with {}: {} -> ID: {}, Name: {}", getIdentifierType(), identifier, server.getId(), server.getName());
            return server;
        }

        log.warn("Multiple servers found for {}: {}", getIdentifierType(), identifier);
        return null;
    }
}
