package com.mashangan.blog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMinutes;
    private final long vipExpireDays;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expire-minutes}") long expireMinutes,
                      @Value("${app.jwt.vip-expire-days:365}") long vipExpireDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
        this.vipExpireDays = vipExpireDays;
    }

    public String generate(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /**
     * VIP 会员令牌：role=vip（与 admin 隔离），sub=vip_members.id，长效默认 365 天。
     * 冻结不靠令牌——每次正文解锁 /api/vip/me 都实时复查 vip_members.enabled。
     */
    public String generateVip(Long memberId, String nickname) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("username", nickname)
                .claim("role", "vip")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(vipExpireDays * 86400)))
                .signWith(key)
                .compact();
    }

    /** 解析成功返回 Claims，失败返回 null（不抛异常，由过滤器跳过） */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long userId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String username(Claims claims) {
        return claims.get("username", String.class);
    }

    public String role(Claims claims) {
        return claims.get("role", String.class);
    }
}
