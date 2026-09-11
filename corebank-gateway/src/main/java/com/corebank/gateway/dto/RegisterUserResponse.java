package com.corebank.gateway.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserResponse {

  private UUID id;
  private String fullName;
  private String email;
  private String accountNumber;
}
