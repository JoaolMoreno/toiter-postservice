package com.toiter.postservice.config;

import com.toiter.postservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private PrintWriter writer;

    private JwtAuthenticationFilter filter;

    private static final String SHARED_KEY = "test-shared-key";
    private static final String VALID_JWT = "valid.jwt.token";
    private static final String INVALID_JWT = "invalid.jwt.token";
    private static final String USERNAME = "testuser";
    private static final Long USER_ID = 123L;

    @BeforeEach
    void setUp() throws IOException {
        filter = new JwtAuthenticationFilter(jwtService);
        ReflectionTestUtils.setField(filter, "sharedKey", SHARED_KEY);
        
        // Clear security context before each test
        SecurityContextHolder.clearContext();
    }

    @Test
    void testPublicRouteSwagger_shouldBypassAuthentication() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testPublicRouteApiDocs_shouldBypassAuthentication() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/v3/api-docs/swagger-config");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testPublicRouteThreadView_shouldBypassAuthentication() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts/thread/123");
        when(request.getMethod()).thenReturn("GET");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testInternalRoute_withValidSharedKey_shouldAuthenticate() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/internal/posts/count");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + SHARED_KEY);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void testInternalRoute_withInvalidSharedKey_shouldReject() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/internal/posts/count");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer wrong-key");
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(writer).write("Acesso não autorizado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testInternalRoute_withoutAuthHeader_shouldReject() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/internal/posts/count");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(writer).write("Acesso não autorizado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testAuthenticatedRoute_withValidCookie_shouldAuthenticate() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        Cookie accessTokenCookie = new Cookie("accessToken", VALID_JWT);
        when(request.getCookies()).thenReturn(new Cookie[]{accessTokenCookie});
        
        when(jwtService.extractUsername(VALID_JWT)).thenReturn(USERNAME);
        when(jwtService.isTokenValid(VALID_JWT)).thenReturn(true);
        when(jwtService.extractUserId(VALID_JWT)).thenReturn(USER_ID);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(USER_ID, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void testAuthenticatedRoute_withValidAuthHeader_shouldAuthenticateAsFallback() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        when(request.getCookies()).thenReturn(null); // No cookies
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_JWT);
        
        when(jwtService.extractUsername(VALID_JWT)).thenReturn(USERNAME);
        when(jwtService.isTokenValid(VALID_JWT)).thenReturn(true);
        when(jwtService.extractUserId(VALID_JWT)).thenReturn(USER_ID);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(USER_ID, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void testAuthenticatedRoute_cookieTakesPrecedenceOverHeader() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        
        // Both cookie and header present - cookie should be used
        Cookie accessTokenCookie = new Cookie("accessToken", VALID_JWT);
        when(request.getCookies()).thenReturn(new Cookie[]{accessTokenCookie});
        // Note: Header is present but should not be used since cookie is available
        lenient().when(request.getHeader("Authorization")).thenReturn("Bearer different.jwt.token");
        
        when(jwtService.extractUsername(VALID_JWT)).thenReturn(USERNAME);
        when(jwtService.isTokenValid(VALID_JWT)).thenReturn(true);
        when(jwtService.extractUserId(VALID_JWT)).thenReturn(USER_ID);

        filter.doFilterInternal(request, response, filterChain);

        // Should use the cookie token, not the header token
        verify(jwtService).extractUsername(VALID_JWT);
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testAuthenticatedRoute_withInvalidToken_shouldReject() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        Cookie accessTokenCookie = new Cookie("accessToken", INVALID_JWT);
        when(request.getCookies()).thenReturn(new Cookie[]{accessTokenCookie});
        when(response.getWriter()).thenReturn(writer);
        
        when(jwtService.extractUsername(INVALID_JWT)).thenThrow(new RuntimeException("Invalid token"));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(writer).write("Token inválido ou malformado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testAuthenticatedRoute_withExpiredToken_shouldReject() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        Cookie accessTokenCookie = new Cookie("accessToken", VALID_JWT);
        when(request.getCookies()).thenReturn(new Cookie[]{accessTokenCookie});
        when(response.getWriter()).thenReturn(writer);
        
        when(jwtService.extractUsername(VALID_JWT)).thenReturn(USERNAME);
        when(jwtService.isTokenValid(VALID_JWT)).thenReturn(false); // Token expired

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(writer).write("Token inválido ou expirado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void testAuthenticatedRoute_withoutToken_shouldProceedUnauthenticated() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        when(request.getCookies()).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        // Should proceed without authentication (will be blocked by Spring Security)
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testAuthenticatedRoute_withMultipleCookies_shouldExtractCorrectOne() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        
        Cookie otherCookie = new Cookie("sessionId", "session123");
        Cookie accessTokenCookie = new Cookie("accessToken", VALID_JWT);
        Cookie anotherCookie = new Cookie("preference", "dark-mode");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie, accessTokenCookie, anotherCookie});
        
        when(jwtService.extractUsername(VALID_JWT)).thenReturn(USERNAME);
        when(jwtService.isTokenValid(VALID_JWT)).thenReturn(true);
        when(jwtService.extractUserId(VALID_JWT)).thenReturn(USER_ID);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtService).extractUsername(VALID_JWT);
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testAuthenticatedRoute_withWrongCookieName_shouldFallbackToHeader() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/posts");
        when(request.getMethod()).thenReturn("GET");
        
        Cookie wrongCookie = new Cookie("wrongName", VALID_JWT);
        when(request.getCookies()).thenReturn(new Cookie[]{wrongCookie});
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_JWT);
        
        when(jwtService.extractUsername(VALID_JWT)).thenReturn(USERNAME);
        when(jwtService.isTokenValid(VALID_JWT)).thenReturn(true);
        when(jwtService.extractUserId(VALID_JWT)).thenReturn(USER_ID);

        filter.doFilterInternal(request, response, filterChain);

        // Should use header as fallback
        verify(jwtService).extractUsername(VALID_JWT);
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testInternalRouteWithApiPrefix_shouldUseSharedKey() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/internal/some-endpoint");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + SHARED_KEY);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
