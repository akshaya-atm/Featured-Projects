package com.akshaya.shopsphere.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    // Signing key comes from JWT_SECRET so it never has to live in source control; falls back
    // to a local-dev-only default when unset.
    private static final String DEFAULT_LOCAL_SECRET = "ShopSphereSuperSecretSigningKeyForJWTTokenSecurity2026!";
    private static final String SECRET_STRING = System.getenv("JWT_SECRET") != null
            ? System.getenv("JWT_SECRET")
            : DEFAULT_LOCAL_SECRET;
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes(StandardCharsets.UTF_8));
    private static final long EXPIRATION_TIME = 86_400_000L; // 24 hours in milliseconds

    public static String generateToken(int userId, String userType, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("userType", userType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }

    public static Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
