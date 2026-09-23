package com.corebank.domain.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BCryptPasswordEncoderTest {

  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
  }

  @Test
  void matches_withCorrectPassword_returnsTrue() {
    String rawPassword = "StrongTestPassword123";
    String encodedPassword = passwordEncoder.encode(rawPassword);

    assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
  }

  @Test
  void matches_withWrongPassword_returnsFalse() {
    String rawPassword = "StrongTestPassword123";
    String encodedPassword = passwordEncoder.encode(rawPassword);

    assertThat(passwordEncoder.matches("WrongPassword456", encodedPassword)).isFalse();
  }

  @Test
  void encode_withSamePasswordTwice_returnsDifferentHashesDueToSalting() {
    String rawPassword = "StrongTestPassword123";

    String firstHash = passwordEncoder.encode(rawPassword);
    String secondHash = passwordEncoder.encode(rawPassword);

    assertThat(firstHash).isNotEqualTo(secondHash);
    assertThat(passwordEncoder.matches(rawPassword, firstHash)).isTrue();
    assertThat(passwordEncoder.matches(rawPassword, secondHash)).isTrue();
  }
}
