package com.corebank.domain.service; // Pacote atualizado

import com.corebank.domain.command.LoginCommand;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.UserRepository;
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

  public String login(LoginCommand command) {
    User user =
        userRepository
            .findByEmail(command.email())
            .orElseThrow(() -> new BadCredentialsException("Email ou senha inválidos."));

    if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Email ou senha inválidos.");
    }

    return jwtService.generateAccessToken(user.getId());
  }
}
