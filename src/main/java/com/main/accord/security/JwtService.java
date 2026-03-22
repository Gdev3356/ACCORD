package com.main.accord.security;

import com.main.accord.domain.account.RefreshToken;
import com.main.accord.domain.account.RefreshTokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${accord.jwt.secret}")
    private String jwtSecret;

    private static final long ACCESS_TOKEN_MS  = 60 * 60 * 1000L;         // 1 hour
    private static final long REFRESH_TOKEN_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final RefreshTokenRepository refreshTokenRepository;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // ── Access token ──────────────────────────────────────────────────────────

    public String generateAccessToken(UUID userId, String email, boolean isAdmin) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("admin", isAdmin)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_MS))
                .signWith(key())
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessTokenValid(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Refresh token ─────────────────────────────────────────────────────────

    public RefreshToken generateRefreshToken(UUID userId) {
        String tokenValue = UUID.randomUUID().toString() + UUID.randomUUID();

        RefreshToken refreshToken = RefreshToken.builder()
                .idUser(userId)
                .dsToken(tokenValue)
                .dtExpires(OffsetDateTime.now().plusDays(7))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken validateRefreshToken(String tokenValue) {
        return refreshTokenRepository.findByDsToken(tokenValue)
                .filter(t -> !t.getStRevoked())
                .filter(t -> t.getDtExpires().isAfter(OffsetDateTime.now()))
                .orElse(null);
    }

    public void revokeAllTokens(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }
}