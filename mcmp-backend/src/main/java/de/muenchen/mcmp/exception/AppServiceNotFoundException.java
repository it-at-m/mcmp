package de.muenchen.mcmp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception thrown to indicate that a requested {@link de.muenchen.mcmp.appservice.Appservice} could not be found.
 * <p>
 * This exception extends {@link ResponseStatusException} and is automatically
 * mapped to HTTP 404 (Not Found) responses. It is thrown when an appservice lookup
 * fails because the specified appservice does not exist or could not be resolved
 * in the system.
 * <p>
 * <b>Integration with Exception Handling:</b>
 * <ul>
 *  <li>Caught and handled by {@link GlobalExceptionHandler#handleNotFoundException(AppServiceNotFoundException)}</li>
 *  <li>Returns a standardized {@link de.muenchen.mcmp.clients.greenit.GreenITResponseDTO} with the error message</li>
 *  <li>Automatically maps to HTTP 404 status code</li>
 * </ul>
 *
 * @see ResponseStatusException
 * @see GlobalExceptionHandler
 */
public class AppServiceNotFoundException extends ResponseStatusException {
    public AppServiceNotFoundException(final String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}