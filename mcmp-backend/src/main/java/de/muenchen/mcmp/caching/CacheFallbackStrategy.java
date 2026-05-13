package de.muenchen.mcmp.caching;

import java.util.List;

/**
 * Interface für Fallback-Strategien, wenn der Cache nicht verfügbar ist.
 *
 * @param <T> der Typ der Entity
 */
public interface CacheFallbackStrategy<T> {

    /**
     * Findet Entities über Datenbankabfrage als Fallback.
     *
     * @param keyName der Name des Cache-Schlüssels
     * @param keyValue der Wert des Cache-Schlüssels
     * @return Liste der gefundenen Entities
     */
    List<T> findByKey(String keyName, String keyValue);
}

