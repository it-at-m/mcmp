package de.muenchen.mcmp.servicenow;

// Custom Exception for ServiceNow errors
public class ServiceNowClientException extends RuntimeException {
    public ServiceNowClientException(String message) {
        super(message);
    }

    public ServiceNowClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
