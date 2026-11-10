package com.example.demo.presentation.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void shouldAddFullSetOfHeadersToApiResponses() throws ServletException, IOException {
        MockHttpServletResponse response = run("/api/v1/products");

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("default-src 'none'; frame-ancestors 'none'", response.getHeader("Content-Security-Policy"));
    }

    @Test
    void shouldNotRestrictSwaggerUiWithTheApiContentSecurityPolicy() throws ServletException, IOException {
        MockHttpServletResponse response = run("/swagger-ui/index.html");

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
        assertNull(response.getHeader("Content-Security-Policy"));
        assertNull(response.getHeader("X-Frame-Options"));
    }

    @Test
    void shouldContinueTheFilterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setRequestURI("/api/v1/products");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertSame(request, chain.getRequest());
    }

    private MockHttpServletResponse run(String uri) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
