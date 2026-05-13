package de.muenchen.mcmp.caching;

import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.matching.ServerMatcher;
import de.muenchen.mcmp.server.matching.GenericMatcherStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Konfigurierbare Entity-Cache-Wrapper-Klasse, die die gemeinsame Funktionalität
 * für spezialisierte Cache-Implementierungen bereitstellt.
 *
 * <p>Diese Klasse eliminiert Duplikation zwischen verschiedenen Cache-Implementierungen
 * und stellt eine einheitliche API für Cache-Operationen und Matcher-Erstellung bereit.</p>
 *
 * <p><strong>Hinweis:</strong> Aktuell auf Server-Entities spezialisiert, kann aber
 * später für andere Entity-Typen erweitert werden.</p>
 */
@Slf4j
public class ConfigurableEntityCache implements AutoCloseable {

    private final Supplier<List<Server>> entitySupplier;
    private final CacheKeyConfiguration<Server> keyConfiguration;
    private final CacheFallbackStrategy<Server> fallbackStrategy;
    private EntityCache<Server> entityCache;

    public ConfigurableEntityCache(Supplier<List<Server>> entitySupplier,
                                   CacheKeyConfiguration<Server> keyConfiguration,
                                   CacheFallbackStrategy<Server> fallbackStrategy) {
        this.entitySupplier = entitySupplier;
        this.keyConfiguration = keyConfiguration;
        this.fallbackStrategy = fallbackStrategy;
    }

    /**
     * Lädt den Cache und gibt diese Instanz für try-with-resources zurück.
     */
    public ConfigurableEntityCache loadForResource() {
        if (entityCache == null) {
            entityCache = EntityCache.<Server>builder()
                    .withEntitySupplier(entitySupplier)
                    .withKeyConfiguration(keyConfiguration)
                    .build();
        }

        entityCache.loadForResource();
        return this;
    }

    /**
     * Erstellt einen Cached Matcher für statische Identifikatoren.
     */
    public <D> ServerMatcher<D> createCachedMatcher(String keyName,
                                                    String identifierType,
                                                    Function<D, String> keyExtractor) {
        return new GenericMatcherStrategy<>(
                null, // ServerService wird für Cached Matcher nicht benötigt
                keyExtractor,
                identifierType,
                identifier -> findEntitiesByKey(keyName, identifier)
        );
    }

    /**
     * Erstellt einen Cached Matcher mit Key-Transformation.
     */
    public <D> ServerMatcher<D> createCachedMatcherWithTransform(String keyName,
                                                                 String identifierType,
                                                                 Function<D, Object> keyExtractor,
                                                                 Function<Object, String> transformer) {
        return new GenericMatcherStrategy<>(
                null,
                data -> {
                    Object value = keyExtractor.apply(data);
                    return value != null ? transformer.apply(value) : null;
                },
                identifierType,
                identifier -> findEntitiesByKey(keyName, identifier)
        );
    }

    /**
     * Erstellt einen Direct Database Matcher für dynamische Identifikatoren.
     */
    public <D> ServerMatcher<D> createDirectMatcher(String identifierType,
                                                    Function<D, String> keyExtractor,
                                                    Function<String, List<Server>> databaseFinder) {
        return new GenericMatcherStrategy<>(
                null,
                keyExtractor,
                identifierType,
                databaseFinder
        );
    }

    /**
     * Erstellt einen Hybrid Matcher mit spezieller Verarbeitungslogik.
     */
    public <D> ServerMatcher<D> createHybridMatcher(String identifierType,
                                                    Function<D, String> keyExtractor,
                                                    Function<String, List<Server>> hybridFinder) {
        return new GenericMatcherStrategy<>(
                null,
                keyExtractor,
                identifierType,
                hybridFinder
        );
    }

    /**
     * Findet Entities anhand eines Cache-Schlüssels.
     */
    public List<Server> findEntitiesByKey(String keyName, String keyValue) {
        if (entityCache != null && entityCache.isLoaded()) {
            return entityCache.findEntitiesByKey(keyName, keyValue);
        }

        // Fallback zur Datenbank über die Fallback-Strategie
        return fallbackStrategy.findByKey(keyName, keyValue);
    }

    /**
     * Prüft, ob der Cache geladen ist.
     */
    public boolean isLoaded() {
        return entityCache != null && entityCache.isLoaded();
    }

    @Override
    public void close() {
        if (entityCache != null) {
            entityCache.close();
        }
    }
}