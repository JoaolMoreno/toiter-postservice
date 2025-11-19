package com.toiter.postservice.config;

import com.toiter.postservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    @Value("${service.shared-key}")
    private String sharedKey;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Extrai o JWT do cookie HttpOnly 'accessToken'.
     * 
     * @param request HttpServletRequest
     * @return JWT token se encontrado, null caso contrário
     */
    private String extractJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if ("accessToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean isInternal = path.startsWith("/internal/") || path.startsWith("/api/internal/");

        logger.info("Request: " + request.getMethod() + " " + path);

        // Ignorar validação para rotas públicas (já configuradas no SecurityConfig)
        if ((path.startsWith("/auth/") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui") || path.startsWith("/posts/thread/"))) {
            logger.debug("Ignorando validação JWT para rota pública: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Validação para /internal/** com token compartilhado
        if (isInternal) {
            logger.debug("Validando token compartilhado para rota /internal/**");
            final String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.equals("Bearer " + sharedKey)) {
                logger.warn("Acesso não autorizado para /internal/** - path: {}", path);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Acesso não autorizado");
                return;
            }

            logger.debug("Token compartilhado válido para rota /internal/**");
            filterChain.doFilter(request, response);
            return;
        }

        // Para rotas não-internas, priorizar cookie HttpOnly 'accessToken'
        String jwt = extractJwtFromCookie(request);
        
        // Fallback: tentar extrair do header Authorization (para clientes não-browser)
        if (jwt == null) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
                logger.debug("JWT extraído do header Authorization (fallback)");
            }
        } else {
            logger.debug("JWT extraído do cookie HttpOnly 'accessToken'");
        }

        // Se não encontrou JWT, prosseguir sem autenticação (usuário anônimo)
        if (jwt == null) {
            logger.debug("Nenhum JWT encontrado - prosseguindo sem autenticação");
            filterChain.doFilter(request, response);
            return;
        }

        // Validar e processar o JWT
        try {
            String username = jwtService.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(jwt)) {
                    Long userId = jwtService.extractUserId(jwt);

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    logger.debug("Usuário autenticado com sucesso - userId: {}", userId);
                } else {
                    logger.warn("Token JWT inválido ou expirado");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Token inválido ou expirado");
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("Erro ao processar token JWT: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token inválido ou malformado");
            return;
        }

        filterChain.doFilter(request, response);
    }
}