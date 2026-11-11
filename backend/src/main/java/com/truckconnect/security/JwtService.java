package com.truckconnect.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-ms}")
    private long accessTokenMs;

    @Value("${app.jwt.refresh-token-ms}")
    private long refreshTokenMs;

    private SecretKey signingKey() {
        // The JWT_SECRET env var must be Base64 for a key of
        // at least 256 bits. Generate one locally, e.g.:
        // openssl rand -base64 64
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserDetails user) {
        return buildToken(user, accessTokenMs, "ACCESS");
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(user, refreshTokenMs, "REFRESH");
    }

    private String buildToken(UserDetails user, long lifetimeMs, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername()) // the email
                .claims(Map.of("type", type))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + lifetimeMs))
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public boolean isTokenValid(String token, UserDetails user) {
        return extractUsername(token).equals(user.getUsername())
                && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
