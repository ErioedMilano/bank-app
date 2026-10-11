package com.erioed.bank.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {
    private static final String SECRET = System.getenv().getOrDefault(
            "JWT_SECRET",
            "dev-secret-change-me-in-production-min-32-characters-long!!"
    );
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private static final long EXPIRY_MS = 24L * 60 * 60 * 1000;

    public static String generate(int userId, String email, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
                .signWith(KEY)
                .compact();
    }

    public static Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static int getUserId(io.jsonwebtoken.Claims claims) {
        return Integer.parseInt(claims.getSubject());
    }
}