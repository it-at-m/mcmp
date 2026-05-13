package de.muenchen.mcmp.clients.foreman;

import de.muenchen.mcmp.caching.CacheKeyConfiguration;
import de.muenchen.mcmp.caching.ConfigurableEntityCache;
import de.muenchen.mcmp.caching.CacheFallbackStrategy;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.server.matching.ServerMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * The ForemanServerCache class represents a cache layer for managing and accessing
 * server-related data efficiently. It integrates with the {@code ServerService} to
 * fetch and store server entities, allowing for both in-memory caching and direct
 * database lookups.
 *
 * This class supports various matching strategies using cached data or real-time
 * database queries, providing flexibility in accessing server information. It
 * implements {@code AutoCloseable} to ensure proper resource cleanup when used in
 * a try-with-resources block.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ForemanServerCache implements AutoCloseable {

    private final ServerService serverService;
    private ConfigurableEntityCache configurableCache;
    private volatile CacheKeyConfiguration<Server> keyConfiguration;

    /**
     * Lädt den Cache und gibt diese Instanz für try-with-resources zurück.
     */
    public ForemanServerCache loadForResource() {
        if (keyConfiguration == null) {
            keyConfiguration = createKeyConfiguration();
        }

        if (configurableCache == null) {
            configurableCache = new ConfigurableEntityCache(
                    serverService::findAll,
                    keyConfiguration,
                    new ForemanFallbackStrategy()
            );
        }

        configurableCache.loadForResource();
        return this;
    }

    /**
     * Creates a {@code ServerMatcher} instance that matches objects based on a combination
     * of their foreman source and ID. The matcher uses a caching mechanism to optimize lookups.
     *
     * @param <T> the type of objects to be matched
     * @param idExtractor a function to extract the ID from an object of type {@code T}
     * @param sourceExtractor a function to extract the source from an object of type {@code T}
     * @return a {@code ServerMatcher} instance configured to match objects based on the source
     *         and ID combination in the format "source:ID"
     */
    public <T> ServerMatcher<T> createForemanSourceIdMatcher(Function<T, Object> idExtractor, Function<T, String> sourceExtractor) {
        return configurableCache.createCachedMatcherWithTransform(
                "foremanSourceId",
                "Foreman Source:ID",
                dto -> {
                    Object id = idExtractor.apply(dto);
                    String source = sourceExtractor.apply(dto);
                    if (id != null && source != null) {
                        return source + ":" + id.toString();
                    }
                    return null;
                },
                Object::toString
        );
    }

    /**
     * Creates a {@code ServerMatcher} instance that matches objects based on their instance UUID.
     * The matcher uses a caching mechanism to optimize lookups, relying on the provided key extractor
     * function to retrieve the instance UUID from the given objects.
     *
     * @param <T> the type of objects to be matched
     * @param keyExtractor a function that extracts the instance UUID as a string from an object of type {@code T}
     * @return a {@code ServerMatcher} instance configured for instance UUID-based lookup and matching
     */
    public <T> ServerMatcher<T> createInstanceUuidMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createCachedMatcher(
                "instanceUuid",
                "Instance UUID",
                keyExtractor
        );
    }

    /**
     * Creates a ServerMatcher instance for UUID-based matching with cache lookups.
     * Uses the provided keyExtractor function to extract the UUID key from the given object.
     *
     * @param keyExtractor a function that extracts a UUID string from an object of type T
     * @return a ServerMatcher instance configured for UUID-based lookup and matching
     */
    public <T> ServerMatcher<T> createUuidMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createCachedMatcher(
                "uuid",
                "UUID",
                keyExtractor
        );
    }

    /**
     * Creates a {@code ServerMatcher} instance that matches objects based on their MAC address.
     * The matcher uses a caching mechanism to optimize lookups, leveraging the provided key extractor
     * function to retrieve the MAC address from the given objects.
     *
     * @param <T> the type of objects to be matched
     * @param keyExtractor a function that extracts the MAC address as a string from an object of type {@code T}
     * @return a {@code ServerMatcher} instance configured for MAC address-based lookup and matching
     */
    public <T> ServerMatcher<T> createMacAddressMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createDirectMatcher(
                "MAC Address",
                keyExtractor,
                serverService::findServersByMacAddress
        );
    }

    /**
     * Creates a {@code ServerMatcher} instance that matches objects based on their IP address.
     * The matcher uses a caching mechanism to optimize lookups, leveraging the provided key extractor
     * function to retrieve the IP address from the given objects.
     *
     * @param <T> the type of objects to be matched
     * @param keyExtractor a function that extracts the IP address as a string from an object of type {@code T}
     * @return a {@code ServerMatcher} instance configured for IP address-based lookup and matching
     */
    public <T> ServerMatcher<T> createIpAddressMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createDirectMatcher(
                "IP Address",
                keyExtractor,
                serverService::findServersByIpAddress
        );
    }

    /**
     * Checks whether the cache is fully loaded and accessible.
     *
     * @return {@code true} if the cache is loaded; {@code false} otherwise
     */
    public boolean isLoaded() {
        return configurableCache != null && configurableCache.isLoaded();
    }

    @Override
    public void close() {
        if (configurableCache != null) {
            configurableCache.close();
        }
    }

    /**
     * Configures and creates a {@code CacheKeyConfiguration} for {@code Server} objects with key mappings for
     * UUID, instance UUID, and foreman source ID. Each key mapping is associated with a function that extracts
     * the corresponding value from a {@code Server} object.
     *
     * @return a {@code CacheKeyConfiguration} instance configured with specific key mappings for {@code Server} objects
     */
    private CacheKeyConfiguration<Server> createKeyConfiguration() {
        return CacheKeyConfiguration.<Server>builder()
                .withKeyMapping("uuid", Server::getUuid)
                .withKeyMapping("instanceUuid", Server::getInstanceUuid)
                .withKeyMapping("foremanSourceId", server -> {
                    if (server.getForemanSource() != null && server.getForemanId() != null) {
                        return server.getForemanSource() + ":" + server.getForemanId().toString();
                    }
                    return null;
                })
                .build();
    }

    /**
     * Implementation of the {@code CacheFallbackStrategy} interface specifically designed for the
     * fallback strategy of a Foreman server cache. This class provides mechanisms to query the
     * database when the cache is not available, allowing alternative lookups based on specific key types.
     *
     * The lookup is determined by the key name provided and supports the following key types:
     * - {@code uuid}: Retrieves servers by their UUID.
     * - {@code instanceUuid}: Retrieves servers by their instance UUID.
     * - {@code foremanSourceId}: Retrieves servers by a combination of Foreman source and ID, separated by a colon.
     *
     * If the key format is invalid or the key name is unknown, appropriate warnings are logged, and
     * an empty list is returned.
     *
     * Fallback strategy is integrated into the {@code ForemanServerCache} class to ensure that server
     * lookups remain functional even when cache queries fail.
     */
    private class ForemanFallbackStrategy implements CacheFallbackStrategy<Server> {
        @Override
        public List<Server> findByKey(String keyName, String keyValue) {
            return switch (keyName) {
                case "uuid" -> serverService.findServersByUuid(keyValue);
                case "instanceUuid" -> serverService.findServersByInstanceUuid(keyValue);
                case "foremanSourceId" -> {
                    try {
                        String[] parts = keyValue.split(":", 2);
                        if (parts.length == 2) {
                            String source = parts[0];
                            Long foremanId = Long.parseLong(parts[1]);
                            yield serverService.findServersByForemanSourceAndId(source, foremanId);
                        }
                        log.warn("Invalid Foreman Source:ID format: {}", keyValue);
                        yield List.of();
                    } catch (NumberFormatException e) {
                        log.warn("Invalid Foreman ID format: {}", keyValue);
                        yield List.of();
                    }
                }
                default -> {
                    log.warn("Unknown key name for fallback: {}", keyName);
                    yield List.of();
                }
            };
        }
    }
}