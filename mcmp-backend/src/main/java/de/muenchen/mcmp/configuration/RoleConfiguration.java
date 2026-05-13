
package de.muenchen.mcmp.configuration;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Configuration class for managing role-to-department mappings.
 *
 * <p>This configuration binds properties with the prefix "lhm" and provides
 * mechanisms to map departments to their corresponding Spring Security authorities.</p>
 *
 * <p><strong>Example configuration:</strong></p>
 * <pre>
 * lhm:
 *   roles:
 *     admin:
 *       departments:
 *         - IT_DEPARTMENT
 *         - HR_DEPARTMENT
 *     user:
 *       departments:
 *         - SALES_DEPARTMENT
 * </pre>
 *
 * <p><strong>Thread-Safety:</strong> This class is thread-safe and uses internal
 * locking mechanisms for cache operations.</p>
 *
 * @author MCMP Team
 * @version 1.1
 * @since 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "lhm")
@Data
@Slf4j
public class RoleConfiguration {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Map of role names to their corresponding Role objects.
     * Populated from configuration properties.
     */
    private Map<String, Role> roles = new HashMap<>();

    /**
     * Cache mapping departments to their granted authorities.
     * Built lazily on first access and protected by read-write lock.
     */
    private volatile Map<String, Set<SimpleGrantedAuthority>> departmentAuthorityCache;

    /**
     * Cache mapping users to their granted authorities.
     * Built lazily on first access and protected by read-write lock.
     */
    private volatile Map<String, Set<SimpleGrantedAuthority>> userAuthorityCache;

    /**
     * Lock for thread-safe cache operations.
     */
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

    /**
     * Represents a role with its associated departments.
     *
     * <p>Departments are stored in uppercase for case-insensitive matching.</p>
     */
    @Getter
    public static class Role {

        /**
         * Set of department identifiers associated with this role.
         * All department names are stored in uppercase.
         */
        private final Set<String> departments;

        /**
         * Set of usernames associated with this role.
         * All usernames are stored in lowercase for case-insensitive matching.
         */
        private final Set<String> users;

        /**
         * Creates a new Role instance with an empty department set.
         */
        public Role() {
            this.departments = new HashSet<>();
            this.users = new HashSet<>();
        }

        /**
         * Sets the departments for this role from a list.
         *
         * <p>Departments are normalized to uppercase and blank entries are filtered out.</p>
         *
         * @param departmentsList the list of department identifiers to set
         */
        public void setDepartments(List<String> departmentsList) {
            this.departments.clear();
            if (departmentsList != null && !departmentsList.isEmpty()) {
                departmentsList.stream()
                        .filter(dept -> dept != null && !dept.isBlank())
                        .map(String::toUpperCase)
                        .forEach(this.departments::add);
            }
        }

        /**
         * Sets the users for this role from a list.
         *
         * <p>Usernames are normalized to lowercase and blank entries are filtered out.</p>
         *
         * @param usersList the list of usernames to set
         */
        public void setUsers(List<String> usersList) {
            this.users.clear();
            if (usersList != null && !usersList.isEmpty()) {
                usersList.stream()
                        .filter(user -> user != null && !user.isBlank())
                        .map(String::toLowerCase)
                        .forEach(this.users::add);
            }
        }
    }

