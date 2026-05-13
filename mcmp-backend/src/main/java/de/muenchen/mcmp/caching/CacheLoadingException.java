package de.muenchen.mcmp.caching;

/**
 * Exception thrown when critical errors occur during cache loading operations.
 *
 * <p>This exception is thrown for unrecoverable errors that prevent the cache
 * from being loaded successfully. It wraps the underlying cause while providing
 * context-specific error information.</p>
 *
 * <p><strong>Usage:</strong></p>
 * <ul>
 *   <li>Database connection failures during entity loading</li>
 *   <li>Configuration errors that prevent cache initialization</li>
 *   <li>Memory allocation failures during cache population</li>
 * </ul>
 */
public class CacheLoadingException extends RuntimeException {

    /**
     * Creates a new CacheLoadingException with the specified message.
     *
     * @param message the exception message
     */
    public CacheLoadingException(final String message) {
        super(message);
    }

    /**
     * Creates a new CacheLoadingException with the specified message and cause.
     *
     * @param message the exception message
     * @param cause the underlying cause
     */
    public CacheLoadingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}