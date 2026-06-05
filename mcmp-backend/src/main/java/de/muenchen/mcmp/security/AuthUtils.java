package de.muenchen.mcmp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utilities for authentication data.
 */
@Slf4j
public final class AuthUtils {

    public static final String NAME_UNAUTHENTICATED_USER = "unauthenticated";

    public static final String TOKEN_PREFERRED_USERNAME = "preferred_username";
    public static final String TOKEN_USERNAME = "username";
    public static final String TOKEN_EMAIL = "email";
    public static final String TOKEN_NAME = "name";
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_READONLY = "ROLE_READONLY";
    public static final String ROLE_WINDOWS = "ROLE_WINDOWS";
    public static final String ROLE_STORAGE = "ROLE_STORAGE";
    public static final String ROLE_LINUX = "ROLE_LINUX";
    public static final String ROLE_ORACLE = "ROLE_ORACLE";
    public static final String ROLE_NON_ORACLE = "ROLE_NON-ORACLE";
    public static final String ROLE_SECURITY = "ROLE_SECURITY";
    public static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    public static final String ROLE_NETWORK = "ROLE_NETWORK";
    public static final String ROLE_API = "ROLE_API";
    public static final String DEPT_PREFIX = "DEPT_";

    /**
     * Represents authenticated user information containing key details such as
     * username, name, email, and department. This record is utilized to
     * encapsulate and transport user-related data in a structured manner.
     *
     * The encapsulated username can be validated for authentication status
     * using the {@code isAuthenticated()} method, which verifies that the
     * username is not equal to the constant {@code NAME_UNAUTHENTICATED_USER}.
     *
     * @param username   The technical username representing the identity of the user.
     * @param name       The full name of the user.
     * @param email      The email address of the user.
     * @param department The organizational department to which the user belongs.
     */
    public record AuthUserInfo(String username, String name, String email, String department) {
        public boolean isAuthenticated() {
            return !NAME_UNAUTHENTICATED_USER.equals(username);
        }
    }

    private AuthUtils() {
    }

    /**
     * Retrieves all standard user attributes (username, name, email, department) in a single operation.
     * Use this instead of calling individual getters to avoid repeated SecurityContext lookups.
     *
     * @return An {@link AuthUserInfo} record containing all resolved values.
     */
    public static AuthUserInfo getCurrentUserInfo() {
        final Authentication authentication = getAuthenticated();
        if (authentication == null) {
            return new AuthUserInfo(NAME_UNAUTHENTICATED_USER, NAME_UNAUTHENTICATED_USER, NAME_UNAUTHENTICATED_USER, NAME_UNAUTHENTICATED_USER);
        }

        return new AuthUserInfo(
                resolveUsername(authentication),
                resolveName(authentication),
                resolveEmail(authentication),
                resolveDepartment(authentication)
        );
    }

    /**
     * Extracts the technical username from the current Spring Security Context.
     *
     * <p><strong>Implementation Details:</strong></p>
     * <ul>
     *   <li>Validates that an {@link Authentication} exists and is successfully authenticated.</li>
     *   <li>For {@link JwtAuthenticationToken}: It performs a tiered lookup in the JWT claims:
     *     <ol>
     *       <li>{@code preferred_username} (Standard for many OIDC providers)</li>
     *       <li>{@code username} (Common fallback)</li>
     *       <li>Standard {@code authentication.getName()} as the final logical fallback</li>
     *     </ol>
     *   </li>
     *   <li>For other authentication types: Returns the result of {@code getName()}.</li>
     * </ul>
     *
     * @return The resolved username or {@link #NAME_UNAUTHENTICATED_USER} if the user
     *         is not authenticated or the name cannot be determined.
     */
    public static String getUsername() {
        return getAuthAttribute(AuthUtils::resolveUsername);
    }


