package com.toiter.postservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    public String extractUsername(@NotNull String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(@NotNull String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(@NotNull String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    public Long extractUserId(@NotNull String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();

        return claims.get("userId", Long.class);
    }

    public Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }

        if (principal instanceof Long) {
            return (Long) principal;
        }

        if (principal instanceof Number) {
            return ((Number) principal).longValue();
        }

        if (principal instanceof String) {
            try {
                return Long.valueOf((String) principal);
            } catch (NumberFormatException e) {
                // principal is not a numeric id string, return null to indicate unauthenticated
                return null;
            }
        }

        // Unknown principal type -> return null (treat as unauthenticated)
        return null;
    }


    public boolean isTokenValid(@NotNull String token) {
        return !isTokenExpired(token);
    }

    private boolean isTokenExpired(@NotNull String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