    /**
     * Retrieves all Spring Security authorities for a specific department.
     *
     * <p>This method uses a cached mapping for performance. The cache is built
     * lazily on first access and is thread-safe.</p>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * Set<SimpleGrantedAuthority> authorities =
     *     roleConfig.getAuthoritiesForDepartment("IT_DEPARTMENT");
     * // Returns: [ROLE_ADMIN]
     * }</pre>
     *
     * @param department the department identifier (case-insensitive)
     * @return an unmodifiable set of authorities for the department,
     *         or an empty set if the department is null, blank, or has no roles
     */
    public Set<SimpleGrantedAuthority> getAuthoritiesForDepartment(String department) {
        if (department == null || department.isBlank()) {
            log.debug("Attempted to get authorities for null or blank department");
            return Collections.emptySet();
        }

        String normalizedDepartment = department.toUpperCase();

        // Check if cache needs initialization
        if (departmentAuthorityCache == null) {
            cacheLock.writeLock().lock();
            try {
                // Double-check after acquiring lock
                if (departmentAuthorityCache == null) {
                    buildCache();
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
        }

        cacheLock.readLock().lock();
        try {
            return departmentAuthorityCache.getOrDefault(normalizedDepartment, Collections.emptySet());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Retrieves all Spring Security authorities for a specific user.
     *
     * <p>This method uses a cached mapping for performance. The cache is built
     * lazily on first access and is thread-safe.</p>
     *
     * <p>Usernames are compared case-insensitively.</p>
     *
     * @param username the username (case-insensitive)
     * @return an unmodifiable set of authorities for the user,
     *         or an empty set if the username is null, blank, or has no roles
     */
    public Set<SimpleGrantedAuthority> getAuthoritiesForUser(String username) {
        if (username == null || username.isBlank()) {
            log.debug("Attempted to get authorities for null or blank username");
            return Collections.emptySet();
        }

        String normalizedUsername = username.toLowerCase();

        if (userAuthorityCache == null) {
            cacheLock.writeLock().lock();
            try {
                if (userAuthorityCache == null) {
                    buildCache();
                }
            } finally {
                cacheLock.writeLock().unlock();
            }
        }

        cacheLock.readLock().lock();
        try {
            return userAuthorityCache.getOrDefault(normalizedUsername, Collections.emptySet());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Builds the internal cache mapping departments to authorities.
     *
     * <p>This method iterates through all configured roles and creates
     * a reverse mapping from departments to their granted authorities.</p>
     *
     * <p><strong>Note:</strong> This method must be called within a write lock.</p>
     */
    private void buildCache() {
        log.info("Building authority cache for {} configured roles", roles.size());

        Map<String, Set<SimpleGrantedAuthority>> tempDepartmentCache = new ConcurrentHashMap<>();
        Map<String, Set<SimpleGrantedAuthority>> tempUserCache = new ConcurrentHashMap<>();

        for (final Map.Entry<String, Role> roleEntry : roles.entrySet()) {
            final String roleName = roleEntry.getKey();
            final Role role = roleEntry.getValue();
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(ROLE_PREFIX + roleName.toUpperCase()
            );

            for (final String department : role.getDepartments()) {
                tempDepartmentCache
                        .computeIfAbsent(department, k -> ConcurrentHashMap.newKeySet())
                        .add(authority);
            }

            for (final String user : role.getUsers()) {
                tempUserCache
                        .computeIfAbsent(user, k -> ConcurrentHashMap.newKeySet())
                        .add(authority);
            }
        }

        tempDepartmentCache.replaceAll((k, v) -> Collections.unmodifiableSet(v));
        tempUserCache.replaceAll((k, v) -> Collections.unmodifiableSet(v));

        departmentAuthorityCache = Collections.unmodifiableMap(tempDepartmentCache);
        userAuthorityCache = Collections.unmodifiableMap(tempUserCache);

        log.info("Authority cache built successfully with {} department mappings and {} user mappings", departmentAuthorityCache.size(), userAuthorityCache.size());
    }

    /**
     * Checks if a department has at least one associated role.
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * boolean hasRoles = roleConfig.hasAuthoritiesForDepartment("IT_DEPARTMENT");
     * // Returns: true if IT_DEPARTMENT has any roles configured
     * }</pre>
     *
     * @param department the department identifier to check (case-insensitive)
     * @return {@code true} if the department has at least one authority,
     *         {@code false} otherwise
     * @throws IllegalArgumentException if department is null or blank
     */
    public boolean hasAuthoritiesForDepartment(String department) {
        if (department == null || department.isBlank()) {
            throw new IllegalArgumentException("Department must not be null or blank");
        }

        return !getAuthoritiesForDepartment(department).isEmpty();
    }

    /**
     * Returns all configured role names.
     *
     * @return an unmodifiable set of all role names
     */
    public Set<String> getAllRoles() {
        return Collections.unmodifiableSet(roles.keySet());
    }

    /**
     * Returns all departments that have at least one role configured.
     *
     * @return an unmodifiable set of all department identifiers
     */
    public Set<String> getAllDepartments() {
        cacheLock.readLock().lock();
        try {
            if (departmentAuthorityCache == null) {
                cacheLock.readLock().unlock();
                cacheLock.writeLock().lock();
                try {
                    if (departmentAuthorityCache == null) {
                        buildCache();
                    }
                    cacheLock.readLock().lock();
                } finally {
                    cacheLock.writeLock().unlock();
                }
            }
            return Collections.unmodifiableSet(departmentAuthorityCache.keySet());
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Invalidates the authority cache.
     *
     * <p>This method should be called when the role configuration changes
     * at runtime. The cache will be rebuilt on the next access.</p>
     *
     * <p><strong>Note:</strong> This operation is thread-safe.</p>
     */
    public void invalidateCache() {
        cacheLock.writeLock().lock();
        try {
            log.info("Invalidating authority cache");
            departmentAuthorityCache = null;
            userAuthorityCache = null;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Checks if a specific role exists in the configuration.
     *
     * @param roleName the role name to check
     * @return {@code true} if the role exists, {@code false} otherwise
     */
    public boolean roleExists(String roleName) {
        return roleName != null && roles.containsKey(roleName);
    }

    /**
     * Gets all departments for a specific role.
     *
     * @param roleName the role name
     * @return an unmodifiable set of department identifiers, or empty set if role doesn't exist
     */
    public Set<String> getDepartmentsForRole(String roleName) {
        if (roleName == null || !roles.containsKey(roleName)) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(roles.get(roleName).getDepartments());
    }
}