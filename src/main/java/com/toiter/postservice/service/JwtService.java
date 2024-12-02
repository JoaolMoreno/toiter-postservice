package com.toiter.postservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.validation.constraints.Min;
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
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            throw new IllegalArgumentException("Principal cannot be null");
        }
        return (Long) principal;
    }


    public boolean isTokenValid(@NotNull String token,@NotNull String usarname) {
        return usarname.equals(extractUsername(token)) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(@NotNull String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}
