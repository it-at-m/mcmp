package de.muenchen.mcmp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception thrown to indicate that a requested GreenIT server operation cannot be executed
 * because the server has been locked.
 * <p>
 * This exception extends {@link ResponseStatusException} and is automatically
 * mapped to HTTP 403 (Forbidden) responses. It is thrown when an operation cannot be processed
 * because the target server has been put into a locked state, prohibiting certain actions
 * or modifications.
 * <p>
 * <b>Integration with Exception Handling:</b>
 * <ul>
 *  <li>Caught and handled by {@code GlobalExceptionHandler#handleForbiddenException(GreenITServerLockedException)}</li>
 *  <li>Returns a standardized {@code GreenITResponseDTO} with the error message</li>
 *  <li>Automatically maps to HTTP 403 status code</li>
 * </ul>
 *
 * @see ResponseStatusException
 * @see GlobalExceptionHandler
 */
public class GreenITServerLockedException extends ResponseStatusException {
    public GreenITServerLockedException(final String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
