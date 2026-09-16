package com.corebank.gateway.service;

import com.corebank.domain.entity.User;
import com.corebank.domain.repository.UserRepository;
import com.corebank.gateway.dto.LoginRequest;
import com.corebank.gateway.dto.LoginResponse;
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

  public LoginResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos."));

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BadCredentialsException("Email ou senha inválidos.");
    }

    String accessToken = jwtService.generateAccessToken(user.getId());

    return new LoginResponse(accessToken, "placeholder-refresh-token");
  }
}
