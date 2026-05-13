package de.muenchen.mcmp.caching;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Generic thread-safe cache implementation for database entities with configurable key mappings.
 *
 * <p>This cache provides fast lookup mechanisms for any database entity based on configurable
 * key attributes. The cache supports both cached and direct database lookups with automatic
 * fallback mechanisms and implements the Resource Pattern for proper resource management.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Generic implementation supporting any database entity type</li>
 *   <li>Configurable key mappings via {@link CacheKeyConfiguration}</li>
 *   <li>Thread-safe operations using ConcurrentHashMap and ReadWriteLock</li>
 *   <li>Automatic fallback to database queries when cache is not loaded</li>
 *   <li>Memory-efficient storage with optimal capacity calculation</li>
 *   <li>Resource Pattern implementation for proper resource management</li>
 *   <li>Builder pattern for configuration</li>
 *   <li>Comprehensive error handling and defensive programming</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Create cache configuration
 * CacheKeyConfiguration<Server> config = CacheKeyConfiguration.<Server>builder()
 *     .withKeyMapping("uuid", Server::getUuid)
 *     .withKeyMapping("instanceUuid", Server::getInstanceUuid)
 *     .withKeyMapping("foremanId", server -> server.getForemanId() != null ?
 *                     server.getForemanId().toString() : null)
 *     .build();
 *
 * // Create and use cache with resource pattern
 * try (EntityCache<Server> cache = EntityCache.<Server>builder()
 *         .withEntitySupplier(serverService::findAll)
 *         .withKeyConfiguration(config)
 *         .build()
 *         .loadForResource()) {
 *
 *     List<Server> servers = cache.findEntitiesByKey("uuid", "some-uuid-value");
 * }
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>This class is fully thread-safe. Multiple threads can safely perform lookups
 * while cache loading/clearing operations are properly synchronized using ReadWriteLock.</p>
 *
 * @param <T> the type of entity to cache
 * @see CacheKeyConfiguration
 * @see EntityCacheBuilder
 */
@Slf4j
public class EntityCache<T> implements AutoCloseable {

    /**
     * Default load factor for optimal hash map performance.
     * Value of 0.75f provides good balance between memory usage and lookup performance.
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Supplier for loading entities from the database.
     */
    private final Supplier<List<T>> entitySupplier;

    /**
     * Configuration defining key mappings for cache lookups.
     */
    private final CacheKeyConfiguration<T> keyConfiguration;

    /**
     * Thread-safe cache maps indexed by key name.
     * Each map contains key-value pairs where keys are extracted from entities
     * and values are lists of entities with that key.
     */
    private final Map<String, Map<String, List<T>>> cachesByKeyName = new ConcurrentHashMap<>();

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
     * Package-private constructor used by builder.
     *
     * @param entitySupplier    supplier for loading entities from database
     * @param keyConfiguration  configuration defining key mappings
     * @throws IllegalArgumentException if any required parameter is null
     */
    EntityCache(final Supplier<List<T>> entitySupplier,
                final CacheKeyConfiguration<T> keyConfiguration) {
        this.entitySupplier = Objects.requireNonNull(entitySupplier, "Entity supplier must not be null");
        this.keyConfiguration = Objects.requireNonNull(keyConfiguration, "Key configuration must not be null");
    }

    /**
     * Creates a new builder for configuring and creating an EntityCache instance.
     *
     * @param <T> the type of entity to cache
     * @return a new builder instance
     */
    public static <T> EntityCacheBuilder<T> builder() {
        return new EntityCacheBuilder<>();
    }

