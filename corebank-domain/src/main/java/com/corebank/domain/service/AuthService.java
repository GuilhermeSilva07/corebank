package com.corebank.domain.service;

import com.corebank.domain.command.LoginCommand;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.result.LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;

  public LoginResult login(LoginCommand command) {
    User user =
        userRepository
            .findByEmail(command.email())
            .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos."));

    if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Email ou senha inválidos.");
    }

    return new LoginResult(
        jwtService.generateAccessToken(user.getId()), refreshTokenService.generateAndStore(user));
  }
}
