package com.corebank.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.corebank.domain.command.LoginCommand;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.result.LoginResult;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private RefreshTokenService refreshTokenService;

  @InjectMocks private AuthService authService;

  @Test
  void login_withValidCredentials_returnsLoginResult() {
    LoginCommand command = new LoginCommand("bob@corebank.com", "strongPassword123");

    User mockUser = new User();
    mockUser.setId(UUID.randomUUID());
    mockUser.setPasswordHash("hashed_password");

    when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(mockUser));
    when(passwordEncoder.matches(command.password(), mockUser.getPasswordHash())).thenReturn(true);
    when(jwtService.generateAccessToken(mockUser.getId())).thenReturn("mock-access-token");
    when(refreshTokenService.generateAndStore(mockUser)).thenReturn("mock-refresh-token");

    LoginResult result = authService.login(command);

    assertThat(result).isNotNull();
    assertThat(result.accessToken()).isEqualTo("mock-access-token");
    assertThat(result.refreshToken()).isEqualTo("mock-refresh-token");
  }

  @Test
  void login_withNonExistentEmail_throwsBadCredentialsException() {
    LoginCommand command = new LoginCommand("non-existent@corebank.com", "strongPassword123");

    when(userRepository.findByEmail(command.email())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid email or password.");
  }

  @Test
  void login_withWrongPassword_throwsBadCredentialsException() {
    LoginCommand command = new LoginCommand("bob@corebank.com", "wrongPassword");

    User mockUser = new User();
    mockUser.setPasswordHash("hashed_password");

    when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(mockUser));
    when(passwordEncoder.matches(command.password(), mockUser.getPasswordHash())).thenReturn(false);

    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid email or password.");
  }
}
