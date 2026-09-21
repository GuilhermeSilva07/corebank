package com.corebank.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.corebank.domain.entity.RefreshToken;
import com.corebank.domain.entity.User;
import com.corebank.domain.exception.InvalidRefreshTokenException;
import com.corebank.domain.repository.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;

  @Mock private JwtService jwtService;

  @InjectMocks private RefreshTokenService refreshTokenService;

  @Test
  void generateAndStore_savesHashedTokenAndReturnsRawToken() {
    User user = new User();
    user.setId(UUID.randomUUID());

    String rawToken = refreshTokenService.generateAndStore(user);

    assertThat(rawToken).isNotBlank();

    verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
  }

  @Test
  void rotate_validToken_returnsNewTokensAndRevokesOldOne() {
    User user = new User();
    user.setId(UUID.randomUUID());

    RefreshToken storedToken = new RefreshToken();
    storedToken.setUser(user);
    storedToken.setTokenHash("any-hash");
    storedToken.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

    when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(any()))
        .thenReturn(Optional.of(storedToken));
    when(jwtService.generateAccessToken(user.getId())).thenReturn("new-access-token");

    var result = refreshTokenService.rotate("raw-refresh-token");

    assertThat(result.accessToken()).isEqualTo("new-access-token");
    assertThat(result.refreshToken()).isNotBlank();
    assertThat(storedToken.getRevokedAt()).isNotNull();

    verify(refreshTokenRepository, times(1)).save(storedToken);
    verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
  }

  @Test
  void rotate_tokenNotFoundOrAlreadyRevoked_throwsInvalidRefreshTokenException() {
    when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
  }

  @Test
  void rotate_expiredToken_throwsInvalidRefreshTokenException() {
    RefreshToken expiredToken = new RefreshToken();
    expiredToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

    when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(any()))
        .thenReturn(Optional.of(expiredToken));

    assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
        .isInstanceOf(InvalidRefreshTokenException.class);

    verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
  }
}
