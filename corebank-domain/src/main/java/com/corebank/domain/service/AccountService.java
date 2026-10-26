package com.corebank.domain.service;

import com.corebank.domain.command.GetStatementCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.exception.AccountAccessDeniedException;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.result.StatementResult;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final AccountRepository accountRepository;

  public StatementResult getStatement(GetStatementCommand command) {
    findOwnedAccount(command.accountId(), command.requesterId());

    return new StatementResult(List.of(), command.page(), command.size(), 0L, 0);
  }

  public Account findOwnedAccount(UUID accountId, UUID requesterId) {
    Account account = accountRepository.findById(accountId).orElse(null);

    if (account == null || !account.getUser().getId().equals(requesterId)) {
      throw new AccountAccessDeniedException(
          "Access denied. The account does not belong to the authenticated user.");
    }

    return account;
  }
}
