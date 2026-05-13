package de.muenchen.mcmp.servicenow.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Represents a single user result from the ServiceNow API.
 */
public record ServiceNowUserResult(
        @JsonProperty("sys_id")
        @NotNull
        String sysId,
        @JsonProperty("user_name")
        @NotNull
        String userName
) {
    /**
     * Compact constructor with validation.
     * Ensures sysId and userName are not null.
     *
     * @param sysId The unique system ID of the user.
     * @param userName The username.
     * @throws IllegalArgumentException If sysId or userName is null.
     */
    public ServiceNowUserResult {
        if (sysId == null || userName == null) {
            throw new IllegalArgumentException("sysId and userName cannot be null");
        }
    }
}