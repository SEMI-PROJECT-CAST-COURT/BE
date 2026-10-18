package com.verdict.castcourt.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secretKey,
        @Value("${jwt.expiration}") long accessTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secretKey));
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String createToken(CustomUserDetails user) {
        Date now = new Date();

        return Jwts.builder()
                .header().type("JWT").and()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("authorities", user.getAuthorities())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public CustomUserDetails parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new CustomUserDetails(
                Integer.parseInt(claims.getSubject()),
                claims.get("username", String.class),
                null,
                claims.get("authorities", String.class)
        );
    }
}
