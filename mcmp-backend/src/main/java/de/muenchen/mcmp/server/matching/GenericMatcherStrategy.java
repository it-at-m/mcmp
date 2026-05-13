
package de.muenchen.mcmp.server.matching;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class GenericMatcherStrategy<T> extends AbstractMultiResultMatcher<T> implements ServerMatcher<T> {

    private final Function<T, String> identifierExtractor;
    private final String identifierType;
    private final Function<String, List<Server>> serverFinder;
    private final Function<String, String> identifierProcessor;

    public GenericMatcherStrategy(
            ServerService serverService,
            Function<T, String> identifierExtractor,
            String identifierType,
            Function<String, List<Server>> serverFinder) {
        this(serverService, identifierExtractor, identifierType, serverFinder, Function.identity());
    }

    public GenericMatcherStrategy(
            ServerService serverService,
            Function<T, String> identifierExtractor,
            String identifierType,
            Function<String, List<Server>> serverFinder,
            Function<String, String> identifierProcessor) {
        super(serverService);
        this.identifierExtractor = identifierExtractor;
        this.identifierType = identifierType;
        this.serverFinder = serverFinder;
        this.identifierProcessor = identifierProcessor;
    }

    @Override
    protected String getIdentifier(T data) {
        return identifierExtractor.apply(data);
    }

    @Override
    protected String getIdentifierType() {
        return identifierType;
    }

    @Override
    protected List<Server> findServers(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return List.of();
        }

        String processedIdentifier = identifierProcessor.apply(identifier);
        if (processedIdentifier == null) {
            return List.of();
        }

        return serverFinder.apply(processedIdentifier);
    }
}