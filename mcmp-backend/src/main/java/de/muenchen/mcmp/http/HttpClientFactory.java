package de.muenchen.mcmp.http;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

/**
 * The HttpClientFactory interface provides a method for creating instances of {@link CloseableHttpClient}
 * that are pre-configured with basic authentication credentials.
 *
 * Implementations of this interface are responsible for constructing and configuring the HTTP client
 * instance to support basic authentication using the provided username and password.
 *
 * This interface is useful in scenarios where HTTP clients need to communicate with remote services
 * that require authentication, allowing for reusable and consistent configuration management.
 */
public interface HttpClientFactory {
    CloseableHttpClient createHttpClientWithBasicAuthentication(String username, String password);
}