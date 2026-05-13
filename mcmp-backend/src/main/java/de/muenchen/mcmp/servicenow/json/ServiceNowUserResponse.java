package de.muenchen.mcmp.servicenow.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Represents the JSON response from the ServiceNow API for user queries.
 */
public record ServiceNowUserResponse(
        @JsonProperty("result")
        @NotNull
        @NotEmpty
        List<ServiceNowUserResult> results  // lowercase: Java-Konvention
) {
    public ServiceNowUserResponse {
        if (results == null) {
            throw new IllegalArgumentException("Result list cannot be null");
        }
    }
}