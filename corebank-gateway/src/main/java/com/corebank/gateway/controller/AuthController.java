package com.corebank.gateway.controller;

import com.corebank.domain.command.RegisterUserCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.service.UserRegistrationService;
import com.corebank.gateway.dto.RegisterUserRequest;
import com.corebank.gateway.dto.RegisterUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

  private final UserRegistrationService userRegistrationService;

  @PostMapping("/users")
  public ResponseEntity<RegisterUserResponse> register(
      @RequestBody @Valid RegisterUserRequest request) {
    RegisterUserCommand command =
        new RegisterUserCommand(
            request.getFullName(),
            request.getCpf(),
            request.getEmail(),
            request.getPhone(),
            request.getPassword(),
            request.getBirthDate(),
            request.getAddress().getStreet(),
            request.getAddress().getNumber(),
            request.getAddress().getComplement(),
            request.getAddress().getNeighborhood(),
            request.getAddress().getCity(),
            request.getAddress().getState(),
            request.getAddress().getZipCode());

    Account account = userRegistrationService.register(command);

    RegisterUserResponse response = new RegisterUserResponse();
    response.setId(account.getUser().getId());
    response.setFullName(account.getUser().getFullName());
    response.setEmail(account.getUser().getEmail());
    response.setAccountNumber(account.getAccountNumber());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
