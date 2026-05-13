package de.muenchen.mcmp.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration class for managing a whitelist of technical API users.
 *
 * <p>
 * This configuration class binds properties under the {@code lhm} prefix
 * and focuses specifically on the {@code api-users} section.
 * It provides a type-safe way to access and verify configured API users
 * throughout the application.
 * </p>
 *
 * <h2>Configuration Example</h2>
 *
 * <pre>{@code
 * lhm:
 *   api-users:
 *     - admin
 *     - technical-client
 *     - monitoring-service
 * }</pre>
 *
 * <p>
 * The values in {@code api-users} are interpreted as usernames or technical
 * client identifiers that are allowed to perform specific API operations,
 * for example:
 * </p>
 * <ul>
 *   <li>calling internal maintenance endpoints,</li>
 *   <li>executing integration or monitoring requests,</li>
 *   <li>acting as a trusted technical system user.</li>
 * </ul>
 *
 * <h2>Binding and Normalization</h2>
 *
 * <p>
 * Spring Boot automatically binds the list defined under {@code lhm.api-users}
 * to the {@link #apiUsers} field using the {@link #setApiUsers(List)} method.
 * During this binding process:
 * </p>
 * <ul>
 *   <li>
 *     {@code null} or blank entries are filtered out to avoid invalid usernames.
 *   </li>
 *   <li>
 *     All usernames are normalized to lowercase to enable case-insensitive
 *     comparison in the {@link #isApiUser(String)} method.
 *   </li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <p>
 * The configuration is exposed as a Spring bean and can be injected into any
 * other component (service, controller, security component, etc.) where it is
 * needed:
 * </p>
 *
 * <pre>{@code
 * @Service
 * public class SomeService {
 *
 *     private final ApiUserConfiguration ApiUserConfiguration;
 *
 *     public SomeService(ApiUserConfiguration ApiUserConfiguration) {
 *         this.ApiUserConfiguration = ApiUserConfiguration;
 *     }
 *
 *     public void handleRequest(String username) {
 *         if (ApiUserConfiguration.isApiUser(username)) {
 *             // Special handling for configured API users
 *         } else {
 *             // Default handling for regular users
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Thread-Safety</h2>
 *
 * <p>
 * The internal set {@link #apiUsers} is not exposed directly. The getter
 * {@link #getApiUsers()} returns an unmodifiable view to prevent accidental
 * modification from outside. The configuration is normally initialized once
 * on application startup and then treated as effectively immutable.
 * </p>
 */
@Configuration
@ConfigurationProperties(prefix = "lhm")
@Data
public class ApiUserConfiguration {

    /**
     * Internal set of configured API users.
     *
     * <p>
     * This set stores normalized usernames (all in lowercase) to allow for
     * case-insensitive membership checks. It is populated from the
     * {@code lhm.api-users} configuration property.
     * </p>
     *
     * <p>
     * The set is initialized as an empty {@link HashSet} and will only contain
     * values after Spring Boot has bound the configuration properties.
     * </p>
     */
    private Set<String> apiUsers = new HashSet<>();

    /**
     * Setter method used by Spring Boot's configuration binding mechanism.
     *
     * <p>
     * This method is responsible for transforming the raw list of usernames
     * from the configuration file into a cleaned and normalized internal set.
     * The following normalization is performed:
     * </p>
     * <ul>
     *   <li>
     *     {@code null} or blank entries are ignored and not added to the set.
     *   </li>
     *   <li>
     *     All valid usernames are converted to lowercase to ensure that
     *     comparisons are case-insensitive.
     *   </li>
     * </ul>
     *
     * <p>
     * The previous contents of {@link #apiUsers} are cleared before the new
     * values are added. This ensures that the set always reflects the current
     * configuration state.
     * </p>
     *
     * @param users raw list of usernames from the {@code lhm.api-users}
     *              property in the configuration file. May be {@code null}.
     */
    public void setApiUsers(List<String> users) {
        this.apiUsers.clear();

        if (users == null) {
            return;
        }

        users.stream()
                .filter(username -> username != null && !username.isBlank())
                .map(String::toLowerCase)
                .forEach(this.apiUsers::add);
    }

    /**
     * Returns an unmodifiable view of all configured API users.
     *
     * <p>
     * The returned set is a defensive copy that cannot be modified by the caller.
     * This design prevents accidental changes to the configuration at runtime and
     * makes the behavior of the application easier to reason about.
     * </p>
     *
     * <p>
     * All usernames in the returned set are guaranteed to be:
     * </p>
     * <ul>
     *   <li>non-null,</li>
     *   <li>non-blank,</li>
     *   <li>stored in lowercase form.</li>
     * </ul>
     *
     * @return an unmodifiable {@link Set} of normalized API usernames;
     *         never {@code null}, but may be empty if no API users are configured.
     */
    public Set<String> getApiUsers() {
        return Collections.unmodifiableSet(apiUsers);
    }

    /**
     * Checks whether the given username is configured as an API user.
     *
     * <p>
     * This method provides a convenient and type-safe way to verify whether a
     * given username (for example, extracted from a JWT claim or HTTP header)
     * is recognized as a technical API user according to the current configuration.
     * </p>
     *
     * <h3>Behavior and Normalization</h3>
     *
     * <ul>
     *   <li>
     *     If {@code username} is {@code null} or blank, the method returns
     *     {@code false}.
     *   </li>
     *   <li>
     *     The input username is converted to lowercase before comparison to
     *     enable case-insensitive lookup.
     *   </li>
     *   <li>
     *     The method performs a simple membership check against the internally
     *     stored set {@link #apiUsers}.
     *   </li>
     * </ul>
     *
     * <h3>Typical Usage</h3>
     *
     * <p>
     * This method is particularly useful in security-related components where
     * special authorization rules need to be applied for certain technical users.
     * For example:
     * </p>
     *
     * <pre>{@code
     * if (ApiUserConfiguration.isApiUser(usernameFromToken)) {
     *     // Grant additional roles or permissions for API users
     * } else {
     *     // Apply standard user access control
     * }
     * }</pre>
     *
     * @param username the username to check; may be {@code null}.
     * @return {@code true} if the username is configured as an API user,
     *         {@code false} otherwise.
     */
    public boolean isApiUser(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return apiUsers.contains(username.toLowerCase());
    }
}