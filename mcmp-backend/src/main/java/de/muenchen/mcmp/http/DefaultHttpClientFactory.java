package de.muenchen.mcmp.http;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * DefaultHttpClientFactory is an implementation of the {@link HttpClientFactory} interface.
 * It provides functionality for creating instances of {@link CloseableHttpClient} that are
 * pre-configured to use basic authentication.
 * <p>
 * This class constructs an HTTP client with the given username and password, using them
 * for basic authentication. It utilizes the {@link BasicCredentialsProvider} to store
 * the credentials and configure the client accordingly.
 * <p>
 * This implementation is particularly useful for establishing authenticated communication
 * with HTTP-based services securely and conveniently.
 */
@Component
public class DefaultHttpClientFactory implements HttpClientFactory {

    /**
     * Creates a pre-configured {@link CloseableHttpClient} instance with basic authentication.
     * This method sets up an HTTP client that includes a username and password for use in authenticated
     * HTTP requests. It employs a {@link BasicCredentialsProvider} to store and provide the credentials
     * and applies specific timeout settings for connection requests and responses.
     *
     * @param username the username to be used for basic authentication; must not be null or blank
     * @param password the password to be used for basic authentication; can be null
     * @return a {@link CloseableHttpClient} instance configured for basic authentication
     * @throws NullPointerException     if the provided username is null
     * @throws IllegalArgumentException if the provided username is blank
     */
    @Override
    public CloseableHttpClient createHttpClientWithBasicAuthentication(final String username, final String password) {
        Objects.requireNonNull(username, "username must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        final var credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(null, -1),
                new UsernamePasswordCredentials(username, password != null ? password.toCharArray() : new char[0])
        );
        final var requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(Duration.ofSeconds(60)))
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .build();
        return HttpClients.custom()
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }
}
