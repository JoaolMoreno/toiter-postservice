package com.toiter.postservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String extractUsername(@NotNull String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(@NotNull String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(@NotNull String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long extractUserId(@NotNull String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        return switch (principal) {
            case Long l -> l;
            case Number n -> n.longValue();
            case String s -> {
                try {
                    yield Long.valueOf(s);
                } catch (NumberFormatException e) {
                    // principal is not a numeric id string, return null to indicate unauthenticated
                    yield null;
                }
            }
            case null, default -> null;
        };
    }


    public boolean isTokenValid(@NotNull String token) {
        return !isTokenExpired(token);
    }

    private boolean isTokenExpired(@NotNull String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
