
package de.muenchen.mcmp.servicenow;

import de.muenchen.mcmp.configuration.EncryptionProperties;
import de.muenchen.mcmp.servicenow.json.OAuthTokenResponse;
import de.muenchen.mcmp.servicenow.json.ServiceNowUserResponse;
import de.muenchen.mcmp.snowConfig.SnowConfigRepository;
import de.muenchen.mcmp.snowConfig.SnowConfigWithDecryptedPassword;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

@Component
@AllArgsConstructor
@Slf4j
public class ServiceNowClient {

    private static final String API_ENDPOINT_USER = "/foundation_data/user?sysparm_query=user_name={username}";

    private final EncryptionProperties encryptionProperties;
    private final SnowConfigRepository snowConfigRepository;
    private final ServiceNowProperties serviceNowProperties;

    /**
     * Retrieves the sys_id of a user based on the user_name via ServiceNow API.
     * Uses OAuth2 Client Credentials for authentication.
     *
     * @param username The username (e.g. "john.doe"). Must not be null or empty.
     * @return The sys_id as a String or {@link Optional#empty()} if not found.
     * @throws ServiceNowClientException In case of errors (e.g. HTTP errors, invalid configuration).
     */
    public Optional<String> getSysIdByUsername(String username) {
        Objects.requireNonNull(username, "Username cannot be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (serviceNowProperties.isSkipSearch()) {
            return username.isBlank() ? Optional.empty() : Optional.of(username);
        }

        try {
            SnowConfigWithDecryptedPassword config = getConfig();
            RestClient restClient = buildRestClient(config);

            String accessToken = fetchAccessToken(config, restClient);
            ServiceNowUserResponse response = fetchUser(restClient, accessToken, username);

            if (response == null || response.results().isEmpty()) {
                log.warn("No user with user_name '{}' found.", username);
                return Optional.empty();
            }

            String sysId = response.results().getFirst().sysId();
            log.info("sys_id for user_name '{}' found: {}", username, sysId);
            return Optional.of(sysId);

        } catch (RestClientResponseException e) {
            // Catches both 4xx and 5xx errors
            log.error("HTTP error in ServiceNow API: Status {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ServiceNowClientException("Error in ServiceNow API request: " + e.getStatusCode(), e);
        } catch (ServiceNowClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while retrieving sys_id for username '{}'", username, e);
            throw new ServiceNowClientException("Error retrieving sys_id.", e);
        }
    }

    protected SnowConfigWithDecryptedPassword getConfig() {
        SnowConfigWithDecryptedPassword config = snowConfigRepository
                .findDefaultServiceNowConfig(encryptionProperties.getPassphrase());
        if (config == null || !config.getEnabled()) {
            throw new ServiceNowClientException("ServiceNow configuration is not available or disabled.");
        }
        return config;
    }

    protected RestClient buildRestClient(SnowConfigWithDecryptedPassword config) {
        RestClient.Builder builder = RestClient.builder().baseUrl(config.getApiEndpoint());

        if (Boolean.TRUE.equals(config.getUseProxy())) {
            URI proxyUri = URI.create(config.getProxy());
            InetSocketAddress proxyAddress = new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort());

            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, proxyAddress));
            builder.requestFactory(requestFactory);
        }

        return builder.build();
    }

    protected String fetchAccessToken(SnowConfigWithDecryptedPassword config, RestClient restClient) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", config.getApiClientId());
        body.add("client_secret", config.getApiClientSecret());

        OAuthTokenResponse tokenResponse = restClient.post()
                .uri(config.getApiClientAuthUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(OAuthTokenResponse.class);

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new ServiceNowClientException("Error retrieving access token.");
        }
        return tokenResponse.accessToken();
    }

    protected ServiceNowUserResponse fetchUser(RestClient restClient, String accessToken, String username) {
        return restClient.get()
                .uri(API_ENDPOINT_USER, username)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(ServiceNowUserResponse.class);
    }
}