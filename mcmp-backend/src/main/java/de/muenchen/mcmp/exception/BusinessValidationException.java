package de.muenchen.mcmp.exception;

public class BusinessValidationException extends RuntimeException {

    public BusinessValidationException(final String message) {
        super(message);
    }
}