    /**
     * Aggregates all relevant security information for the current user into a {@link UserRoles} object.
     *
     * <p>This method serves as a central hub to evaluate the user's identity and their assigned
     * {@link GrantedAuthority}s. It maps technical Spring Security roles (like {@code ROLE_ADMIN})
     * to the application's domain-specific {@link UserRoles} model.</p>
     *
     * @return A {@link UserRoles} object containing the username and boolean flags for all
     *         supported application roles. Never returns {@code null}.
     */
    public static UserRoles getCurrentUserRoles() {
        final Authentication authentication = getAuthenticated();
        if (authentication == null) {
            return new UserRoles(NAME_UNAUTHENTICATED_USER, false, false,false, false, false, false, false, false, false, false, false);
        }
        final String username = getUsername();

        final Set<String> authoritySet = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean hasUserRole = authoritySet.contains(ROLE_USER);
        boolean isAdmin = authoritySet.contains(ROLE_ADMIN);
        boolean isReadonly = authoritySet.contains(ROLE_READONLY);
        boolean hasWindowsRole = authoritySet.contains(ROLE_WINDOWS);
        boolean hasLinuxRole = authoritySet.contains(ROLE_LINUX);
        boolean hasOracleRole = authoritySet.contains(ROLE_ORACLE);
        boolean hasNonOracleRole = authoritySet.contains(ROLE_NON_ORACLE);
        boolean hasSecurityRole = authoritySet.contains(ROLE_SECURITY);
        boolean hasOperatorRole = authoritySet.contains(ROLE_OPERATOR);
        boolean hasNetworkRole = authoritySet.contains(ROLE_NETWORK);
        boolean hasStorageRole = authoritySet.contains(ROLE_STORAGE);

        return new UserRoles(username, hasUserRole, isAdmin, isReadonly, hasStorageRole, hasWindowsRole, hasLinuxRole, hasOracleRole, hasNonOracleRole, hasSecurityRole, hasOperatorRole, hasNetworkRole);
    }

    /**
     * Helper method to determine if the current user possesses Administrative privileges.
     *
     * <p>This is a convenience wrapper around {@link #getCurrentUserRoles()}. It is primarily
     * used for quick checks in service logic or UI rendering where only the admin status
     * is relevant.</p>
     *
     * @return {@code true} if the user is authenticated and has the {@link #ROLE_ADMIN} authority.
     */
    public static boolean isAdmin() {
        final Authentication authentication = getAuthenticated();
        if (authentication == null) {
            return false;
        }
        return hasRole(authentication.getAuthorities(), ROLE_ADMIN);
    }

    /**
     * Checks if the current user has any of the special roles defined in HasSpecialRole annotation.
     * Special roles include: ADMIN, WINDOWS, LINUX, ORACLE, NON_ORACLE, NETWORK, OPERATOR, SECURITY.
     *
     * @return {@code true} if the user has at least one special role, {@code false} otherwise.
     */
    public static boolean hasSpecialRole() {
        UserRoles roles = getCurrentUserRoles();
        return roles.hasAdminRole() ||
                roles.hasWindowsRole() ||
                roles.hasLinuxRole() ||
                roles.hasOracleRole() ||
                roles.hasNonOracleRole() ||
                roles.hasNetworkRole() ||
                roles.hasOperatorRole() ||
                roles.hasSecurityRole();
    }

    /**
     * Retrieves the department of the currently authenticated user from the Security Context.
     *
     * <p><strong>Technical Background:</strong></p>
     * The JWT issued by the identity provider does <b>not</b> contain department information directly.
     * To bridge this gap, the {@code UserInfoAuthoritiesService} fetches the department from the
     * OIDC {@code /userinfo} endpoint and maps it into the Spring Security Context as a
     * {@link org.springframework.security.core.authority.SimpleGrantedAuthority} with the prefix
     * defined in {@link #DEPT_PREFIX} (e.g., "DEPT_IT-DEPARTMENT").
     *
     * <p>This method scans the authorities of the current {@link Authentication} object for this
     * specific prefix and extracts the original department name.</p>
     *
     * @return The department name (without prefix) if found; {@link #NAME_UNAUTHENTICATED_USER}
     *         if the user is not authenticated or no department authority is present.
     */
    public static String getCurrentUserDepartment() {
        return getAuthAttribute(AuthUtils::resolveDepartment);
    }

    /**
     * Retrieves the email of the currently authenticated user from the authentication context.
     *
     * This method resolves the user's email by extracting a specific claim from the JWT token. If no valid
     * authentication is present or the claim is not available, a default unauthenticated username is returned.
     *
     * @return The email address of the currently authenticated user as a {@link String}, or
     *         {@link #NAME_UNAUTHENTICATED_USER} if the user is not authenticated or the email claim
     *         cannot be determined.
     */
    public static String getCurrentUserEmail() {
        return getAuthAttribute(AuthUtils::resolveEmail);
    }

    /**
     * Retrieves the current user's name from the authentication context.
     *
     * This method extracts the name of the currently authenticated user by resolving
     * a specific claim from the JWT token. If no valid authentication is present or the
     * claim is not available, a default unauthenticated username is returned.
     *
     * @return The current user's name as a {@link String}. If the user is not authenticated
     *         or the name cannot be determined, it returns {@link #NAME_UNAUTHENTICATED_USER}.
     */
    public static String getCurrentUserName() {
        return getAuthAttribute(AuthUtils::resolveName);
    }

