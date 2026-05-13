package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.exception.MaintenanceModeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MaintenanceModeInterceptor implements HandlerInterceptor {

    private final AppConfigCacheService appConfigCacheService;

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> EXEMPT_ENDPOINTS = Set.of("/app-config/system-status", "/user/darkmode");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (appConfigCacheService.isMaintenanceMode()) {
            final String method = request.getMethod();
            final String uri = request.getRequestURI();

            if (WRITE_METHODS.contains(method) && EXEMPT_ENDPOINTS.stream().noneMatch(uri::endsWith)) {
                throw new MaintenanceModeException("System ist im Wartungsmodus!");
            }
        }
        return true;
    }
}