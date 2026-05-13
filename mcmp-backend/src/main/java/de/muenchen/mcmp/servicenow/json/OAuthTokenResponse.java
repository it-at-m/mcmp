package de.muenchen.mcmp.servicenow.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the OAuth2 token response from the ServiceNow authorization server.
 */
public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
) {}