    /**
     * Retrieves the {@link Authentication} object from the Spring Security context if the user is authenticated.
     *
     * This method fetches the current {@link Authentication} from the {@link SecurityContextHolder}.
     * It evaluates whether the authentication exists and is considered authenticated. If these checks pass, the method returns the
     * {@link Authentication} object; otherwise, it returns {@code null}.
     *
     * @return The authenticated {@link Authentication} object if present and authenticated, or {@code null} if no valid authentication exists.
     */
    private static Authentication getAuthenticated() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null && authentication.isAuthenticated()) ? authentication : null;
    }

    /**
     * Resolves and retrieves a specific attribute of the authenticated user by applying the provided resolver function.
     *
     * @param attributeResolver A {@link Function} that takes an {@link Authentication} object and extracts the desired attribute as a {@link String}.
     *                          This function defines how the authentication attribute is resolved.
     * @return The resolved attribute as a {@link String}, or {@link #NAME_UNAUTHENTICATED_USER} if no {@link Authentication} object
     *         exists or if the resolved attribute is {@code null}.
     */
    private static String getAuthAttribute(Function<Authentication, String> attributeResolver) {
        final Authentication authentication = getAuthenticated();
        if (authentication == null) {
            return NAME_UNAUTHENTICATED_USER;
        }
        final String result = attributeResolver.apply(authentication);
        return result != null ? result : NAME_UNAUTHENTICATED_USER;
    }

    /**
     * Retrieves the specified JWT claim as a string from the given {@link Authentication} object.
     * <p>
     * This method checks if the provided {@link Authentication} instance is
     * of type {@link JwtAuthenticationToken}. If it is, it extracts the claim
     * value from the token's attributes based on the given claim name. If the claim exists,
     * it is converted to a {@link String} and returned; otherwise, {@code null} is returned.
     *
     * @param authentication the {@link Authentication} object containing the JWT token.
     *                        Must be an instance of {@link JwtAuthenticationToken} to extract claims.
     * @param claimName       the name of the claim to extract from the JWT token's attributes.
     *                        Must not be null.
     * @return the claim value as a {@link String}, or {@code null} if the claim is not present,
     *         the claim value is {@code null}, or the {@link Authentication} object is not a
     *         {@link JwtAuthenticationToken} instance.
     */
    private static String getJwtClaimAsString(final Authentication authentication, final String claimName) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            final Map<String, Object> attributes = jwtAuth.getTokenAttributes();
            final Object claim = attributes.get(claimName);
            return claim != null ? String.valueOf(claim) : null;
        }
        return null;
    }

    /**
     * Checks whether the specified role is present within the provided collection of authorities.
     *
     * @param authorities a collection of {@link GrantedAuthority} objects representing the roles granted to a user.
     *                     Must not be null.
     * @param role the role to check for within the authority collection. Must not be null.
     * @return {@code true} if the specified role is found in the authorities; {@code false} otherwise.
     */
    private static boolean hasRole(final Collection<? extends GrantedAuthority> authorities, final String role) {
        return authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }

    /**
     * Resolves the username from the provided authentication object.
     * Attempts to extract the username from specific JWT claims and defaults to the
     * name associated with the authentication object if the claims are not available.
     *
     * @param authentication the authentication object containing user details and tokens
     * @return the resolved username as a string, which may come from JWT claims or
     *         the default name property of the authentication object
     */
    private static String resolveUsername(Authentication authentication) {
        String name = getJwtClaimAsString(authentication, TOKEN_PREFERRED_USERNAME);
        if (name == null) {
            name = getJwtClaimAsString(authentication, TOKEN_USERNAME);
        }
        if (name == null) {
            name = authentication.getName();
        }
        return name;
    }

    /**
     * Resolves the department associated with the given authentication object.
     * Extracts the department information from the granted authorities of the authentication
     * object by identifying authorities that start with the predefined department prefix.
     *
     * @param authentication the authentication object containing user credentials and authorities
     * @return the name of the user's department if found, otherwise a default name representing an unauthenticated user
     */
    private static String resolveDepartment(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith(DEPT_PREFIX))
                .map(auth -> auth.substring(DEPT_PREFIX.length()))
                .findFirst()
                .orElse(NAME_UNAUTHENTICATED_USER);
    }

    /**
     * Resolves and extracts the email address from the provided authentication token.
     *
     * @param authentication the authentication object containing the token with claims
     * @return the email address extracted from the authentication token, or null if the claim is not present
     */
    private static String resolveEmail(Authentication authentication) {
        return getJwtClaimAsString(authentication, TOKEN_EMAIL);
    }

    /**
     * Resolves and retrieves the name from the provided authentication object.
     *
     * @param authentication the authentication object containing user credentials and claims
     * @return the name extracted from the JWT claims of the authentication object, or null if not present
     */
    private static String resolveName(Authentication authentication) {
        return getJwtClaimAsString(authentication, TOKEN_NAME);
    }
}
