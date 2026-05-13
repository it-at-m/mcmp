package de.muenchen.mcmp.exception;

public class MaintenanceModeException extends RuntimeException {
    public MaintenanceModeException(String message) {
        super(message);
    }
}
