package de.muenchen.mcmp.servicenow;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class ServiceNowService {

    private final ServiceNowClient serviceNowClient;

    /**
     * Retrieves the sys_id of a user via the ServiceNow API.
     *
     * @param username The username.
     * @return The sys_id or null.
     */
    public Optional<String> getSysIdByUsername(final String username) {
        return serviceNowClient.getSysIdByUsername(username);
    }
}
