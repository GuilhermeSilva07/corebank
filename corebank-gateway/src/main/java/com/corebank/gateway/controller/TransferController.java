package com.corebank.gateway.controller;

import com.corebank.domain.command.TransferCommand;
import com.corebank.domain.result.TransferResult;
import com.corebank.domain.service.TransferService;
import com.corebank.gateway.dto.TransferRequest;
import com.corebank.gateway.dto.TransferResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

  private final TransferService transferService;

  @PostMapping
  public ResponseEntity<TransferResponse> transfer(
      @Valid @RequestBody TransferRequest request, Principal principal) {
    UUID requesterId = UUID.fromString(principal.getName());
    TransferCommand command =
        new TransferCommand(
            requesterId,
            request.getOriginAccountId(),
            request.getDestinationAccountId(),
            request.getAmount());

    TransferResult result = transferService.transfer(command);

    TransferResponse response =
        new TransferResponse(
            result.transferId(),
            result.originAccountId(),
            result.destinationAccountId(),
            result.amount(),
            result.originBalanceAfter(),
            result.createdAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
