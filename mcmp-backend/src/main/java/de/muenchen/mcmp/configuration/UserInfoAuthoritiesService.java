package de.muenchen.mcmp.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import de.muenchen.mcmp.config.app.AppConfigCacheService;
import de.muenchen.mcmp.logging.SiemLoggingService;
import de.muenchen.mcmp.security.AuthUtils;
import de.muenchen.mcmp.security.Authorities;
import de.muenchen.mcmp.servicenow.ServiceNowService;
import de.muenchen.mcmp.user.User;
import de.muenchen.mcmp.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Service class responsible for managing and resolving authorities for a user.
 *
 * <p>This service integrates multiple authorization sources:</p>
 * <ul>
 *   <li>OAuth2 /userinfo endpoint - provides base authorities from identity provider</li>
 *   <li>Department-based roles - additional roles based on organizational unit</li>
 *   <li>Database user records - admin flags and custom permissions</li>
 * </ul>
 *
 * <p><strong>Caching:</strong> Authorities are cached for {@value #AUTHENTICATION_CACHE_ENTRY_SECONDS_TO_EXPIRE}
 * seconds per user (identified by JWT subject). This reduces load on the userinfo endpoint but means
 * permission changes may take up to 60 seconds to take effect.</p>
 *
 * <p><strong>Thread-Safety:</strong> Uses Caffeine Cache with atomic get-or-compute operations for thread-safe caching.
 * Multiple concurrent requests for the same user will only trigger one load operation.</p>
 *
 * <p><strong>Security:</strong> All authentication and authorization events are logged to SIEM
 * for audit and compliance purposes.</p>
 *
 * @since 1.0
 * @see SiemLoggingService
 * @see RoleConfiguration
 * @see DepartmentFilterConfiguration
 */
@Slf4j
@Service
public class UserInfoAuthoritiesService {

    private static final int AUTHENTICATION_CACHE_ENTRY_SECONDS_TO_EXPIRE = 60;

    private static final String CLAIM_AUTHORITIES = "authorities";
    private static final String DEPARTMENT = "department";
    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String HEADER_FORWARDED = "forwarded";
    private static final String UNKNOWN_REMOTE_IP = "unknown";
    private static final String UNKNOWN_USERNAME = "unknown";

    private final String userInfoUri;
    private final RestTemplate restTemplate;
    private final Cache<String, Collection<SimpleGrantedAuthority>> cache;
    private final Cache<String, Boolean> authSuccessLogCache;

    private final RoleConfiguration roleConfiguration;
    private final DepartmentFilterConfiguration departmentFilterConfiguration;
    private final ApiUserConfiguration apiUserConfiguration;
    private final UserService userService;
    private final SiemLoggingService siemLoggingService;
    private final ServiceNowService serviceNowService;
    private final AppConfigCacheService appConfigCacheService;

    /**
     * Creates a new instance
     *
     * @param userInfoUri userinfo endpoint URI
     * @param restTemplateBuilder a {@link RestTemplateBuilder}
     */
    public UserInfoAuthoritiesService(@Value("${security.user-info-uri}") final String userInfoUri,
                                      final RestTemplateBuilder restTemplateBuilder,
                                      final RoleConfiguration roleConfiguration,
                                      final UserService userService,
                                      final SiemLoggingService siemLoggingService,
                                      final DepartmentFilterConfiguration departmentFilterConfiguration,
                                      final ApiUserConfiguration apiUserConfiguration,
                                      final ServiceNowService serviceNowService,
                                      final AppConfigCacheService appConfigCacheService) {
        this.userInfoUri = userInfoUri;
        this.restTemplate = restTemplateBuilder.build();
        this.roleConfiguration = roleConfiguration;
        this.userService = userService;
        this.siemLoggingService = siemLoggingService;
        this.departmentFilterConfiguration = departmentFilterConfiguration;
        this.apiUserConfiguration = apiUserConfiguration;
        this.serviceNowService = serviceNowService;
        this.appConfigCacheService = appConfigCacheService;

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(AUTHENTICATION_CACHE_ENTRY_SECONDS_TO_EXPIRE, TimeUnit.SECONDS)
                .ticker(Ticker.systemTicker())
                .build();
        this.authSuccessLogCache = Caffeine.newBuilder()
                .expireAfterWrite(AUTHENTICATION_CACHE_ENTRY_SECONDS_TO_EXPIRE, TimeUnit.SECONDS)
                .ticker(Ticker.systemTicker())
                .build();
    }


    /**
     * Loads and resolves authorities (roles and permissions) for an authenticated user based on their JWT token.
     *
     * <p><strong>Architecture:</strong></p>
     * <p>This method serves as the main entry point for authority resolution and implements a multi-layered
     * approach combining OAuth2 claims, department-based roles, and database-stored permissions.</p>
     *
     * <p><strong>Caching Strategy:</strong></p>
     * <ul>
     *   <li>Uses Caffeine's Cache for thread-safe, atomic cache operations</li>
     *   <li>Cache key: JWT subject + issued-at timestamp (format: "username:epochSeconds")</li>
     *   <li>TTL: {@value #AUTHENTICATION_CACHE_ENTRY_SECONDS_TO_EXPIRE} seconds</li>
     *   <li>Each JWT token gets its own cache entry to prevent session mismatch</li>
     *   <li>Multiple concurrent requests with the same token trigger only ONE fetch operation</li>
     *   <li>Subsequent requests with the same token within TTL return cached results immediately</li>
     * </ul>
     *
     * <p><strong>Thread-Safety:</strong></p>
     * <p>Caffeine's Cache.get() with mapping function guarantees that only one thread will execute
     * the fetch operation per cache key, even under high concurrency. Other threads will block
     * and receive the same computed value. The JWT is passed directly to the fetch function.</p>
     *
     * <p><strong>Error Handling Strategy:</strong></p>
     * <ul>
     *   <li><strong>Expected authorization errors</strong> (e.g., invalid department):
     *       Logged as WARN without stack trace, rethrown as-is</li>
     *   <li><strong>Technical errors</strong> (e.g., userinfo endpoint unavailable):
     *       Logged as ERROR with full stack trace, wrapped in AuthenticationServiceException</li>
     *   <li><strong>Unexpected errors</strong>: Logged as ERROR with full stack trace,
     *       wrapped in AuthenticationServiceException</li>
     *   <li>All failures are logged to SIEM for security audit</li>
     * </ul>
     *
     * <p><strong>Security Considerations:</strong></p>
     * <ul>
     *   <li>Returns immutable collection to prevent unauthorized modification</li>
     *   <li>All authentication events (success/failure) are logged to SIEM</li>
     *   <li>Remote IP is extracted for audit trail (supports X-Forwarded-For/X-Real-IP)</li>
     * </ul>
     *
     * <p><strong>Flow:</strong></p>
     * <ol>
     *   <li>Extract username and remote IP from JWT and request context</li>
     *   <li>Log authentication attempt to SIEM</li>
     *   <li>Request authorities from cache (triggers fetch if not cached or expired)</li>
     *   <li>Log successful authentication with resolved authorities</li>
     *   <li>Return immutable collection of authorities</li>
     *   <li>On error: Log appropriately and throw AuthenticationServiceException</li>
     * </ol>
     *
     * @param jwt the JWT token from the authenticated request, must not be null
     * @return an immutable collection of {@link SimpleGrantedAuthority} objects representing
     *         all granted authorities from userinfo endpoint, department configuration, and database
     * @throws AuthenticationServiceException if the user is not authorized (e.g., wrong department),
     *         if the userinfo endpoint is unreachable, or if any unexpected error occurs during
     *         authority resolution
     * @see Cache for caching implementation details
     */
    public Collection<SimpleGrantedAuthority> loadAuthorities(final Jwt jwt) {
        if (jwt == null) {
            log.error("JWT token is null - denying access");
            throw new AuthenticationServiceException("JWT token is null");
        }
        final String username = extractUsername(jwt);
        if (username == null || username.isBlank()) {
            log.error("Username is null or blank - denying access");
            throw new AuthenticationServiceException("Username is null or blank");
        }
        final String remoteIp = getCurrentRemoteIp();

        logAuthenticationAttempt(username, remoteIp, "Authentication process started");

        try {
            final String cacheKey;
            if (jwt.getIssuedAt() != null) {
                cacheKey = jwt.getSubject() + ":" + jwt.getIssuedAt().getEpochSecond();
            } else {
                log.warn("JWT token for user {} has no issuedAt claim - using current time for cache key", username);
                cacheKey = jwt.getSubject() + ":" + java.time.Instant.now().getEpochSecond();
            }

            final boolean cacheHit = cache.getIfPresent(cacheKey) != null;
            final var now = java.time.Instant.now();
            final var exp = jwt.getExpiresAt();
            final Long secondsUntilExp = (exp == null) ? null : java.time.Duration.between(now, exp).getSeconds();

            log.info(
                    "Authorities cache lookup: user={}, cacheKey={}, hit={}, now={}, exp={}, secondsUntilExp={}",
                    username, cacheKey, cacheHit, now, exp, secondsUntilExp
            );

            Collection<SimpleGrantedAuthority> authorities = cache.get(
                    cacheKey,
                    key -> fetchAuthoritiesFromUserInfo(jwt, username, remoteIp)
            );

            if (authorities == null || authorities.isEmpty()) {
                String msg = "No authorities resolved for user " + username;
                log.warn(msg);
                logAuthenticationFailure(username, remoteIp, msg, "No authorities from UserInfo/DB");
                throw new AuthenticationServiceException(msg);
            }

            logSuccessOncePerCacheKey(cacheKey, username, remoteIp, authorities);

            log.debug("Resolved authorities: {}", authorities);

            // Return immutable collection to prevent external modification
            return Collections.unmodifiableCollection(authorities);

        } catch (RuntimeException e) {
            final Throwable cause = e.getCause() != null ? e.getCause() : e;

            if (cause instanceof AuthenticationServiceException authEx) {
                log.warn("Authorization denied for user {}: {}", username, authEx.getMessage());
                logAuthenticationFailure(username, remoteIp, authEx.getMessage(), "Authorization denied");
                throw authEx;
            }

            if (cause instanceof RestClientException restEx) {
                log.error("Failed to load authorities for user {} - UserInfo endpoint error: {}", username, restEx.getMessage(), restEx);
                logAuthenticationFailure(username, remoteIp, restEx.getMessage(), "UserInfo endpoint error");
                throw new AuthenticationServiceException("Failed to load user authorities", restEx);
            }

            log.error("Unexpected error loading authorities for user {}: {}", username, cause.getMessage(), cause);
            logAuthenticationFailure(username, remoteIp, cause.getMessage(), "Unexpected error");
            throw new AuthenticationServiceException("Failed to load user authorities", cause);
        }
    }

    /**
     * Fetches authorities from the userinfo endpoint and combines them with department and database roles.
     * This method is called by the LoadingCache when a value needs to be loaded.
     * Thread-safe: Caffeine ensures only one thread executes this per cache key.
     *
     * @return collection of authorities
     * @throws RestClientException if userinfo endpoint is unreachable
     * @throws AuthenticationServiceException if user is not authorized
     */
    private Collection<SimpleGrantedAuthority> fetchAuthoritiesFromUserInfo(final Jwt jwt, final String username, final String remoteIp) {
        log.debug("Fetching user-info for token subject: {}", jwt.getSubject());

        final var now = java.time.Instant.now();
        final var exp = jwt.getExpiresAt();
        final Long secondsUntilExp = (exp == null) ? null : Duration.between(now, exp).getSeconds();

        if (exp != null && secondsUntilExp <= 0) {
            log.error("JWT token for user {} has expired at {} (now={}, secondsUntilExp={})", username, exp, now, secondsUntilExp);
            throw new AuthenticationServiceException("JWT token has expired");
        }

        // Near-expiry guard: avoid unstable calls close to expiration (race/clock-skew)
        final long nearExpiryThresholdSeconds = 10;
        if (exp != null && secondsUntilExp <= nearExpiryThresholdSeconds) {
            log.warn(
                    "JWT token for user {} expires too soon for /userinfo call (now={}, exp={}, secondsUntilExp={}s, threshold={}s). Denying to avoid near-expiry race.",
                    username, now, exp, secondsUntilExp, nearExpiryThresholdSeconds
            );
            throw new AuthenticationServiceException("JWT token expires too soon");
        }

        if (jwt.getIssuedAt() != null) {
            long tokenAge = java.time.Instant.now().getEpochSecond() - jwt.getIssuedAt().getEpochSecond();
            if (tokenAge > 300) { // Token älter als 5 Minuten
                log.warn("JWT token for user {} is older than 5 minutes (age: {}s) - potential session expiry risk", username, tokenAge);
            }
        }

        if (apiUserConfiguration != null && apiUserConfiguration.isApiUser(username.toLowerCase())) {
            log.debug("User is an API user, skipping userinfo endpoint");
            final List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority(AuthUtils.ROLE_API));
            return authorities;
        }

        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue());
        final HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            @SuppressWarnings("unchecked")
            final Map<String, Object> map = restTemplate.exchange(
                    this.userInfoUri,
                    HttpMethod.GET,
                    entity,
                    Map.class
            ).getBody();

            if (map == null) {
                log.error("UserInfo endpoint returned null response body");
                throw new AuthenticationServiceException("UserInfo endpoint returned invalid response");
            }

            log.debug("Response from user-info Endpoint: {}", map);

            final Set<SimpleGrantedAuthority> authorities = new HashSet<>();
            final Optional<String> userDepartment = extractDepartment(map);
            userDepartment.ifPresent(dept -> authorities.add(new SimpleGrantedAuthority(AuthUtils.DEPT_PREFIX + dept)));

            // Department filter check - FAIL CLOSED (throw exception)
            if (departmentFilterConfiguration != null && departmentFilterConfiguration.isEnabled()) {
                final var allowedDepartments = departmentFilterConfiguration.getAllowedDepartments();
                if (userDepartment.isEmpty()
                        || allowedDepartments == null
                        || allowedDepartments.isEmpty()
                        || !allowedDepartments.contains(userDepartment.get())) {

                    final String deptInfo = String.format("User department: %s, Allowed departments: %s", userDepartment.orElse("none"), allowedDepartments);
                    log.warn("User {} not authorized - {}", username, deptInfo);
                    logAuthorizationEvent(username, remoteIp, "Department: " + userDepartment.orElse(""), "Denied", "User is not allowed to access MCMP");

                    throw new AuthenticationServiceException("User not authorized: department '" + userDepartment.orElse("none") + "' is not in allowed departments");
                }
            }

            // Collect special authorities (from userinfo, department, and user-specific roles)
            final Set<SimpleGrantedAuthority> specialAuthorities = new HashSet<>();
            if (map.containsKey(CLAIM_AUTHORITIES)) {
                specialAuthorities.addAll(asAuthorities(map.get(CLAIM_AUTHORITIES)));
            }
            if (roleConfiguration != null && userDepartment.isPresent()) {
                specialAuthorities.addAll(roleConfiguration.getAuthoritiesForDepartment(userDepartment.get()));
            }
            if (roleConfiguration != null) {
                specialAuthorities.addAll(roleConfiguration.getAuthoritiesForUser(username));
            }
            boolean hasSpecialRoles = !specialAuthorities.isEmpty();

            // Add special authorities to the main authorities set
            /*
            if (hasSpecialRoles) {
                if (appConfigCacheService.isMaintenanceMode()) {
                    authorities.add(new SimpleGrantedAuthority(AuthUtils.ROLE_READONLY));
                } else {
                    authorities.addAll(specialAuthorities);
                }
            }
            */
            authorities.addAll(specialAuthorities);

            // Create AuthUserInfo from JWT and map
            final AuthUtils.AuthUserInfo userInfo = new AuthUtils.AuthUserInfo(
                    username,
                    map.getOrDefault("name", "").toString(),
                    map.getOrDefault("email", "").toString(),
                    userDepartment.orElse("")
            );

            // Add database user authorities (and manage specialRole flag)
            authorities.addAll(resolveUserAuthorities(username, hasSpecialRoles, userInfo));

            if (authorities.stream().allMatch(auth -> auth.getAuthority().startsWith(AuthUtils.DEPT_PREFIX))) {
                log.warn("No authorities found for user {} - denying access", username);
                logAuthenticationFailure(username, remoteIp, "No authorities", "No authorities assigned to user");
                throw new AuthenticationServiceException("No authorities assigned to user");
            }

            logAuthorizationEvent(
                    username,
                    remoteIp,
                    "Resolved authorities",
                    "Granted",
                    authorities.toString()
            );

            return Collections.unmodifiableSet(authorities);

        } catch (HttpClientErrorException.Unauthorized e) {
            final String responseBody = e.getResponseBodyAsString();
            final String wwwAuthenticate = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE) : null;
            log.warn(
                    "UserInfo endpoint returned 401 for user {} from {}. endpoint={}, now={}, iss={}, aud={}, azp={}, sid={}, jti={}, iat={}, exp={}, secondsUntilExp={}, scope={}, wwwAuthenticate={}, claims={}, responseBody={}",
                    username,
                    remoteIp,
                    this.userInfoUri,
                    now,
                    jwt.getIssuer(),
                    jwt.getAudience(),
                    jwt.getClaimAsString("azp"),
                    jwt.getClaimAsString("sid"),
                    jwt.getClaimAsString("jti"),
                    jwt.getIssuedAt(),
                    exp,
                    secondsUntilExp,
                    jwt.getClaimAsString("scope"),
                    wwwAuthenticate,
                    safeJwtClaimsForLog(jwt),
                    responseBody
            );
            logAuthenticationFailure(username, remoteIp, "Session expired or invalid", "UserInfo 401 Unauthorized");
            throw new AuthenticationServiceException("Session expired or invalid", e);
        } catch (RestClientException e) {
            final String errorMessage = e.getMessage();
            log.error("UserInfo endpoint error: {}", errorMessage, e);
            logAuthenticationFailure(username, remoteIp, errorMessage, "UserInfo endpoint unreachable");
            throw e;
        } catch (AuthenticationServiceException e) {
            // Already logged and specific - just rethrow
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during authentication: {}", e.getMessage(), e);
            logAuthenticationFailure(username, remoteIp, e.getMessage(), "Unexpected error");
            throw new AuthenticationServiceException("Authentication failed", e);
        }
    }

    /**
     * Resolves the granted authorities for the given username based on the application's user repository,
     * and manages the user's special role flag accordingly.
     * <p>
     * The method applies the following logic:
     * </p>
     * <ul>
     *   <li>If the {@code username} is {@code null}, blank, or if {@code userService} is not available,
     *       an empty, immutable list of authorities is returned.</li>
     *   <li>The user is looked up via {@code userService.findByUsername(username)}.</li>
     *   <li>If the user exists:
     *     <ul>
     *       <li>The {@code specialRole} flag is updated to reflect whether the user has effective special roles,
     *           where effective special roles are {@code true} if {@code hasSpecialRoles} is {@code true} or
     *           if the user is marked as an admin (i.e. {@code getAdmin()} returns {@code Boolean.TRUE}).</li>
     *       <li>A default {@code ROLE_USER} authority is always granted.</li>
     *       <li>If the user is marked as an admin (i.e. {@code getAdmin()} returns {@code Boolean.TRUE}),
     *           an additional {@code ROLE_ADMIN} authority is granted.</li>
     *       <li>The resulting list of authorities is immutable.</li>
     *     </ul>
     *   </li>
     *   <li>If the user does not exist and {@code hasSpecialRoles} is {@code true}:
     *     <ul>
     *       <li>A new user is created with {@code specialRole} set to {@code true}, using the provided {@code userInfo} for the fields.</li>
     *       <li>The new user is saved via {@code userService.save(user)}.</li>
     *       <li>A default {@code ROLE_USER} authority is granted.</li>
     *     </ul>
     *   </li>
     *   <li>If the user does not exist and {@code hasSpecialRoles} is {@code false}:
     *     <ul>
     *       <li>No user is created, and an empty list of authorities is returned.</li>
     *     </ul>
     *   </li>
     *   <li>If an exception occurs during lookup or save:
     *     <ul>
     *       <li>A debug log entry is written with the error message.</li>
     *       <li>A security event is logged to capture the failed lookup attempt, including the remote IP.</li>
     *       <li>An empty, immutable list of authorities is returned.</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <p><strong>Security considerations:</strong></p>
     * <ul>
     *   <li>Admin privileges are derived exclusively from the database flag {@code user.getAdmin()}.</li>
     *   <li>The {@code specialRole} flag is updated to reflect effective special roles, including admin status.</li>
     *   <li>The returned list is immutable to prevent callers from modifying the authorities after resolution.</li>
     * </ul>
     *
     * @param username the technical username used to look up the user in the underlying user service;
     *                 may be {@code null} or blank, in which case no authorities are granted
     * @param hasSpecialRoles {@code true} if the user has special roles from authorities sources, {@code false} otherwise
     * @param userInfo the user information extracted from JWT and /userinfo endpoint
     * @return an immutable {@link java.util.List} of {@link org.springframework.security.core.authority.SimpleGrantedAuthority}
     *         containing at least {@code ROLE_USER} for valid users, plus {@code ROLE_ADMIN} if the user
     *         has admin privileges; returns an empty list if the user cannot be resolved or an error occurs
     */
    private List<SimpleGrantedAuthority> resolveUserAuthorities(final String username, final boolean hasSpecialRoles, final AuthUtils.AuthUserInfo userInfo) {
        if (username == null || username.isBlank() || userService == null) {
            return Collections.emptyList();
        }
        try {
            var userOptional = userService.findByUsername(username);
            if (userOptional.isPresent()) {
                final var user = userOptional.get();
                // Consider admin as a special role for updating specialRole flag
                boolean effectiveHasSpecialRoles = hasSpecialRoles || Boolean.TRUE.equals(user.getAdmin());
                updateSpecialRoleIfNeeded(user, effectiveHasSpecialRoles, username);

                if (Boolean.TRUE.equals(user.getAdmin())) {
                    return List.of(
                            new SimpleGrantedAuthority(AuthUtils.ROLE_USER),
                            new SimpleGrantedAuthority(AuthUtils.ROLE_ADMIN)
                    );
                } else {
                    return List.of(new SimpleGrantedAuthority(AuthUtils.ROLE_USER));
                }
            } else if (hasSpecialRoles) {
                final String sysId = getSysId(username);
                final User newUser = createNewUser(username, sysId, userInfo);
                userService.save(newUser);
                return List.of(new SimpleGrantedAuthority(AuthUtils.ROLE_USER));
            }
        } catch (Exception e) {
            log.debug("Error finding or saving user by username '{}': {}", username, e.getMessage());
            logSecurityEvent(username, getCurrentRemoteIp(), "User lookup or save failed", e.getMessage());
        }
        return Collections.emptyList();
    }

    private void updateSpecialRoleIfNeeded(User user, boolean effectiveHasSpecialRoles, String username) {
        if (Boolean.TRUE.equals(user.getSpecialRole()) != effectiveHasSpecialRoles) {
            user.setSpecialRole(effectiveHasSpecialRoles);
            try {
                userService.save(user);
            } catch (Exception e) {
                log.error("Failed to save user with username: " + username, e);
            }
        }
    }

    private String getSysId(String username) {
        try {
            return serviceNowService.getSysIdByUsername(username).orElse(null);
        } catch (Exception e) {
            log.warn("Error getting sysId for username {}: {}", username, e.getMessage());
            return username;
        }
    }

    private User createNewUser(String username, String sysId, AuthUtils.AuthUserInfo userInfo) {
        final User newUser = new User();
        newUser.setUsername(username);
        newUser.setSysId(sysId);
        newUser.setDepartment(userInfo.department());
        newUser.setName(userInfo.name());
        newUser.setEmail(userInfo.email());
        newUser.setAdmin(false);
        newUser.setSpecialRole(true);
        return newUser;
    }

    private static Set<SimpleGrantedAuthority> asAuthorities(final Object object) {
        final Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        Object authoritiesObject = object;
        if (authoritiesObject instanceof Collection<?> collection) {
            authoritiesObject = collection.toArray(new Object[0]);
        }
        if (ObjectUtils.isArray(authoritiesObject)) {
            authorities.addAll(
                    Stream.of((Object[]) authoritiesObject)
                            .map(Object::toString)
                            .map(SimpleGrantedAuthority::new)
                            .toList());
        }
        return authorities;
    }

    /**
     * Invalidates all cache entries for the given username.
     * Use this when user permissions change and you need immediate effect.
     *
     * <p>Since cache keys now include token-specific information (JWT ID or issuedAt),
     * we need to search for all keys belonging to this user.</p>
     *
     * @param username the username (subject) to invalidate cache for
     */
    public void invalidateUserCache(final String username) {
        if (username == null || username.isBlank()) {
            log.warn("Cannot invalidate cache: username is null or blank");
            return;
        }

        // Find and invalidate all cache entries for this user
        long invalidatedCount = cache.asMap().keySet().stream()
                .filter(key -> key.equals(username) || key.startsWith(username + ":"))
                .peek(key -> cache.invalidate(key))
                .count();

        log.info("Cache invalidated for user '{}': {} entries removed", username, invalidatedCount);
    }

    private Optional<String> extractDepartment(final Map<String, Object> map) {
        if (map == null || !map.containsKey(DEPARTMENT)) {
            return Optional.empty();
        }
        final Object deptObj = map.get(DEPARTMENT);
        return deptObj instanceof String ? Optional.of(deptObj.toString()) : Optional.empty();
    }

    protected String extractUsername(Jwt jwt) {
        if (jwt == null) {
            return UNKNOWN_USERNAME;
        }

        String username = jwt.getClaimAsString(PREFERRED_USERNAME);
        if (username == null) {
            username = jwt.getClaimAsString("username");
        }
        if (username == null) {
            username = jwt.getClaimAsString("sub");
        }
        if (username == null) {
            username = jwt.getSubject();
        }
        return username;
    }

    protected String getCurrentRemoteIp() {
        try {
            final ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // RFC 7239 'Forwarded' header
                final String authorization = request.getHeader(HEADER_FORWARDED);

                if (authorization != null && !authorization.isEmpty()) {
                    final String forwardedIp = extractIpFromAuthorizationHeader(authorization);
                    if (forwardedIp != null && (isValidIpv4(forwardedIp) || isValidIpv6(forwardedIp))) {
                        log.debug("Extracted IP from Forwarded header: {}", forwardedIp);
                        return forwardedIp;
                    }
                }

                final String xForwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    log.debug("Extracted IP from X-Forwarded-For header: {}", xForwardedFor);
                    return xForwardedFor.split(",")[0].trim();
                }

                final String xRealIp = request.getHeader(HEADER_X_REAL_IP);
                if (xRealIp != null && !xRealIp.isEmpty()) {
                    if (isValidIpv4(xRealIp) || isValidIpv6(xRealIp)) {
                        log.debug("Extracted IP from X-Real-IP header: {}", xRealIp);
                        return xRealIp;
                    }
                    log.warn("Invalid IP format in X-Real-IP header: {}", xRealIp.replaceAll("[\r\n]", "_"));
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not determine remote IP: {}", e.getMessage());
        }
        return UNKNOWN_REMOTE_IP;
    }

    /**
     * Extracts the IP address from the Authorization header.
     * Example formats:
     *   proto=https;host=mcmp.example.org;for="10.1.2.3:49288"
     *   proto=http;host="localhost:8080";for="[0:0:0:0:0:0:0:1]:59542"
     *
     * @param authorizationHeader the Authorization Header value
     * @return the extracted IP address or null if not found
     */
    protected String extractIpFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        try {
            // The header consists of parameter-value pairs separated by semicolons
            String[] parts = authorizationHeader.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.toLowerCase(Locale.ROOT).startsWith("for=")) {
                    String forPart = part.substring(4).trim();

                    // Remove surrounding quotes
                    forPart = forPart.replaceAll("^\"|\"$", "").trim();

                    // Handle IPv6 in square brackets [2001:db8:cafe::17]:4711
                    if (forPart.startsWith("[")) {
                        int closingBracket = forPart.indexOf(']');
                        if (closingBracket != -1) {
                            String ip = forPart.substring(1, closingBracket).trim();
                            if (isValidIpv6(ip)) {
                                return ip;
                            }
                        }
                    } else {
                        // Handle IPv4 or bare IPv6 and strip port if present
                        int colonIndex = forPart.lastIndexOf(':');
                        // If there's exactly one colon and it's not followed by hex-only chars, it's likely a port (IPv4:port)
                        // If it's an IPv6 without brackets, it shouldn't have a port per RFC 7239,
                        // but we handle standard cases here.
                        String ipCandidate = forPart;
                        if (colonIndex != -1 && !forPart.contains("]")) {
                            // Check if it looks like IPv4 with port or just IPv6
                            if (isValidIpv4(forPart.substring(0, colonIndex))) {
                                ipCandidate = forPart.substring(0, colonIndex);
                            }
                        }

                        if (isValidIpv4(ipCandidate) || isValidIpv6(ipCandidate)) {
                            return ipCandidate;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract IP from Forwarded header: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Simple IPv4 validation suitable for logging purposes.
     */
    private boolean isValidIpv4(String ip) {
        return ip != null && ip.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
    }

    /**
     * Simple IPv6 validation:
     * - must contain at least one colon
     * - must consist of hex digits and colons only
     * This is intentionally lightweight and primarily used for filtering obvious non-IP values.
     */
    private boolean isValidIpv6(String ip) {
        if (ip == null) {
            return false;
        }
        if (!ip.contains(":")) {
            return false;
        }
        return ip.matches("^[0-9a-fA-F:]+$");
    }

    private static Map<String, Object> safeJwtClaimsForLog(final Jwt jwt) {
        if (jwt == null) {
            return Map.of();
        }

        // Keys, die typischerweise sensibel/PII sind oder Log-Spam verursachen können:
        final Set<String> denyList = Set.of(
                "access_token", "token", "id_token", "refresh_token", "authorization",
                "password", "secret",
                "email", "mail",
                "name", "given_name", "family_name",
                "phone_number",
                "address"
        );

        final int maxStringLen = 200;

        final Map<String, Object> result = new LinkedHashMap<>();
        jwt.getClaims().forEach((k, v) -> {
            if (k == null) return;

            final String key = k.toLowerCase(Locale.ROOT);
            if (denyList.contains(key)) {
                result.put(k, "<redacted>");
                return;
            }

            if (v instanceof String s) {
                result.put(k, s.length() <= maxStringLen ? s : (s.substring(0, maxStringLen) + "…<truncated>"));
                return;
            }

            // Collections/Maps können riesig sein; hier nur "as is" – optional später begrenzen.
            result.put(k, v);
        });

        return Collections.unmodifiableMap(result);
    }

    private void logAuthenticationAttempt(String username, String remoteIp, String details) {
        log.debug("Auth attempt for user {} from {}", username, remoteIp);
    }

    private void logAuthenticationSuccess(String username, String remoteIp, Collection<SimpleGrantedAuthority> authorities, String details) {
        siemLoggingService.logAuthSuccess(username, remoteIp, authorities, details);
    }

    private void logAuthenticationFailure(String username, String remoteIp, String error, String details) {
        siemLoggingService.logAuthFailure(username, remoteIp, error, details);
    }

    private void logAuthorizationEvent(String username, String remoteIp, String resource, String result, String roles) {
        log.debug("Authorization for user {} on resource {}: {}", username, resource, result);
    }

    private void logPrivilegeEscalation(String username, String remoteIp, String privilege, String reason) {
        siemLoggingService.logAdminAccess(username, remoteIp);
    }

    private void logSecurityEvent(String username, String remoteIp, String event, String details) {
        siemLoggingService.logSecurityError(username, remoteIp, event + ": " + details);
    }

    private void logSuccessOncePerCacheKey(
            final String cacheKey,
            final String username,
            final String remoteIp,
            final Collection<SimpleGrantedAuthority> authorities
    ) {
        final Boolean previous = authSuccessLogCache.asMap().putIfAbsent(cacheKey, Boolean.TRUE);
        if (previous != null) {
            return;
        }

        logAuthorizationEvent(
                username,
                remoteIp,
                "Resolved authorities",
                "Granted",
                authorities.toString()
        );
        logAuthenticationSuccess(username, remoteIp, authorities, "Authentication completed");
    }
}
