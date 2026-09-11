package com.corebank.gateway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserRequest {

  @NotBlank private String fullName;

  @NotBlank private String cpf;

  @NotBlank @Email private String email;

  @NotBlank
  @Size(min = 8)
  private String password;

  private String phone;

  @NotNull private LocalDate birthDate;

  @NotNull @Valid private AddressRequest address;
}
