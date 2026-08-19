package de.muenchen.mcmp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.List;

/**
 * Wraps changing (POST/PUT/PATCH/DELETE) requests in a {@link ContentCachingRequestWrapper} so
 * their JSON body remains readable after Spring MVC has already consumed it (e.g. for
 * {@code @RequestBody} binding). This lets {@code GlobalExceptionHandler} capture the request
 * body of a failed request for the error log, without needing to read the (single-read) input
 * stream itself - which would either fail or return nothing at that point.
 */
@Component
@Order(2)
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    private static final List<String> CACHED_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Caps how much of the request body is buffered in memory. Well above what's ultimately
     * persisted (see {@code ErrorLogService.MAX_BODY_LENGTH}), it only guards against holding an
     * unbounded amount of a huge payload in memory just to cache it.
     */
    private static final int CONTENT_CACHE_LIMIT = 65536;

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {
        if (CACHED_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(new ContentCachingRequestWrapper(request, CONTENT_CACHE_LIMIT), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
