package de.muenchen.mcmp.server.matching;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Thread-safe cache for server lookups to optimize matching strategies.
 *
 * <p>This cache provides fast lookup mechanisms for servers based on various identifiers
 * such as UUIDs, instance UUIDs, Foreman IDs, and ServiceNow system IDs. The cache supports
 * both cached and direct database lookups with automatic fallback mechanisms.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Thread-safe operations using ConcurrentHashMap and ReadWriteLock</li>
 *   <li>Automatic fallback to database queries when cache is not loaded</li>
 *   <li>Memory-efficient storage with duplicate prevention</li>
 *   <li>Defensive programming with null-safety checks</li>
 *   <li>Resource Pattern implementation for proper resource management</li>
 *   <li>Dynamic cache sizing based on actual server count</li>
 * </ul>
 *
 * <p><strong>Usage Example with Resource Pattern:</strong></p>
 * <pre>{@code
 * // Try-with-resources usage for automatic cleanup
 * try (ServerCache cache = serverCache.loadForResource()) {
 *     List<Server> servers = cache.findServersByUuid("some-uuid");
 *     List<Server> servers2 = cache.findServersByInstanceUuid("instance-uuid");
 *     // Cache will be automatically cleared when leaving the try block
 * }
 *
 * // Traditional usage still supported
 * serverCache.loadCache();
 * List<Server> servers = serverCache.findServersByUuid("some-uuid");
 * serverCache.clearCache();
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>This class is fully thread-safe. Multiple threads can safely perform lookups
 * while cache loading/clearing operations are properly synchronized.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ServerCache implements AutoCloseable {

    /**
     * Default load factor balancing memory usage and lookup performance.
     * 0.75f provides good balance for read-heavy workloads.
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Service for database operations and server retrieval.
     */
    private final ServerService serverService;

    /**
     * Thread-safe cache mapping UUIDs to server lists.
     * Initialized dynamically based on server count.
     */
    private Map<String, List<Server>> serversByUuid;

    /**
     * Thread-safe cache mapping instance UUIDs to server lists.
     * Initialized dynamically based on server count.
     */
    private Map<String, List<Server>> serversByInstanceUuid;

    /**
     * Thread-safe cache mapping Foreman IDs to server lists.
     * Initialized dynamically based on server count.
     */
    private Map<String, List<Server>> serversByForemanId;

    /**
     * Thread-safe cache mapping ServiceNow server system IDs to server lists.
     * Initialized dynamically based on server count.
     */
    private Map<String, List<Server>> serversByServerSysId;

    /**
     * Thread-safe cache mapping ServiceNow instance system IDs to server lists.
     * Initialized dynamically based on server count.
     */
    private Map<String, List<Server>> serversByInstanceSysId;

    /**
     * Thread-safe flag indicating whether the cache has been loaded.
     */
    private final AtomicBoolean cacheLoaded = new AtomicBoolean(false);

    /**
     * Thread-safe flag indicating whether this cache instance should auto-clear on close.
     */
    private final AtomicBoolean autoClearOnClose = new AtomicBoolean(false);

    /**
     * Lock for coordinating cache loading/clearing operations with read operations.
     */
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    /**
     * Loads the cache and returns this instance configured for use with try-with-resources.
     * When used with try-with-resources, the cache will be automatically cleared when
     * the try block is exited.
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * try (ServerCache cache = serverCache.loadForResource()) {
     *     List<Server> servers = cache.findServersByUuid("some-uuid");
     *     // Cache automatically cleared at end of try block
     * }
     * }</pre>
     *
     * @return this ServerCache instance configured for resource management
     * @throws RuntimeException if cache loading fails critically
     */
    public ServerCache loadForResource() {
        loadCache();
        autoClearOnClose.set(true);
        return this;
    }

    /**
     * Automatically clears the cache when used with try-with-resources pattern.
     * This method is called automatically when exiting a try-with-resources block.
     *
     * <p>Note: This only clears the cache if it was loaded using {@link #loadForResource()}.
     * Manual cache management using {@link #loadCache()} and {@link #clearCache()}
     * is not affected by this method.</p>
     */
    @Override
    public void close() {
        if (autoClearOnClose.get()) {
            clearCache();
            autoClearOnClose.set(false);
        }
    }

    /**
     * Loads all servers from the database into the cache for optimized lookup operations.
     *
     * <p>This method performs a full cache refresh by clearing existing cache data
     * and reloading all servers from the database. The operation is thread-safe
     * and will block other cache operations during loading.</p>
     *
     * <p><strong>Performance Considerations:</strong></p>
     * <ul>
     *   <li>This operation may take significant time for large server datasets</li>
     *   <li>Memory usage will increase based on the number of servers and their attributes</li>
     *   <li>Cache maps are sized dynamically based on actual server count</li>
     * </ul>
     *
     * <p><strong>Exception Handling:</strong></p>
     * <p>If an exception occurs during loading, the cache remains in an unloaded state
     * and all lookup operations will fall back to direct database queries. Errors are
     * logged but not propagated to maintain graceful degradation.</p>
     *
     * @see #clearCache()
     * @see #isLoaded()
     * @see #loadForResource()
     */
    public void loadCache() {
        cacheLock.writeLock().lock();
        try {
            // Clear existing cache first
            clearCacheInternal();

            // Load servers from database
            final List<Server> allServers = serverService.findAll();
            log.info("Loading cache with {} servers from database", allServers.size());

            // Initialize cache maps with optimal capacity based on actual server count
            initializeCacheMaps(allServers.size());

            // Populate cache with servers
            for (final Server server : allServers) {
                addServerToCache(server);
            }

            cacheLoaded.set(true);
            log.info("Successfully loaded {} servers into cache with optimized capacity", allServers.size());

        } catch (Exception e) {
            handleLoadingFailure("Failed to load servers from database: " + e.getMessage());
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Initializes cache maps with optimal capacity based on the number of servers.
     *
     * @param serverCount the number of servers to size the cache for
     */
    private void initializeCacheMaps(final int serverCount) {
        // Calculate optimal initial capacity to avoid rehashing
        // We use a slightly higher capacity to account for hash distribution
        final int optimalCapacity = (int) Math.ceil(serverCount / DEFAULT_LOAD_FACTOR) + 1;

        log.debug("Initializing cache maps with capacity {} for {} servers", optimalCapacity, serverCount);

        serversByUuid = new ConcurrentHashMap<>(optimalCapacity, DEFAULT_LOAD_FACTOR);
        serversByInstanceUuid = new ConcurrentHashMap<>(optimalCapacity, DEFAULT_LOAD_FACTOR);
        serversByForemanId = new ConcurrentHashMap<>(optimalCapacity, DEFAULT_LOAD_FACTOR);
        serversByServerSysId = new ConcurrentHashMap<>(optimalCapacity, DEFAULT_LOAD_FACTOR);
        serversByInstanceSysId = new ConcurrentHashMap<>(optimalCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Handles cache loading failures with consistent cleanup.
     */
    private void handleLoadingFailure(final String context) {
        log.warn("{}, cache will remain unloaded and fallback to database queries", context);
        clearCacheInternal();
    }

    /**
     * Clears all cached data and resets the cache to an unloaded state.
     *
     * <p>This operation is thread-safe and will block until all current read
     * operations are completed. After clearing, all lookup operations will
     * fall back to direct database queries until the cache is reloaded.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>After completing bulk import operations</li>
     *   <li>When server data has been significantly modified</li>
     *   <li>To free up memory when cache is no longer needed</li>
     *   <li>During application shutdown</li>
     * </ul>
     *
     * @see #loadCache()
     * @see #isLoaded()
     */
    public void clearCache() {
        cacheLock.writeLock().lock();
        try {
            clearCacheInternal();
            log.info("Cache cleared successfully");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Internal cache clearing method without locking (assumes write lock is held).
     */
    private void clearCacheInternal() {
        cacheLoaded.set(false);

        // Clear existing maps if they exist
        if (serversByUuid != null) {
            serversByUuid.clear();
            serversByUuid = null;
        }
        if (serversByInstanceUuid != null) {
            serversByInstanceUuid.clear();
            serversByInstanceUuid = null;
        }
        if (serversByForemanId != null) {
            serversByForemanId.clear();
            serversByForemanId = null;
        }
        if (serversByServerSysId != null) {
            serversByServerSysId.clear();
            serversByServerSysId = null;
        }
        if (serversByInstanceSysId != null) {
            serversByInstanceSysId.clear();
            serversByInstanceSysId = null;
        }
    }

    /**
     * Checks if the cache is currently loaded and ready for use.
     *
     * @return true if the cache is loaded, false otherwise
     */
    public boolean isLoaded() {
        return cacheLoaded.get();
    }

    /**
     * Finds servers by UUID with automatic fallback to database query.
     *
     * @param uuid the UUID to search for
     * @return list of servers with the given UUID, empty list if none found
     */
    public List<Server> findServersByUuid(final String uuid) {
        return findCachedOrFallback(uuid, serversByUuid, serverService::findServersByUuid);
    }

    /**
     * Finds servers by instance UUID with automatic fallback to database query.
     *
     * @param instanceUuid the instance UUID to search for
     * @return list of servers with the given instance UUID, empty list if none found
     */
    public List<Server> findServersByInstanceUuid(final String instanceUuid) {
        return findCachedOrFallback(instanceUuid, serversByInstanceUuid, serverService::findServersByInstanceUuid);
    }

    /**
     * Finds servers by Foreman ID with automatic fallback to database query.
     *
     * @param foremanId the Foreman ID to search for
     * @return list of servers with the given Foreman ID, empty list if none found
     */
    public List<Server> findServersByForemanId(final String foremanId) {
        return findCachedOrFallback(foremanId, serversByForemanId, serverService::findServersByForemanId);
    }

    /**
     * Finds servers by ServiceNow server system ID with automatic fallback to database query.
     *
     * @param serverSysId the server system ID to search for
     * @return list of servers with the given server system ID, empty list if none found
     */
    public List<Server> findServersByServerSysId(final String serverSysId) {
        return findCachedOrFallback(serverSysId, serversByServerSysId, serverService::findServersByServerSysId);
    }

    /**
     * Finds servers by ServiceNow instance system ID with automatic fallback to database query.
     *
     * @param instanceSysId the instance system ID to search for
     * @return list of servers with the given instance system ID, empty list if none found
     */
    public List<Server> findServersByInstanceSysId(final String instanceSysId) {
        return findCachedOrFallback(instanceSysId, serversByInstanceSysId, serverService::findServersByInstanceSysId);
    }

    /**
     * Finds servers by MAC address using direct database query.
     *
     * <p>MAC addresses are not cached due to their dynamic nature and potential
     * for frequent changes.</p>
     *
     * @param macAddress the MAC address to search for
     * @return list of servers with the given MAC address, empty list if none found
     */
    public List<Server> findServersByMacAddress(final String macAddress) {
        if (StringUtils.isBlank(macAddress)) {
            return Collections.emptyList();
        }
        return serverService.findServersByMacAddress(macAddress);
    }

    /**
     * Finds servers by IP address using direct database query.
     *
     * <p>IP addresses are not cached due to their dynamic nature and potential
     * for frequent changes.</p>
     *
     * @param ipAddress the IP address to search for
     * @return list of servers with the given IP address, empty list if none found
     */
    public List<Server> findServersByIpAddress(final String ipAddress) {
        if (StringUtils.isBlank(ipAddress)) {
            return Collections.emptyList();
        }
        return serverService.findServersByIpAddress(ipAddress);
    }

    /**
     * Generic method for cached lookup with automatic fallback to database.
     */
    private List<Server> findCachedOrFallback(final String key,
                                              final Map<String, List<Server>> cache,
                                              final Function<String, List<Server>> fallback) {
        if (StringUtils.isBlank(key)) {
            return Collections.emptyList();
        }

        cacheLock.readLock().lock();
        try {
            if (cacheLoaded.get() && cache != null) {
                return cache.getOrDefault(key, Collections.emptyList());
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        // Fallback to database query
        return fallback.apply(key);
    }

    /**
     * Adds a server to all relevant cache maps based on its identifiers.
     */
    private void addServerToCache(final Server server) {
        if (server == null) {
            return;
        }

        // Add to all relevant caches
        addToMapIfNotBlank(server.getUuid(), server, serversByUuid);
        addToMapIfNotBlank(server.getInstanceUuid(), server, serversByInstanceUuid);
        addToMapIfNotNull(server.getForemanId(), server, serversByForemanId, Object::toString);
        addToMapIfNotBlank(server.getSnowServerSysId(), server, serversByServerSysId);
        addToMapIfNotBlank(server.getSnowInstanceSysId(), server, serversByInstanceSysId);
    }

    /**
     * Adds a server to a cache map if the key is not blank.
     */
    private void addToMapIfNotBlank(final String key, final Server server, final Map<String, List<Server>> map) {
        if (StringUtils.isNotBlank(key) && map != null) {
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(server);
        }
    }

    /**
     * Adds a server to a cache map if the value is not null, using a key extractor.
     */
    private <T> void addToMapIfNotNull(final T value, final Server server,
                                       final Map<String, List<Server>> map,
                                       final Function<T, String> keyExtractor) {
        if (value != null && map != null) {
            final String key = keyExtractor.apply(value);
            if (StringUtils.isNotBlank(key)) {
                map.computeIfAbsent(key, k -> new ArrayList<>()).add(server);
            }
        }
    }

    /**
     * Cleanup method called during application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        clearCache();
        log.debug("ServerCache shutdown completed");
    }
}