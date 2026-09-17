package com.corebank.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

  @NotBlank(message = "The email is required")
  @Email(message = "Invalid email format")
  private String email;

  @NotBlank(message = "The password is required")
  private String password;
}
