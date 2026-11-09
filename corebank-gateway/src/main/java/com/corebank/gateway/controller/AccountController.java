package com.corebank.gateway.controller;

import com.corebank.domain.command.GetStatementCommand;
import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.result.StatementResult;
import com.corebank.domain.service.AccountService;
import com.corebank.gateway.dto.BalanceResponse;
import com.corebank.gateway.dto.StatementItemResponse;
import com.corebank.gateway.dto.StatementResponse;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @GetMapping("/{id}/statement")
  public ResponseEntity<StatementResponse> getStatement(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {

    UUID requesterId = UUID.fromString(principal.getName());
    GetStatementCommand command = new GetStatementCommand(id, requesterId, page, size);

    return ResponseEntity.ok(toResponse(accountService.getStatement(command)));
  }

  @GetMapping("/{id}/transactions")
  public ResponseEntity<StatementResponse> getTransactions(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
      @RequestParam(required = false) TransactionType type,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {

    UUID requesterId = UUID.fromString(principal.getName());
    GetStatementCommand command =
        new GetStatementCommand(id, requesterId, from, to, type, page, size);

    return ResponseEntity.ok(toResponse(accountService.getStatement(command)));
  }

  @GetMapping("/{id}/balance")
  public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id, Principal principal) {
    UUID requesterId = UUID.fromString(principal.getName());
    BigDecimal balance = accountService.getBalance(id, requesterId);

    return ResponseEntity.ok(new BalanceResponse(balance));
  }

  private static StatementResponse toResponse(StatementResult result) {
    return new StatementResponse(
        result.items().stream()
            .map(
                i ->
                    new StatementItemResponse(
                        i.id(), i.type(), i.amount(), i.timestamp(), i.runningBalance()))
            .toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }
}
