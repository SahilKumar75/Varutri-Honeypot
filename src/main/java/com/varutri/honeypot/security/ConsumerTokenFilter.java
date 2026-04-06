package com.varutri.honeypot.security;

import com.varutri.honeypot.service.security.ConsumerTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces short-lived bearer tokens on consumer endpoints.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ConsumerTokenFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ConsumerTokenService consumerTokenService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/consumer")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/consumer/auth/token")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid consumer bearer token");
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        var claims = consumerTokenService.validateToken(token);
        if (claims.isEmpty()) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "Consumer bearer token is invalid or expired");
            return;
        }

        request.setAttribute("consumerDeviceId", claims.get().getDeviceId());
        request.setAttribute("consumerAppId", claims.get().getAppId());
        request.setAttribute("consumerPlatform", claims.get().getPlatform());

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"status\":" + status
                + ",\"message\":\"Unauthorized\",\"error\":{\"code\":\"CONSUMER_TOKEN_ERROR\",\"details\":\""
                + message + "\"}}");
        log.warn("Consumer token auth failed: {}", message);
    }
}