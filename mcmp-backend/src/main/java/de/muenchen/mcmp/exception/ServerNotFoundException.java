package de.muenchen.mcmp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception thrown to indicate that a requested GreenIT server resource could not be found.
 * <p>
 * This exception extends {@link ResponseStatusException} and is automatically
 * mapped to HTTP 404 (Not Found) responses. It is thrown when a server lookup
 * fails because the specified server does not exist or is unavailable in the system.
 * <p>
 * <b>Integration with Exception Handling:</b>
 * <ul>
 *  <li>Caught and handled by {@code GlobalExceptionHandler#handleNotFoundException(ServerNotFoundException)}</li>
 *  <li>Returns a standardized {@code GreenITResponseDTO} with the error message</li>
 *  <li>Automatically maps to HTTP 404 status code</li>
 * </ul>
 *
 * @see ResponseStatusException
 * @see GlobalExceptionHandler
 */
public class ServerNotFoundException extends ResponseStatusException {
    public ServerNotFoundException(final String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}