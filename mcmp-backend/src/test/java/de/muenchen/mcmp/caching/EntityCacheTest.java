package de.muenchen.mcmp.caching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for the EntityCache class.
 * Tests cover all public methods, edge cases, error conditions,
 * thread safety, and resource management functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EntityCache Tests")
class EntityCacheTest {

    @Mock
    private Supplier<List<TestEntity>> mockEntitySupplier;

    private CacheKeyConfiguration<TestEntity> keyConfiguration;
    private List<TestEntity> testEntities;

    @BeforeEach
    void setUp() {
        setupTestEntities();
        setupKeyConfiguration();
        // Setup default mock behavior
        lenient().when(mockEntitySupplier.get()).thenReturn(testEntities);
    }

    private void setupTestEntities() {
        testEntities = Arrays.asList(
                new TestEntity("1", "uuid-1", "instance-1", 100L),
                new TestEntity("2", "uuid-2", "instance-2", 200L),
                new TestEntity("3", "uuid-3", "instance-1", 300L), // Same instance
                new TestEntity("4", "uuid-4", null, 400L), // Null instance
                new TestEntity("5", "", "instance-3", null) // Empty ID, null value
        );
    }

    private void setupKeyConfiguration() {
        keyConfiguration = CacheKeyConfiguration.<TestEntity>builder()
                .withKeyMapping("id", TestEntity::id)
                .withKeyMapping("uuid", TestEntity::uuid)
                .withKeyMapping("instanceUuid", TestEntity::instanceUuid)
                .withKeyMapping("value", entity -> entity.value() != null ?
                        entity.value().toString() : null)
                .build();
    }

