package de.muenchen.mcmp.infoblox;

/**
 * A custom exception that signifies invalid input has been provided.
 * This exception is thrown when user input does not meet the expected validation rules.
 * It extends {@link RuntimeException}, so it is unchecked and does not require explicit handling.
 *
 * Two constructors are provided:
 * 1. A single-argument constructor to specify an error message.
 * 2. A two-argument constructor to specify both an error message and the underlying cause of the exception.
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}