package com.example.demo.presentation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds baseline security headers to every response.
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} everywhere, so browsers never reinterpret a response type.</li>
 *   <li>{@code Referrer-Policy: no-referrer} everywhere.</li>
 *   <li>For the JSON API only ({@code /api/**}): a CSP that forbids loading anything and framing, plus
 *       {@code X-Frame-Options: DENY}. The Swagger UI is excluded from the CSP because it needs scripts and styles.</li>
 * </ul>
 *
 * {@code Strict-Transport-Security} is deliberately not set here: it is only meaningful over HTTPS, which is
 * terminated by the reverse proxy / load balancer in front of the application.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");

        if (request.getRequestURI().startsWith(API_PREFIX)) {
            response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
            response.setHeader("X-Frame-Options", "DENY");
        }

        filterChain.doFilter(request, response);
    }
}
