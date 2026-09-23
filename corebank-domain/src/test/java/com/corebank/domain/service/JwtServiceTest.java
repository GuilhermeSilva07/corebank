package com.corebank.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
}
