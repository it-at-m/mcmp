package de.muenchen.mcmp.configuration;

import de.muenchen.mcmp.config.app.AppConfigCacheService;
import de.muenchen.mcmp.logging.SiemLoggingService;
import de.muenchen.mcmp.servicenow.ServiceNowService;
import de.muenchen.mcmp.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.restclient.autoconfigure.RestTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The central class for configuration of all security aspects.
 * Automatically used when not running with profile `no-security`.
 * Configures all endpoints to require authentication via access token.
 * (except the Spring Boot Actuator endpoints)
 * Additionally it configures the use of the {@link UserInfoAuthoritiesService}.
 */
@RequiredArgsConstructor
@Configuration
@Profile("!no-security")
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@Import(RestTemplateAutoConfiguration.class)
public class SecurityConfiguration {

    private final RestTemplateBuilder restTemplateBuilder;

    private final SecurityProperties securityProperties;

    private final RoleConfiguration roleConfiguration;

    private final UserService userService;

    private final SiemLoggingService siemLoggingService;

    private final DepartmentFilterConfiguration departmentFilterConfiguration;

    private final ApiUserConfiguration apiUsersConfiguration;

    private final ServiceNowService serviceNowService;

    private final AppConfigCacheService appConfigCacheService;

    @Bean
    public UserInfoAuthoritiesService userInfoAuthoritiesService() {
        return new UserInfoAuthoritiesService(
                securityProperties.getUserInfoUri(),
                restTemplateBuilder,
                roleConfiguration,
                userService,
                siemLoggingService,
                departmentFilterConfiguration,
                apiUsersConfiguration,
                serviceNowService,
                appConfigCacheService
        );
    }

    @Bean
    @Order(0)
    public SecurityFilterChain clientAccessFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/clients/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtUserInfoAuthenticationConverter(userInfoAuthoritiesService())))
                );
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((requests) -> requests.requestMatchers(
                                // allow access to /actuator/info
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/info"),
                                // allow access to /actuator/health for OpenShift Health Check
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health"),
                                // allow access to /actuator/health/liveness for OpenShift Liveness Check
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/liveness"),
                                // allow access to /actuator/health/readiness for OpenShift Readiness Check
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/readiness"),
                                // allow access to /actuator/metrics for Prometheus monitoring in OpenShift
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/metrics"))
                        .permitAll())
                .authorizeHttpRequests((requests) -> requests.requestMatchers("/**")
                        .authenticated())
                .oauth2ResourceServer(httpSecurityOAuth2ResourceServerConfigurer -> httpSecurityOAuth2ResourceServerConfigurer
                        .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(new JwtUserInfoAuthenticationConverter(
                                userInfoAuthoritiesService()))));
        return http.build();
    }
}
