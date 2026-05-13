package de.muenchen.mcmp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception for invalid arguments in GreenIT operations.
 * <p>
 * This exception extends {@link ResponseStatusException} and automatically maps to an
 * HTTP 400 Bad Request status. Use this exception when a GreenIT operation receives
 * invalid or unacceptable input parameters.
 *
 * @see ResponseStatusException
 * @see HttpStatus#BAD_REQUEST
 */
public class GreenITIllegalArgumentException extends ResponseStatusException {
    public GreenITIllegalArgumentException(final String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
