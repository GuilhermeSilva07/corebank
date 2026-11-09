package com.corebank.domain.service;

import static com.corebank.domain.repository.TransactionSpecifications.completed;
import static com.corebank.domain.repository.TransactionSpecifications.createdAtBefore;
import static com.corebank.domain.repository.TransactionSpecifications.createdAtFrom;
import static com.corebank.domain.repository.TransactionSpecifications.ofAccount;
import static com.corebank.domain.repository.TransactionSpecifications.ofType;

import com.corebank.domain.command.GetStatementCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Transaction;
import com.corebank.domain.exception.AccountAccessDeniedException;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.TransactionRepository;
import com.corebank.domain.result.StatementItemResult;
import com.corebank.domain.result.StatementResult;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

  /** Time zone that defines the calendar days of the {@code from}/{@code to} filters. */
  public static final ZoneId STATEMENT_ZONE = ZoneId.of("America/Sao_Paulo");

  /** Newest first; the id tiebreak keeps the order total so pages never repeat or skip rows. */
  static final Sort STATEMENT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;

  @Transactional(readOnly = true)
  public StatementResult getStatement(GetStatementCommand command) {
    findOwnedAccount(command.accountId(), command.requesterId());

    Pageable pageable = PageRequest.of(command.page(), command.size(), STATEMENT_SORT);
    Page<Transaction> page = transactionRepository.findAll(buildFilter(command), pageable);

    return new StatementResult(
        page.getContent().stream().map(AccountService::toItem).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private static Specification<Transaction> buildFilter(GetStatementCommand command) {
    Specification<Transaction> filter = ofAccount(command.accountId()).and(completed());

    if (command.from() != null) {
      filter = filter.and(createdAtFrom(command.from().atStartOfDay(STATEMENT_ZONE).toInstant()));
    }
    if (command.to() != null) {
      // "to" is inclusive: everything before the start of the following day.
      filter =
          filter.and(
              createdAtBefore(command.to().plusDays(1).atStartOfDay(STATEMENT_ZONE).toInstant()));
    }
    if (command.type() != null) {
      filter = filter.and(ofType(command.type()));
    }
    return filter;
  }

  private static StatementItemResult toItem(Transaction transaction) {
    return new StatementItemResult(
        transaction.getId(),
        transaction.getType().name(),
        transaction.getAmount(),
        transaction.getCreatedAt(),
        transaction.getBalanceAfter());
  }

  public Account findOwnedAccount(UUID accountId, UUID requesterId) {
    Account account = accountRepository.findById(accountId).orElse(null);

    if (account == null || !account.getUser().getId().equals(requesterId)) {
      throw new AccountAccessDeniedException(
          "Access denied. The account does not belong to the authenticated user.");
    }

    return account;
  }

  public BigDecimal getBalance(UUID accountId, UUID requesterId) {
    Account account = findOwnedAccount(accountId, requesterId);
    return account.getBalance();
  }
}
