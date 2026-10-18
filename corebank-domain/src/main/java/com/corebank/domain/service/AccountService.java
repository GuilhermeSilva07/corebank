package com.corebank.domain.service;

import com.corebank.domain.command.GetStatementCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.result.StatementResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;

  public StatementResult getStatement(GetStatementCommand command) {
    Account account =
        accountRepository
            .findById(command.accountId())
            .orElseThrow(() -> new IllegalArgumentException("Account not found."));

    if (!account.getUser().getId().equals(command.requesterId())) {
      throw new SecurityException(
          "Access denied. The account does not belong to the authenticated user.");
    }

    return new StatementResult(List.of(), command.page(), command.size(), 0L, 0);
  }
}
