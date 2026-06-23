package de.muenchen.mcmp.config.app;

import de.muenchen.mcmp.exception.MaintenanceModeException;
import de.muenchen.mcmp.types.SystemMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
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
        final SystemMode mode = appConfigCacheService.getSystemMode();

        if (mode == SystemMode.NORMAL || mode == SystemMode.INFO) {
            return true;
        }

        final String method = request.getMethod();
        final String uri = request.getRequestURI();

        if (EXEMPT_ENDPOINTS.stream().anyMatch(uri::endsWith)) {
            return true;
        }

        if (WRITE_METHODS.contains(method)) {
            if (mode == SystemMode.FRONTEND_READ_ONLY) {
                // Im FRONTEND_READ_ONLY Modus dürfen Methoden im clients Package schreiben
                if (handler instanceof HandlerMethod handlerMethod) {
                    String packageName = handlerMethod.getBeanType().getPackageName();
                    if (packageName.startsWith("de.muenchen.mcmp.clients")) {
                        return true;
                    }
                }
            }

            throw new MaintenanceModeException("System befindet sich im Modus: " + mode);
        }

        return true;
    }
}