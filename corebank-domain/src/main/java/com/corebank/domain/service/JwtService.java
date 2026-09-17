package com.corebank.domain.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  @Value("${jwt.secret}")
  private String secretKey;

  private static final long EXPIRATION_TIME_MS = 900000;

  public String generateAccessToken(UUID userId) {
    Key key = Keys.hmacShaKeyFor(secretKey.getBytes());

    return Jwts.builder()
        .setSubject(userId.toString())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }
}
