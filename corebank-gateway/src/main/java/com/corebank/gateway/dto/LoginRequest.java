package com.corebank.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

  @NotBlank(message = "O email é obrigatório")
  @Email(message = "Formato de email inválido")
  private String email;

  @NotBlank(message = "A senha é obrigatória")
  private String password;
}

/*
Versão de DTO como record comentada, para discussão sobre qual modelo usar

 package com.corebank.gateway.dto;

 import jakarta.validation.constraints.Email;
 import jakarta.validation.constraints.NotBlank;

 public record LoginRequest(
     @NotBlank(message = "O email é obrigatório")
     @Email(message = "Formato de email inválido")
     String email,

     @NotBlank(message = "A senha é obrigatória")
     String password
 ) {}
*/
