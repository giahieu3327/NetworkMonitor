package com.network_monitor.portal_service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.experimental.UtilityClass;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@UtilityClass
public class JwtTokenUtils {

    public String generateToken(String email, String secret, long expireMinutes) {
        SecretKey key = getKey(secret);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMinutes * 60_000L);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    public String getEmail(String token, String secret) {
        return parseToken(token, secret).getSubject();
    }

    public boolean isValid(String token, String secret) {
        try {
            parseToken(token, secret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseToken(String token, String secret) {
        SecretKey key = getKey(secret);

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getKey(String secret) {
        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }
}