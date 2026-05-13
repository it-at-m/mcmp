package de.muenchen.mcmp.clients.snow;

import de.muenchen.mcmp.caching.CacheKeyConfiguration;
import de.muenchen.mcmp.caching.ConfigurableEntityCache;
import de.muenchen.mcmp.caching.CacheFallbackStrategy;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import de.muenchen.mcmp.server.ServerUtils;
import de.muenchen.mcmp.server.matching.ServerMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * A fallback strategy implementation for {@code SnowServerCache} to handle database queries
 * when the cache is unavailable. This strategy provides logic to retrieve {@code Server}
 * entities based on specific key-value pairs.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SnowServerCache implements AutoCloseable {

    private final ServerService serverService;
    private ConfigurableEntityCache configurableCache;
    private volatile CacheKeyConfiguration<Server>  keyConfiguration;

    /**
     * Loads and initializes the cache configuration for server resources.
     * This method ensures that the key configuration and the configurable cache
     * are properly created and initialized. If the cache has already been initialized,
     * it refreshes the resource data for usage.
     *
     * @return the current instance of {@code SnowServerCache} with the cache loaded
     */
    public SnowServerCache loadForResource() {
        if (keyConfiguration == null) {
            keyConfiguration = createKeyConfiguration();
        }

        if (configurableCache == null) {
            configurableCache = new ConfigurableEntityCache(
                    serverService::findAll,
                    keyConfiguration,
                    new SnowFallbackStrategy()
            );
        }

        configurableCache.loadForResource();
        return this;
    }

    /**
     * Creates a matcher for associating data entities with servers based on their instance system IDs.
     * The matcher leverages a specified key extraction function to extract the instance system ID
     * from the provided data for matching purposes.
     *
     * @param keyExtractor a function that extracts the instance system ID as a {@code String}
     *                     from the input data of type {@code T}; must not be null
     * @return a {@code ServerMatcher<T>} that enables matching of servers based on instance system IDs
     */
    public <T> ServerMatcher<T> createInstanceSysIdMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createCachedMatcher(
                "instanceSysId",
                "ServiceNow Instance System ID",
                keyExtractor
        );
    }

    /**
     * Creates a server matcher for associating data entities with servers based on their system IDs.
     * The matcher utilizes a specified key extraction function to extract the system ID
     * from the provided data for matching purposes.
     *
     * @param keyExtractor a function that extracts the system ID as a {@code String}
     *                     from the input data of type {@code T}; must not be null
     * @return a {@code ServerMatcher<T>} that enables matching of servers based on system IDs
     */
    public <T> ServerMatcher<T> createServerSysIdMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createCachedMatcher(
                "serverSysId",
                "ServiceNow Server System ID",
                keyExtractor
        );
    }
    /**
     * Creates a matcher for associating data entities with servers based on their instance UUIDs.
     * The matcher uses a specified key extraction function to extract the instance UUID
     * from the provided data and performs matching.
     *
     * @param keyExtractor a function that extracts the instance UUID as a {@code String}
     *                     from the input data of type {@code T}; must not be null
     * @return a {@code ServerMatcher<T>} that enables matching of servers based on instance UUIDs
     */
    public <T> ServerMatcher<T> createInstanceUuidMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createCachedMatcher(
                "instanceUuid",
                "Instance UUID",
                keyExtractor
        );
    }

    /**
     * Creates a MAC Address Matcher that associates data entities with servers based on MAC addresses.
     * The matcher uses a specified key extraction function to extract the MAC address from the provided
     * data and matches it against a collection of servers.
     *
     * @param keyExtractor a function that extracts the MAC address as a {@code String} from the input data {@code T};
     *                     must not be null
     * @return a {@code ServerMatcher<T>} that enables matching of servers based on MAC addresses
     */
    public <T> ServerMatcher<T> createMacAddressMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createDirectMatcher(
                "MAC Address",
                keyExtractor,
                serverService::findServersByMacAddress
        );
    }

    /**
     * Creates an IP Address Matcher that associates data entities with servers based on their IP addresses.
     * The matcher uses a specified key extraction function to extract the IP address from the provided data
     * and matches it against a collection of servers.
     *
     * @param keyExtractor a function that extracts the IP address as a {@code String} from the input data {@code T};
     *                     must not be null
     * @return a {@code ServerMatcher<T>} that enables matching of servers based on IP addresses
     */
    public <T> ServerMatcher<T> createIpAddressMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createDirectMatcher(
                "IP Address",
                keyExtractor,
                serverService::findServersByIpAddress
        );
    }

    /**
     * Creates a Serial Number Matcher that associates data entities with servers based on their serial numbers.
     * The matcher uses a specified key extraction function to extract the serial number from the provided data
     * and matches it against a collection of servers.
     *
     * @param keyExtractor a function that extracts the serial number as a {@code String} from the input data {@code T};
     *                     must not be null
     * @return a {@code ServerMatcher<T>} that enables matching of servers based on serial numbers
     */
    public <T> ServerMatcher<T> createSerialNumberMatcher(Function<T, String> keyExtractor) {
        return configurableCache.createHybridMatcher(
                "Serial Number",
                keyExtractor,
                this::findServersBySerialNumber
        );
    }

    /**
     * Searches for and retrieves a list of servers that match the provided serial number.
     * If the serial number is valid, it is converted to a UUID, which is then used as the key
     * to query the server data from the cache. If the conversion fails or the serial number
     * is null, an empty list is returned.
     *
     * @param serialNumber the serial number of the server to be searched; can be null
     * @return a list of servers matching the given serial number; returns an empty list
     *         if no matching servers are found or if the serial number is null
     */
    private List<Server> findServersBySerialNumber(String serialNumber) {
        if (serialNumber != null) {
            try {
                String convertedUuid = ServerUtils.convertSerialToUUID(serialNumber);
                return configurableCache.findEntitiesByKey("uuid", convertedUuid);
            } catch (Exception e) {
                log.warn("Error converting serial number to UUID: {}", serialNumber, e);
                return List.of();
            }
        }
        return List.of();
    }

    /**
     * Checks whether the cache is loaded.
     *
     * @return true if the cache is loaded and ready for use; false otherwise
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
     * Creates and configures a {@link CacheKeyConfiguration} for {@link Server} objects.
     * This method defines the key mappings required for identifying and organizing
     * {@link Server} entities in the cache. Each key mapping specifies the property
     * of the {@link Server} object that will serve as a unique identifier or lookup key.
     *
     * @return a fully constructed {@link CacheKeyConfiguration} for {@link Server} objects
     */
    private CacheKeyConfiguration<Server> createKeyConfiguration() {
        return CacheKeyConfiguration.<Server>builder()
                .withKeyMapping("uuid", Server::getUuid)
                .withKeyMapping("instanceUuid", Server::getInstanceUuid)
                .withKeyMapping("serverSysId", Server::getSnowServerSysId)
                .withKeyMapping("instanceSysId", Server::getSnowInstanceSysId)
                .build();
    }

    /**
     * A fallback strategy implementation for {@code SnowServerCache} to handle database queries
     * when the cache is unavailable. This strategy provides logic to retrieve {@code Server}
     * entities based on specific key-value pairs.
     * <p>
     * Supported key names and their corresponding database query methods include:
     * <p>
     * - {@code "uuid"}: Retrieves servers by their universally unique identifier (UUID).
     * - {@code "instanceUuid"}: Retrieves servers by instance UUID.
     * - {@code "serverSysId"}: Retrieves servers by server system ID.
     * - {@code "instanceSysId"}: Retrieves servers by instance system ID.
     * <p>
     * If the key name does not match any of the supported values, an empty list is returned,
     * and a warning is logged.
     */
    private class SnowFallbackStrategy implements CacheFallbackStrategy<Server> {
        @Override
        public List<Server> findByKey(String keyName, String keyValue) {
            return switch (keyName) {
                case "uuid" -> serverService.findServersByUuid(keyValue);
                case "instanceUuid" -> serverService.findServersByInstanceUuid(keyValue);
                case "serverSysId" -> serverService.findServersByServerSysId(keyValue);
                case "instanceSysId" -> serverService.findServersByInstanceSysId(keyValue);
                default -> {
                    log.warn("Unknown key name for fallback: {}", keyName);
                    yield List.of();
                }
            };
        }
    }
}
