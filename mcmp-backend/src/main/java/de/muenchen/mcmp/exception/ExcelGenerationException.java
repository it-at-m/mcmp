package de.muenchen.mcmp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception thrown when generating an Excel report fails.
 */
public class ExcelGenerationException extends ResponseStatusException {
    public ExcelGenerationException(final String message, final Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}