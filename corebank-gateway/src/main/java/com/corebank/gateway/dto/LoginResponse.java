package com.corebank.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;

}

/*
Versão de DTO como record comentada, para discussão sobre qual modelo usar

 package com.corebank.gateway.dto;

 public record LoginResponse(
     String accessToken,
     String refreshToken
 ) {}
*/