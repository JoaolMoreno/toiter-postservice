package com.toiter.postservice.config;

import com.toiter.postservice.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

/**
 * Filter to apply rate limiting to requests based on user ID or IP.
 * Runs before authentication to limit unauthenticated traffic by IP.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService rateLimitService;
    private final boolean enabled;

    public RateLimitFilter(RateLimitService rateLimitService, @Value("${rate-limit.enabled:true}") boolean enabled) {
        this.rateLimitService = rateLimitService;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // small log to use the logger field in most flows
        logger.debug("RateLimitFilter processing request: {} {}", request.getMethod(), request.getRequestURI());

        if (!enabled || !rateLimitService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip rate limiting for certain paths
        if (shouldSkipRateLimiting(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract user ID from SecurityContext if available
        Long userId = extractUserIdFromSecurityContext();

        // Extract IP address from request
        String ipAddress = extractIpAddress(request);

        // Determine request type
        RateLimitService.RequestType requestType = determineRequestType(method);

        // Check rate limit
        if (!rateLimitService.isAllowed(userId, ipAddress, requestType)) {
            long resetTime = rateLimitService.getResetTime(userId, ipAddress, requestType);
            int limit = rateLimitService.getLimitForType(requestType);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + resetTime));
            response.setHeader("Retry-After", String.valueOf(resetTime));
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                    "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please try again in %d seconds.\"}",
                    resetTime
            ));
            return;
        }

        // Add rate limit headers to response (best-effort)
        long remaining = rateLimitService.getRemainingRequests(userId, ipAddress, requestType);
        long resetTime = rateLimitService.getResetTime(userId, ipAddress, requestType);
        int limit = rateLimitService.getLimitForType(requestType);

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + resetTime));

        filterChain.doFilter(request, response);
    }

    /**
     * Determine if rate limiting should be skipped for this path.
     */
    private boolean shouldSkipRateLimiting(String path) {
        return path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/images/") ||
                path.startsWith("/internal/") ||
                path.startsWith("/api/internal/");
    }

    private Long extractUserIdFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        try {
            return Long.parseLong(principal.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract IP address from the request, considering proxy headers.
     */
    private String extractIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress;
    }

    /**
     * Determine the request type for rate limiting purposes.
     */
    private RateLimitService.RequestType determineRequestType(String method) {
        if (HttpMethod.GET.matches(method)) {
            return RateLimitService.RequestType.GET;
        }
        return RateLimitService.RequestType.OTHER;
    }
}
