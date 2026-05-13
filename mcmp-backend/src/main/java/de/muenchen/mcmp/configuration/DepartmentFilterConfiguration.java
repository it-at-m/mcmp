package de.muenchen.mcmp.configuration;

import lombok.Data;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration class for department-based access filtering.
 * <p>
 * This configuration allows restricting access to the application based on user departments.
 * It is particularly useful for test environments where access should be limited to specific
 * departments for testing purposes.
 * </p>
 * <p>
 * The configuration is loaded from the application configuration file under the prefix
 * {@code lhm.department-filter}.
 * </p>
 *
 * <h2>Configuration Example:</h2>
 * <pre>
 * lhm:
 *   department-filter:
 *     enabled: true
 *     allowed-departments:
 *       - ITM-IBS41
 *       - ITM-IBS42
 * </pre>
 *
 * <h2>Usage:</h2>
 * <p>
 * The filter can be enabled or disabled via the {@code enabled} property. When disabled,
 * all departments are allowed by default. When enabled, only departments listed in
 * {@code allowed-departments} are granted access.
 * </p>
 *
 * @see RoleConfiguration
 */
@ConfigurationProperties(prefix = "lhm.department-filter")
@Data
public class DepartmentFilterConfiguration {

    /**
     * Flag to enable or disable the department filter.
     * <p>
     * When set to {@code false}, the filter is inactive and all departments are allowed.
     * When set to {@code true}, only departments listed in {@code allowedDepartments} are permitted.
     * </p>
     *
     *
     * -- GETTER --
     *  Checks whether the department filter is currently enabled.
     *
     * @default false
     * @return {@code true} if the filter is active, {@code false} otherwise
     */
    private boolean enabled = false;

    /**
     * Set of departments that are allowed to access the application.
     * <p>
     * All department identifiers are normalized to uppercase for case-insensitive comparison.
     * This set is only relevant when {@code enabled} is set to {@code true}.
     * </p>
     */
    @Setter(lombok.AccessLevel.NONE)
    private Set<String> allowedDepartments = new HashSet<>();

    /**
     * Sets the allowed departments from the configuration file.
     * <p>
     * This method is automatically called by Spring Boot's configuration binding mechanism.
     * It normalizes all department identifiers to uppercase to ensure case-insensitive
     * matching during authorization checks.
     * </p>
     * <p>
     * Null and blank entries are filtered out automatically.
     * </p>
     *
     * @param departmentsList List of department identifiers from the configuration file
     */
    public void setAllowedDepartments(List<String> departmentsList) {
        this.allowedDepartments.clear();
        if (departmentsList != null) {
            this.allowedDepartments = departmentsList.stream()
                    .filter(dept -> dept != null && !dept.isBlank())
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Checks whether a given department is allowed to access the application.
     * <p>
     * This method implements the following logic:
     * </p>
     * <ul>
     *   <li>If the filter is disabled ({@code enabled = false}), all departments are allowed</li>
     *   <li>If the filter is enabled ({@code enabled = true}), only departments in the
     *       {@code allowedDepartments} list are permitted</li>
     *   <li>Null or blank department identifiers are always denied when the filter is enabled</li>
     *   <li>Department comparison is case-insensitive</li>
     * </ul>
     *
     * @param department The department identifier to check (will be normalized to uppercase)
     * @return {@code true} if the department is allowed access, {@code false} otherwise
     */
    public boolean isAllowed(String department) {
        // If filter is disabled, allow all departments
        if (!enabled) {
            return true;
        }

        // Reject null or blank departments
        if (department == null || department.isBlank()) {
            return false;
        }

        // Check if department is in the allowed list (case-insensitive)
        return allowedDepartments.contains(department.toUpperCase());
    }

    /**
     * Returns an unmodifiable view of the allowed departments.
     * <p>
     * This method is useful for logging, debugging, or displaying the current
     * filter configuration.
     * </p>
     *
     * @return Unmodifiable set of allowed department identifiers (all uppercase)
     */
    public Set<String> getAllowedDepartments() {
        return Set.copyOf(allowedDepartments);
    }
}