    private EntityCache<TestEntity> createEntityCache() {
        return EntityCache.<TestEntity>builder()
                .withEntitySupplier(mockEntitySupplier)
                .withKeyConfiguration(keyConfiguration)
                .build();
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create builder successfully")
        void shouldCreateBuilder() {
            EntityCache.EntityCacheBuilder<TestEntity> builder = EntityCache.builder();
            assertThat(builder).isNotNull();
        }

        @Test
        @DisplayName("Should build cache with valid configuration")
        void shouldBuildWithValidConfiguration() {
            try (EntityCache<TestEntity> cache = EntityCache.<TestEntity>builder()
                    .withEntitySupplier(Collections::emptyList)
                    .withKeyConfiguration(keyConfiguration)
                    .build()) {

                assertThat(cache).isNotNull();
                assertThat(cache.isLoaded()).isFalse();
            }
        }

        @Test
        @DisplayName("Should throw exception when entity supplier is null")
        @SuppressWarnings("resource")
        void shouldThrowWhenEntitySupplierIsNull() {
            assertThatThrownBy(() ->
                    EntityCache.<TestEntity>builder()
                            .withKeyConfiguration(keyConfiguration)
                            .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Entity supplier is required");
        }

        @Test
        @DisplayName("Should throw exception when key configuration is null")
        @SuppressWarnings("resource")
        void shouldThrowWhenKeyConfigurationIsNull() {
            assertThatThrownBy(() ->
                    EntityCache.<TestEntity>builder()
                            .withEntitySupplier(Collections::emptyList)
                            .build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Key configuration is required");
        }
    }

    @Nested
    @DisplayName("Cache Loading Tests")
    class CacheLoadingTests {

        @Test
        @DisplayName("Should load cache successfully")
        void shouldLoadCacheSuccessfully() {
            try (EntityCache<TestEntity> cache = createEntityCache()) {

                cache.loadCache();

                assertThat(cache.isLoaded()).isTrue();
            }
            verify(mockEntitySupplier).get();
        }

        @Test
        @DisplayName("Should handle null entities during loading")
        void shouldHandleNullEntitiesDuringLoading() {
            List<TestEntity> entitiesWithNulls = new ArrayList<>(testEntities);
            entitiesWithNulls.add(null);
            when(mockEntitySupplier.get()).thenReturn(entitiesWithNulls);

            try (EntityCache<TestEntity> cache = createEntityCache()) {

                assertThatCode(cache::loadCache).doesNotThrowAnyException();
                assertThat(cache.isLoaded()).isTrue();
            }
        }

        @Test
        @DisplayName("Should throw CacheLoadingException on supplier failure")
        void shouldThrowCacheLoadingExceptionOnSupplierFailure() {
            Logger entityCacheLogger = (Logger) LoggerFactory.getLogger(EntityCache.class);
            Level originalLevel = entityCacheLogger.getLevel();
            entityCacheLogger.setLevel(Level.OFF);

            try {
                when(mockEntitySupplier.get()).thenThrow(new RuntimeException("Database error"));
                try (EntityCache<TestEntity> cache = createEntityCache()) {

                    assertThatThrownBy(cache::loadCache)
                            .isInstanceOf(CacheLoadingException.class)
                            .hasMessage("Critical failure during cache loading")
                            .hasCauseInstanceOf(RuntimeException.class);

                    assertThat(cache.isLoaded()).isFalse();
                }
            } finally {
                entityCacheLogger.setLevel(originalLevel);
            }
        }

        @Test
        @DisplayName("Should clear cache before loading")
        void shouldClearCacheBeforeLoading() {
            try (EntityCache<TestEntity> cache = createEntityCache()) {

                cache.loadCache();
                assertThat(cache.isLoaded()).isTrue();

                // Second load should work
                cache.loadCache();
                assertThat(cache.isLoaded()).isTrue();
            }
            verify(mockEntitySupplier, times(2)).get();
        }
    }

    @Nested
    @DisplayName("Cache Clearing Tests")
    class CacheClearingTests {

        @Test
        @DisplayName("Should clear cache successfully")
        void shouldClearCacheSuccessfully() {
            try (EntityCache<TestEntity> cache = createEntityCache()) {
                cache.loadCache();
                assertThat(cache.isLoaded()).isTrue();

                cache.clearCache();
                assertThat(cache.isLoaded()).isFalse();
            }
        }

        @Test
        @DisplayName("Should handle clearing unloaded cache")
        void shouldHandleClearingUnloadedCache() {
            try (EntityCache<TestEntity> cache = createEntityCache()) {
                assertThat(cache.isLoaded()).isFalse();

                assertThatCode(cache::clearCache).doesNotThrowAnyException();
                assertThat(cache.isLoaded()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Entity Lookup Tests")
    class EntityLookupTests {

        private EntityCache<TestEntity> cache;

        @BeforeEach
        void loadCache() {
            cache = createEntityCache();
            cache.loadCache();
        }

        @Test
        @DisplayName("Should find entities by valid key")
        void shouldFindEntitiesByValidKey() {
            List<TestEntity> result = cache.findEntitiesByKey("uuid", "uuid-1");

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().uuid()).isEqualTo("uuid-1");
        }

        @Test
        @DisplayName("Should find multiple entities with same key value")
        void shouldFindMultipleEntitiesWithSameKeyValue() {
            List<TestEntity> result = cache.findEntitiesByKey("instanceUuid", "instance-1");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TestEntity::instanceUuid)
                    .allMatch("instance-1"::equals);
        }

        @Test
        @DisplayName("Should return empty list for non-existent key value")
        void shouldReturnEmptyListForNonExistentKeyValue() {
            List<TestEntity> result = cache.findEntitiesByKey("uuid", "non-existent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for unconfigured key")
        void shouldReturnEmptyListForUnconfiguredKey() {
            List<TestEntity> result = cache.findEntitiesByKey("unknownKey", "value");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception for null key name")
        void shouldThrowExceptionForNullKeyName() {
            assertThatThrownBy(() -> cache.findEntitiesByKey(null, "value"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Key name must not be null or blank");
        }

        @Test
        @DisplayName("Should throw exception for blank key name")
        void shouldThrowExceptionForBlankKeyName() {
            assertThatThrownBy(() -> cache.findEntitiesByKey("  ", "value"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Key name must not be null or blank");
        }

        @Test
        @DisplayName("Should return empty list for null key value")
        void shouldReturnEmptyListForNullKeyValue() {
            List<TestEntity> result = cache.findEntitiesByKey("uuid", null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list for blank key value")
        void shouldReturnEmptyListForBlankKeyValue() {
            List<TestEntity> result = cache.findEntitiesByKey("uuid", "  ");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when cache is not loaded")
        void shouldReturnEmptyListWhenCacheIsNotLoaded() {
            cache.clearCache();

            List<TestEntity> result = cache.findEntitiesByKey("uuid", "uuid-1");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return defensive copy of entities")
        void shouldReturnDefensiveCopyOfEntities() {
            List<TestEntity> result1 = cache.findEntitiesByKey("uuid", "uuid-1");
            List<TestEntity> result2 = cache.findEntitiesByKey("uuid", "uuid-1");

            assertThat(result1).isNotSameAs(result2);
            assertThat(result1).isEqualTo(result2);

            // Modifying result should not affect cache
            result1.clear();
            List<TestEntity> result3 = cache.findEntitiesByKey("uuid", "uuid-1");
            assertThat(result3).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty list for configured key with no cache map")
        void shouldReturnEmptyListForConfiguredKeyWithNoCacheMap() {
            // Create cache with key configuration but don't load it
            try (EntityCache<TestEntity> cache = createEntityCache()) {

                // Manually set cache as loaded but without proper initialization
                // This simulates the case where keyMap would be null
                cache.loadCache();
            }

            // Clear one specific key mapping from internal cache maps to simulate null keyMap
            // We need to use reflection or create a scenario where this happens naturally

            // Alternative: Create a cache configuration with a key that won't be populated
            CacheKeyConfiguration<TestEntity> configWithUnusedKey = CacheKeyConfiguration.<TestEntity>builder()
                    .withKeyMapping("id", TestEntity::id)
                    .withKeyMapping("unusedKey", entity -> "unused")
                    .build();

            List<TestEntity> result;
            try (EntityCache<TestEntity> cacheWithUnusedKey = EntityCache.<TestEntity>builder()
                    .withEntitySupplier(Collections::emptyList) // Empty entities
                    .withKeyConfiguration(configWithUnusedKey)
                    .build()) {

                cacheWithUnusedKey.loadCache();

                result = cacheWithUnusedKey.findEntitiesByKey("unusedKey", "someValue");
            }

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when cache is loaded but keyMap is null")
        void shouldReturnEmptyListWhenCacheIsLoadedButKeyMapIsNull() {
            // Create cache with empty entity list to ensure keyMaps are not initialized
            when(mockEntitySupplier.get()).thenReturn(Collections.emptyList());

            try (EntityCache<TestEntity> cache = createEntityCache()) {
                cache.loadCache();

                // Now the cache is loaded but keyMaps will be null/empty
                List<TestEntity> result = cache.findEntitiesByKey("uuid", "some-uuid");

                assertThat(result).isEmpty();
                assertThat(cache.isLoaded()).isTrue();
            }
        }

        @Test
        @DisplayName("Should handle race condition where keyMap is null after partial cache clear")
        void shouldHandleRaceConditionWhereKeyMapIsNullAfterPartialCacheClear() {
            EntityCache<TestEntity> cache = createEntityCache();
            cache.loadCache();

            // Verify it works initially
            List<TestEntity> initialResult = cache.findEntitiesByKey("uuid", "uuid-1");
            assertThat(initialResult).hasSize(1);

            // Clear the cache completely - this makes all keyMaps null
            cache.clearCache();
            assertThat(cache.isLoaded()).isFalse();

            // Set cache as loaded again without reinitializing maps
            // This simulates the race condition scenario
            try {
                java.lang.reflect.Field cacheLoadedField = EntityCache.class.getDeclaredField("cacheLoaded");
                cacheLoadedField.setAccessible(true);
                java.util.concurrent.atomic.AtomicBoolean cacheLoadedAtomicBoolean =
                        (java.util.concurrent.atomic.AtomicBoolean) cacheLoadedField.get(cache);
                cacheLoadedAtomicBoolean.set(true);

                // Now the cache reports as loaded but keyMaps are null
                List<TestEntity> result = cache.findEntitiesByKey("uuid", "uuid-1");

                assertThat(result).isEmpty();
                assertThat(cache.isLoaded()).isTrue();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                assertThat(e).as("Could not simulate race condition").isNull();
            }
        }



    }

    @Nested
    @DisplayName("Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @DisplayName("Should load for resource and auto-clear on close")
        void shouldLoadForResourceAndAutoClearOnClose() throws Exception {
            try (EntityCache<TestEntity> cache = createEntityCache()) {

                try (EntityCache<TestEntity> resourceCache = cache.loadForResource()) {
                    assertThat(resourceCache.isLoaded()).isTrue();
                    List<TestEntity> result = resourceCache.findEntitiesByKey("uuid", "uuid-1");
                    assertThat(result).hasSize(1);
                }

                // Cache should be cleared after try-with-resources
                assertThat(cache.isLoaded()).isFalse();
            }
        }

        @Test
        @DisplayName("Should not auto-clear when loaded manually")
        void shouldNotAutoClearWhenLoadedManually() throws Exception {
            EntityCache<TestEntity> cache = createEntityCache();
            cache.loadCache();
            assertThat(cache.isLoaded()).isTrue();

            cache.close(); // Manual close should not clear

            assertThat(cache.isLoaded()).isTrue();
        }

        @Test
        @DisplayName("Should handle multiple close calls gracefully")
        void shouldHandleMultipleCloseCallsGracefully() throws Exception {
            try (EntityCache<TestEntity> cache = createEntityCache()) {

                try (EntityCache<TestEntity> resourceCache = cache.loadForResource()) {
                    assertThat(resourceCache.isLoaded()).isTrue();
                }

                // Additional close calls should not cause issues
                assertThatCode(cache::close).doesNotThrowAnyException();
                assertThatCode(cache::close).doesNotThrowAnyException();
            }
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should return configured key names")
        void shouldReturnConfiguredKeyNames() {
            Set<String> configuredKeys;
            try (EntityCache<TestEntity> cache = createEntityCache()) {
                configuredKeys = cache.getConfiguredKeys();
            }

            assertThat(configuredKeys).containsExactlyInAnyOrder("id", "uuid", "instanceUuid", "value");
        }

        @Test
        @DisplayName("Should return immutable set of configured keys")
        void shouldReturnImmutableSetOfConfiguredKeys() {
            Set<String> configuredKeys;
            try (EntityCache<TestEntity> cache = createEntityCache()) {
                configuredKeys = cache.getConfiguredKeys();
            }

            assertThatThrownBy(() -> configuredKeys.add("newKey"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle key extraction exceptions gracefully")
        void shouldHandleKeyExtractionExceptionsGracefully() {
            CacheKeyConfiguration<TestEntity> faultyConfig = CacheKeyConfiguration.<TestEntity>builder()
                    .withKeyMapping("id", TestEntity::id)
                    .withKeyMapping("faulty", entity -> {
                        throw new RuntimeException("Key extraction failed");
                    })
                    .build();

            List<TestEntity> result;
            try (EntityCache<TestEntity> faultyCache = EntityCache.<TestEntity>builder()
                    .withEntitySupplier(mockEntitySupplier)
                    .withKeyConfiguration(faultyConfig)
                    .build()) {

                // Should not throw exception, just log warning
                assertThatCode(faultyCache::loadCache).doesNotThrowAnyException();
                assertThat(faultyCache.isLoaded()).isTrue();

                // Other keys should still work
                result = faultyCache.findEntitiesByKey("id", "1");
            }
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent cache operations safely")
        void shouldHandleConcurrentCacheOperationsSafely() throws InterruptedException, ExecutionException, TimeoutException {
            final int threadCount = 5;
            final int operationsPerThread = 10;
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            final List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Create a single cache instance for all threads
            List<TestEntity> result;
            try (EntityCache<TestEntity> sharedCache = createEntityCache()) {
                sharedCache.loadCache();

                // Start concurrent operations
                for (int i = 0; i < threadCount; i++) {
                    final int threadId = i;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        for (int j = 0; j < operationsPerThread; j++) {
                            if (threadId % 3 == 0) {
                                // Read operations
                                sharedCache.findEntitiesByKey("uuid", "uuid-1");
                                sharedCache.isLoaded();
                                sharedCache.getConfiguredKeys();
                            } else if (threadId % 3 == 1) {
                                // Cache reload operations
                                sharedCache.loadCache();
                            } else {
                                // Clear and load operations
                                sharedCache.clearCache();
                                sharedCache.loadCache();
                            }
                        }
                    }, executor);
                    futures.add(future);
                }

                // Wait for all operations to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.SECONDS);

                executor.shutdown();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

                // Verify cache is still functional
                assertThat(sharedCache.isLoaded()).isTrue();
                result = sharedCache.findEntitiesByKey("uuid", "uuid-1");
            }
            assertThat(result).hasSize(1);
        }
    }

    /**
         * Test entity class for testing purposes.
         */
        private record TestEntity(String id, String uuid, String instanceUuid, Long value) {

            public @NotNull String toString() {
                return "TestEntity{" +
                       "id='" + id + '\'' +
                       ", uuid='" + uuid + '\'' +
                       ", instanceUuid='" + instanceUuid + '\'' +
                       ", value=" + value +
                       '}';
            }
        }
}