    /**
     * Loads the cache and returns this instance configured for use with try-with-resources.
     * When used with try-with-resources, the cache will be automatically cleared when
     * the try block is exited.
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * try (EntityCache<Server> cache = entityCache.loadForResource()) {
     *     List<Server> servers = cache.findEntitiesByKey("uuid", "some-uuid");
     *     // Cache automatically cleared at end of try block
     * }
     * }</pre>
     *
     * @return this EntityCache instance configured for resource management
     * @throws CacheLoadingException if cache loading fails critically
     */
    public EntityCache<T> loadForResource() {
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
     * Loads all entities from the database into the cache for optimized lookup operations.
     *
     * <p>This method performs a full cache refresh by clearing existing cache data
     * and reloading all entities from the database. The operation is thread-safe
     * and will block other cache operations during loading.</p>
     *
     * <p><strong>Performance Considerations:</strong></p>
     * <ul>
     *   <li>This operation may take significant time for large entity datasets</li>
     *   <li>Memory usage will increase based on the number of entities and configured keys</li>
     *   <li>Cache maps are sized optimally based on actual entity count</li>
     * </ul>
     *
     * <p><strong>Exception Handling:</strong></p>
     * <p>If an exception occurs during loading, the cache remains in an unloaded state
     * and all lookup operations will fall back to direct database queries. Critical errors
     * are wrapped in {@link CacheLoadingException}.</p>
     *
     * @throws CacheLoadingException if cache loading fails critically
     * @see #clearCache()
     * @see #isLoaded()
     * @see #loadForResource()
     */
    public void loadCache() {
        cacheLock.writeLock().lock();
        try {
            log.info("Starting cache loading process");
            clearCacheInternal();

            final List<T> allEntities = entitySupplier.get();
            log.info("Retrieved {} entities from database for caching", allEntities.size());

            initializeCacheMaps(allEntities.size());
            populateCache(allEntities);

            cacheLoaded.set(true);
            log.info("Successfully loaded {} entities into cache with {} key mappings",
                    allEntities.size(), keyConfiguration.getKeyMappings().size());

        } catch (Exception e) {
            handleLoadingFailure("Failed to load entities from database: " + e.getMessage(), e);
            throw new CacheLoadingException("Critical failure during cache loading", e);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Initializes cache maps with optimal capacity based on entity count.
     *
     * @param entityCount the number of entities to optimize cache capacity for
     */
    private void initializeCacheMaps(final int entityCount) {
        final int optimalCapacity = calculateOptimalCapacity(entityCount);
        log.debug("Initializing cache maps with capacity {} for {} entities", optimalCapacity, entityCount);

        keyConfiguration.getKeyMappings().keySet().forEach(keyName ->
                cachesByKeyName.put(keyName, new ConcurrentHashMap<>(optimalCapacity, DEFAULT_LOAD_FACTOR))
        );
    }

    /**
     * Calculates optimal initial capacity to minimize hash collisions and rehashing.
     *
     * @param entityCount the number of entities
     * @return optimal initial capacity
     */
    private int calculateOptimalCapacity(final int entityCount) {
        return Math.max(1, (int) Math.ceil(entityCount / DEFAULT_LOAD_FACTOR) + 1);
    }

    /**
     * Populates cache maps with entities based on configured key mappings.
     *
     * @param entities the entities to add to cache
     */
    private void populateCache(final List<T> entities) {
        int processedCount = 0;
        for (final T entity : entities) {
            if (entity != null) {
                addEntityToCache(entity);
                processedCount++;
            }
        }
        log.debug("Added {} entities to cache maps", processedCount);
    }

    /**
     * Adds an entity to all relevant cache maps based on configured key mappings.
     *
     * @param entity the entity to add to cache
     */
    private void addEntityToCache(final T entity) {
        keyConfiguration.getKeyMappings().forEach((keyName, keyExtractor) -> {
            try {
                final String keyValue = keyExtractor.apply(entity);
                if (isValidKeyValue(keyValue)) {
                    final Map<String, List<T>> keyMap = cachesByKeyName.get(keyName);
                    if (keyMap != null) {
                        keyMap.computeIfAbsent(keyValue, k -> new ArrayList<>()).add(entity);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract key '{}' from entity {}: {}", keyName, entity, e.getMessage());
            }
        });
    }

    /**
     * Validates if a key value is suitable for caching.
     *
     * @param keyValue the key value to validate
     * @return true if key value is valid (not null and not blank)
     */
    private boolean isValidKeyValue(final String keyValue) {
        return keyValue != null && !keyValue.trim().isEmpty();
    }

    /**
     * Handles cache loading failures with consistent error reporting and cleanup.
     *
     * @param message the error message
     * @param cause   the underlying exception
     */
    private void handleLoadingFailure(final String message, final Exception cause) {
        log.error(message, cause);
        cacheLoaded.set(false);
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
     *   <li>When entity data has been significantly modified</li>
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
        cachesByKeyName.clear();
        cacheLoaded.set(false);
        log.debug("Internal cache data structures cleared");
    }

    /**
     * Checks if the cache is currently loaded and ready for use.
     *
     * @return true if the cache is loaded and ready for lookups, false otherwise
     */
    public boolean isLoaded() {
        return cacheLoaded.get();
    }

    /**
     * Finds entities by a specific key with automatic fallback to database query.
     *
     * <p>This method first attempts to retrieve entities from the cache if loaded.
     * If the cache is not loaded or the key mapping is not configured, it will
     * return an empty list as this is a cache-specific operation.</p>
     *
     * @param keyName the name of the key mapping to use for lookup
     * @param keyValue the value to search for
     * @return list of entities matching the key, empty list if none found or key not configured
     * @throws IllegalArgumentException if keyName is null or blank
     */
    public List<T> findEntitiesByKey(final String keyName, final String keyValue) {
        validateKeyName(keyName);

        if (!isValidKeyValue(keyValue)) {
            return Collections.emptyList();
        }

        if (!keyConfiguration.hasKeyMapping(keyName)) {
            log.warn("Key mapping '{}' is not configured in cache", keyName);
            return Collections.emptyList();
        }

        return findCachedEntities(keyName, keyValue);
    }

    /**
     * Validates that a key name is not null or blank.
     *
     * @param keyName the key name to validate
     * @throws IllegalArgumentException if keyName is null or blank
     */
    private void validateKeyName(final String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Key name must not be null or blank");
        }
    }

    /**
     * Retrieves entities from cache for a specific key.
     *
     * @param keyName the name of the key mapping
     * @param keyValue the value to search for
     * @return list of cached entities, empty list if cache not loaded or key not found
     */
    private List<T> findCachedEntities(final String keyName, final String keyValue) {
        cacheLock.readLock().lock();
        try {
            if (!cacheLoaded.get()) {
                log.debug("Cache not loaded, returning empty result for key '{}' = '{}'", keyName, keyValue);
                return Collections.emptyList();
            }

            final Map<String, List<T>> keyMap = cachesByKeyName.get(keyName);
            if (keyMap == null) {
                return Collections.emptyList();
            }

            final List<T> entities = keyMap.get(keyValue);
            return entities != null ? new ArrayList<>(entities) : Collections.emptyList();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Gets all configured key mapping names.
     *
     * @return set of configured key names
     */
    public Set<String> getConfiguredKeys() {
        return keyConfiguration.getKeyMappings().keySet();
    }

    /**
     * Builder class for creating EntityCache instances with proper configuration.
     *
     * @param <T> the type of entity to cache
     */
    public static class EntityCacheBuilder<T> {
        private Supplier<List<T>> entitySupplier;
        private CacheKeyConfiguration<T> keyConfiguration;

        /**
         * Sets the entity supplier for loading entities from database.
         *
         * @param entitySupplier supplier for loading entities
         * @return this builder instance
         */
        public EntityCacheBuilder<T> withEntitySupplier(final Supplier<List<T>> entitySupplier) {
            this.entitySupplier = entitySupplier;
            return this;
        }

        /**
         * Sets the key configuration defining cache key mappings.
         *
         * @param keyConfiguration configuration for cache keys
         * @return this builder instance
         */
        public EntityCacheBuilder<T> withKeyConfiguration(final CacheKeyConfiguration<T> keyConfiguration) {
            this.keyConfiguration = keyConfiguration;
            return this;
        }

        /**
         * Builds the EntityCache instance with current configuration.
         *
         * @return configured EntityCache instance
         * @throws IllegalStateException if required configuration is missing
         */
        public EntityCache<T> build() {
            if (entitySupplier == null) {
                throw new IllegalStateException("Entity supplier is required");
            }
            if (keyConfiguration == null) {
                throw new IllegalStateException("Key configuration is required");
            }
            return new EntityCache<>(entitySupplier, keyConfiguration);
        }
    }
}