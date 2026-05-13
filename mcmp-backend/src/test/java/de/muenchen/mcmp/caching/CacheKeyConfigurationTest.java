package de.muenchen.mcmp.caching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CacheKeyConfiguration Tests")
class CacheKeyConfigurationTest {

    private CacheKeyConfiguration<TestEntity> cacheKeyConfiguration;

    @BeforeEach
    void setUp() {
        cacheKeyConfiguration = CacheKeyConfiguration.<TestEntity>builder()
                .withKeyMapping("id", TestEntity::id)
                .withKeyMapping("uuid", TestEntity::uuid)
                .withKeyMapping("name", TestEntity::name)
                .build();
    }

    @Nested
    @DisplayName("Constructor and Builder Tests")
    class ConstructorAndBuilderTests {

        @Test
        @DisplayName("Should create configuration with valid key mappings")
        void shouldCreateConfigurationWithValidKeyMappings() {
            // Given
            CacheKeyConfiguration<TestEntity> config = CacheKeyConfiguration.<TestEntity>builder()
                    .withKeyMapping("testKey", TestEntity::id)
                    .build();

            // When & Then
            assertNotNull(config);
            assertTrue(config.hasKeyMapping("testKey"));
        }

        @Test
        @DisplayName("Should throw exception when building without key mappings")
        void shouldThrowExceptionWhenBuildingWithoutKeyMappings() {
            // Given
            CacheKeyConfiguration.CacheKeyConfigurationBuilder<TestEntity> builder =
                    CacheKeyConfiguration.builder();

            // When & Then
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    builder::build);
            assertEquals("At least one key mapping must be configured", exception.getMessage());
        }

