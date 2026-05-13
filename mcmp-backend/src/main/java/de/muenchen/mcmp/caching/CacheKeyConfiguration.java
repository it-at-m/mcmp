
package de.muenchen.mcmp.caching;

import java.util.*;
import java.util.function.Function;

/**
 * Configuration class defining key mappings for entity cache lookups.
 *
 * <p>This class encapsulates the mapping configuration that determines how entity
 * attributes are used as cache keys. Each mapping consists of a key name and a
 * function that extracts the key value from an entity.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * CacheKeyConfiguration<Server> config = CacheKeyConfiguration.<Server>builder()
 *     .withKeyMapping("uuid", Server::getUuid)
 *     .withKeyMapping("instanceUuid", Server::getInstanceUuid)
 *     .withKeyMapping("foremanId", server -> server.getForemanId() != null ?
 *                     server.getForemanId().toString() : null)
 *     .build();
 * }</pre>
 *
 * <p><strong>Key Design Principles:</strong></p>
 * <ul>
 *   <li>Immutable configuration once built</li>
 *   <li>Type-safe key extraction functions</li>
 *   <li>Validation of key mappings</li>
 *   <li>Support for null-safe key extraction</li>
 * </ul>
 *
 * @param <T> the type of entity for which keys are configured
 */
public class CacheKeyConfiguration<T> {

    /**
     * Immutable map of key mappings.
     * Key: name of the cache key
     * Value: function to extract the key value from an entity
     */
    private final Map<String, Function<T, String>> keyMappings;

    /**
     * Private constructor used by builder.
     *
     * @param keyMappings the key mappings to use
     */
    private CacheKeyConfiguration(final Map<String, Function<T, String>> keyMappings) {
        this.keyMappings = Collections.unmodifiableMap(new HashMap<>(keyMappings));
    }

    /**
     * Creates a new builder for configuring cache key mappings.
     *
     * @param <T> the type of entity for which keys are configured
     * @return a new builder instance
     */
    public static <T> CacheKeyConfigurationBuilder<T> builder() {
        return new CacheKeyConfigurationBuilder<>();
    }

    /**
     * Gets all configured key mappings.
     *
     * @return immutable map of key mappings
     */
    public Map<String, Function<T, String>> getKeyMappings() {
        return keyMappings;
    }

    /**
     * Checks if a key mapping with the given name exists.
     *
     * @param keyName the name of the key mapping to check
     * @return true if the key mapping exists, false otherwise
     */
    public boolean hasKeyMapping(final String keyName) {
        return keyMappings.containsKey(keyName);
    }

    /**
     * Gets the key extractor function for a specific key name.
     *
     * @param keyName the name of the key mapping
     * @return the key extractor function, or null if not found
     */
    public Function<T, String> getKeyExtractor(final String keyName) {
        return keyMappings.get(keyName);
    }

    /**
     * Builder class for creating CacheKeyConfiguration instances.
     *
     * @param <T> the type of entity for which keys are configured
     */
    public static class CacheKeyConfigurationBuilder<T> {
        private final Map<String, Function<T, String>> keyMappings = new HashMap<>();

        /**
         * Adds a key mapping configuration.
         *
         * @param keyName the name of the cache key (must not be null or blank)
         * @param keyExtractor function to extract key value from entity (must not be null)
         * @return this builder instance
         * @throws IllegalArgumentException if keyName is null/blank or keyExtractor is null
         */
        public CacheKeyConfigurationBuilder<T> withKeyMapping(final String keyName,
                                                              final Function<T, String> keyExtractor) {
            validateKeyName(keyName);
            validateKeyExtractor(keyExtractor);

            keyMappings.put(keyName, keyExtractor);
            return this;
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
         * Validates that a key extractor is not null.
         *
         * @param keyExtractor the key extractor to validate
         * @throws IllegalArgumentException if keyExtractor is null
         */
        private void validateKeyExtractor(final Function<T, String> keyExtractor) {
            if (keyExtractor == null) {
                throw new IllegalArgumentException("Key extractor must not be null");
            }
        }

        /**
         * Builds the CacheKeyConfiguration instance with current mappings.
         *
         * @return configured CacheKeyConfiguration instance
         * @throws IllegalStateException if no key mappings have been configured
         */
        public CacheKeyConfiguration<T> build() {
            if (keyMappings.isEmpty()) {
                throw new IllegalStateException("At least one key mapping must be configured");
            }
            return new CacheKeyConfiguration<>(keyMappings);
        }
    }
}