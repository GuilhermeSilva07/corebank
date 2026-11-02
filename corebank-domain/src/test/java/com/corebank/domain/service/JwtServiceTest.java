package com.corebank.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corebank.domain.exception.InvalidAccessTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

  private JwtService jwtService;
  private final String secret = "AVerySecureKeyWithSufficientSizeForHS256_1234567890";

  @BeforeEach
  void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "secretKey", secret);
  }

  @Test
  void generateAccessToken_createsValidTokenWithCorrectSubjectAndExpiration() {
    UUID userId = UUID.randomUUID();

    String token = jwtService.generateAccessToken(userId);

    assertThat(token).isNotBlank();

    Claims claims =
        Jwts.parserBuilder()
            .setSigningKey(secret.getBytes())
            .build()
            .parseClaimsJws(token)
            .getBody();

    assertThat(claims.getSubject()).isEqualTo(userId.toString());

    Date expiration = claims.getExpiration();
    Date issuedAt = claims.getIssuedAt();

    long diffInMillis = expiration.getTime() - issuedAt.getTime();
    assertThat(diffInMillis).isEqualTo(900000L);
  }

  @Test
  void parseUserId_validToken_returnsOriginalUserId() {
    UUID userId = UUID.randomUUID();
    String token = jwtService.generateAccessToken(userId);

    UUID parsedUserId = jwtService.parseUserId(token);

    assertThat(parsedUserId).isEqualTo(userId);
  }

  @Test
  void parseUserId_expiredToken_throwsInvalidAccessTokenException() {
    Key key = Keys.hmacShaKeyFor(secret.getBytes());
    String expiredToken =
        Jwts.builder()
            .setSubject(UUID.randomUUID().toString())
            .setIssuedAt(new Date(System.currentTimeMillis() - 1_000_000))
            .setExpiration(new Date(System.currentTimeMillis() - 500_000))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();

    assertThatThrownBy(() -> jwtService.parseUserId(expiredToken))
        .isInstanceOf(InvalidAccessTokenException.class);
  }

  @Test
  void parseUserId_tamperedSignature_throwsInvalidAccessTokenException() {
    Key wrongKey =
        Keys.hmacShaKeyFor("ADifferentSecretKeyUsedToTamperTheSignature_12345".getBytes());
    String tokenSignedWithWrongKey =
        Jwts.builder()
            .setSubject(UUID.randomUUID().toString())
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(wrongKey, SignatureAlgorithm.HS256)
            .compact();

    assertThatThrownBy(() -> jwtService.parseUserId(tokenSignedWithWrongKey))
        .isInstanceOf(InvalidAccessTokenException.class);
  }
}