        @Test
        @DisplayName("Should validate key name is not null")
        void shouldValidateKeyNameIsNotNull() {
            // Given
            CacheKeyConfiguration.CacheKeyConfigurationBuilder<TestEntity> builder =
                    CacheKeyConfiguration.builder();

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.withKeyMapping(null, TestEntity::id));
            assertEquals("Key name must not be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should validate key name is not blank")
        void shouldValidateKeyNameIsNotBlank() {
            // Given
            CacheKeyConfiguration.CacheKeyConfigurationBuilder<TestEntity> builder =
                    CacheKeyConfiguration.builder();

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.withKeyMapping("   ", TestEntity::id));
            assertEquals("Key name must not be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should validate key extractor is not null")
        void shouldValidateKeyExtractorIsNotNull() {
            // Given
            CacheKeyConfiguration.CacheKeyConfigurationBuilder<TestEntity> builder =
                    CacheKeyConfiguration.builder();

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.withKeyMapping("testKey", null));
            assertEquals("Key extractor must not be null", exception.getMessage());
        }

        @Test
        @DisplayName("Should allow chaining of key mappings")
        void shouldAllowChainingOfKeyMappings() {
            // Given & When
            CacheKeyConfiguration<TestEntity> config = CacheKeyConfiguration.<TestEntity>builder()
                    .withKeyMapping("id", TestEntity::id)
                    .withKeyMapping("uuid", TestEntity::uuid)
                    .withKeyMapping("name", TestEntity::name)
                    .build();

            // Then
            assertAll(
                    () -> assertTrue(config.hasKeyMapping("id")),
                    () -> assertTrue(config.hasKeyMapping("uuid")),
                    () -> assertTrue(config.hasKeyMapping("name"))
            );
        }
    }

    @Nested
    @DisplayName("Key Mapping Tests")
    class KeyMappingTests {

        @Test
        @DisplayName("Should return true for existing key mapping")
        void shouldReturnTrueForExistingKeyMapping() {
            // When & Then
            assertTrue(cacheKeyConfiguration.hasKeyMapping("id"));
            assertTrue(cacheKeyConfiguration.hasKeyMapping("uuid"));
            assertTrue(cacheKeyConfiguration.hasKeyMapping("name"));
        }

        @Test
        @DisplayName("Should return false for non-existing key mapping")
        void shouldReturnFalseForNonExistingKeyMapping() {
            // When & Then
            assertFalse(cacheKeyConfiguration.hasKeyMapping("nonExistentKey"));
        }

        @Test
        @DisplayName("Should return key extractor for existing key")
        void shouldReturnKeyExtractorForExistingKey() {
            // When
            Function<TestEntity, String> extractor = cacheKeyConfiguration.getKeyExtractor("id");

            // Then
            assertNotNull(extractor);
        }

        @Test
        @DisplayName("Should return null for non-existing key")
        void shouldReturnNullForNonExistingKey() {
            // When
            Function<TestEntity, String> extractor = cacheKeyConfiguration.getKeyExtractor("nonExistentKey");

            // Then
            assertNull(extractor);
        }

        @Test
        @DisplayName("Should return immutable map of key mappings")
        void shouldReturnImmutableMapOfKeyMappings() {
            // When
            Map<String, Function<TestEntity, String>> keyMappings = cacheKeyConfiguration.getKeyMappings();

            // Then
            assertEquals(3, keyMappings.size());
            assertThrows(UnsupportedOperationException.class,
                    () -> keyMappings.put("newKey", TestEntity::id));
        }
    }

    @Nested
    @DisplayName("Key Extraction Tests")
    class KeyExtractionTests {

        private TestEntity testEntity;

        @BeforeEach
        void setUp() {
            testEntity = new TestEntity("test-id", "test-uuid", "test-name");
        }

        @Test
        @DisplayName("Should extract key values correctly")
        void shouldExtractKeyValuesCorrectly() {
            // Given
            Function<TestEntity, String> idExtractor = cacheKeyConfiguration.getKeyExtractor("id");
            Function<TestEntity, String> uuidExtractor = cacheKeyConfiguration.getKeyExtractor("uuid");
            Function<TestEntity, String> nameExtractor = cacheKeyConfiguration.getKeyExtractor("name");

            // When & Then
            assertAll(
                    () -> assertEquals("test-id", idExtractor.apply(testEntity)),
                    () -> assertEquals("test-uuid", uuidExtractor.apply(testEntity)),
                    () -> assertEquals("test-name", nameExtractor.apply(testEntity))
            );
        }

        @Test
        @DisplayName("Should handle null entity gracefully")
        void shouldHandleNullEntityGracefully() {
            // Given
            Function<TestEntity, String> extractor = cacheKeyConfiguration.getKeyExtractor("id");

            // When & Then
            assertThrows(NullPointerException.class, () -> extractor.apply(null));
        }

        @Test
        @DisplayName("Should support null-safe key extraction")
        void shouldSupportNullSafeKeyExtraction() {
            // Given
            CacheKeyConfiguration<TestEntity> config = CacheKeyConfiguration.<TestEntity>builder()
                    .withKeyMapping("nullSafeId", entity -> entity != null ? entity.id() : null)
                    .build();
            Function<TestEntity, String> extractor = config.getKeyExtractor("nullSafeId");

            // When & Then
            assertAll(
                    () -> assertEquals("test-id", extractor.apply(testEntity)),
                    () -> assertNull(extractor.apply(null))
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty string as key name after trimming")
        void shouldHandleEmptyStringAsKeyNameAfterTrimming() {
            // Given
            CacheKeyConfiguration.CacheKeyConfigurationBuilder<TestEntity> builder =
                    CacheKeyConfiguration.builder();

            // When & Then
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.withKeyMapping("", TestEntity::id));
            assertEquals("Key name must not be null or blank", exception.getMessage());
        }

        @Test
        @DisplayName("Should allow overwriting existing key mappings")
        void shouldAllowOverwritingExistingKeyMappings() {
            // Given
            Function<TestEntity, String> originalExtractor = TestEntity::id;
            Function<TestEntity, String> newExtractor = TestEntity::uuid;

            // When
            CacheKeyConfiguration<TestEntity> config = CacheKeyConfiguration.<TestEntity>builder()
                    .withKeyMapping("testKey", originalExtractor)
                    .withKeyMapping("testKey", newExtractor)
                    .build();

            // Then
            TestEntity entity = new TestEntity("id", "uuid", "name");
            Function<TestEntity, String> finalExtractor = config.getKeyExtractor("testKey");
            assertEquals("uuid", finalExtractor.apply(entity));
        }

        @Test
        @DisplayName("Should handle special characters in key names")
        void shouldHandleSpecialCharactersInKeyNames() {
            // Given & When
            CacheKeyConfiguration<TestEntity> config = CacheKeyConfiguration.<TestEntity>builder()
                    .withKeyMapping("key-with-dash", TestEntity::id)
                    .withKeyMapping("key_with_underscore", TestEntity::uuid)
                    .withKeyMapping("key.with.dot", TestEntity::name)
                    .build();

            // Then
            assertAll(
                    () -> assertTrue(config.hasKeyMapping("key-with-dash")),
                    () -> assertTrue(config.hasKeyMapping("key_with_underscore")),
                    () -> assertTrue(config.hasKeyMapping("key.with.dot"))
            );
        }
    }

        /**
         * Test entity class for testing purposes.
         */
        private record TestEntity(String id, String uuid, String name) {
    }
}