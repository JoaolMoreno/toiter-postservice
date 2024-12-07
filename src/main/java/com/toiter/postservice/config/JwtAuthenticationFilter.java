package com.toiter.postservice.config;

import com.toiter.postservice.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final JwtService jwtService;

    @Value("${service.shared-key}")
    private String sharedKey;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        final String authHeader = request.getHeader("Authorization");

        logger.debug(String.format("Path: %s, AuthHeader: %s", path, authHeader));

        // Ignorar validação para rotas públicas (já configuradas no SecurityConfig)
        if (path.startsWith("/auth/") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) {
            logger.debug("Ignorando validação JWT para rota pública");
            filterChain.doFilter(request, response);
            return;
        }

        // Validação para /internal/** com token compartilhado
        if (path.startsWith("/internal/")) {
            logger.debug("Validando token compartilhado para rota /internal/**");
            if (authHeader == null || !authHeader.equals("Bearer " + sharedKey)) {
                logger.warn("Acesso não autorizado para /internal/**");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Acesso não autorizado para /internal/**");
                return;
            }

            logger.debug("Token compartilhado válido para rota /internal/**");
            filterChain.doFilter(request, response);
            return;
        }

        logger.debug("Validando token JWT para outras rotas");
        // Validação JWT para outros endpoints
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Token JWT não encontrado ou malformado");
            filterChain.doFilter(request, response);
            return;
        }
        logger.debug("Token JWT encontrado");

        final String jwt = authHeader.substring(7);

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
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token JWT inválido ou malformado");
            return;
        }

        filterChain.doFilter(request, response);
    }
}