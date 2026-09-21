package com.corebank.domain.service;

import com.corebank.domain.entity.RefreshToken;
import com.corebank.domain.entity.User;
import com.corebank.domain.exception.InvalidRefreshTokenException;
import com.corebank.domain.repository.RefreshTokenRepository;
import com.corebank.domain.result.RefreshResult;
import com.corebank.domain.util.TokenUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final long EXPIRATION_DAYS = 7;

  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtService jwtService;

  @Transactional
  public String generateAndStore(User user) {
    String rawToken = TokenUtils.generateRandomToken();
    String tokenHash = TokenUtils.hash(rawToken);

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setTokenHash(tokenHash);
    refreshToken.setUser(user);
    refreshToken.setExpiresAt(Instant.now().plus(EXPIRATION_DAYS, ChronoUnit.DAYS));
    refreshToken.setCreatedAt(Instant.now());

    refreshTokenRepository.save(refreshToken);

    return rawToken;
  }

  @Transactional
  public RefreshResult rotate(String rawToken) {
    String tokenHash = TokenUtils.hash(rawToken);

    RefreshToken refreshToken =
        refreshTokenRepository
            .findByTokenHashAndRevokedAtIsNull(tokenHash)
            .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token inválido."));

    if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
      throw new InvalidRefreshTokenException("Refresh token expirado.");
    }

    refreshToken.setRevokedAt(Instant.now());
    revokeWithOptimisticLock(refreshToken);

    String newRefreshToken = generateAndStore(refreshToken.getUser());
    String newAccessToken = jwtService.generateAccessToken(refreshToken.getUser().getId());

    return new RefreshResult(newAccessToken, newRefreshToken);
  }

  private void revokeWithOptimisticLock(RefreshToken refreshToken) {
    try {
      refreshTokenRepository.saveAndFlush(refreshToken);
    } catch (OptimisticLockingFailureException e) {
      throw new InvalidRefreshTokenException("Refresh token já foi utilizado.");
    }
  }
}
