package com.corebank.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.corebank.domain.command.GetStatementCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.User;
import com.corebank.domain.exception.AccountAccessDeniedException;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.result.StatementResult;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

  @Mock private AccountRepository accountRepository;
  @InjectMocks private AccountService accountService;

  @Test
  void getStatement_withValidOwner_returnsStatementStub() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    GetStatementCommand command = new GetStatementCommand(accountId, requesterId, 0, 20);

    User owner = new User();
    owner.setId(requesterId);

    Account account = new Account();
    account.setId(accountId);
    account.setUser(owner);

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    StatementResult result = accountService.getStatement(command);

    assertThat(result).isNotNull();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void getStatement_withDifferentUser_throwsAccountAccessDeniedException() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID anotherUserId = UUID.randomUUID();
    GetStatementCommand command = new GetStatementCommand(accountId, requesterId, 0, 20);

    User anotherUser = new User();
    anotherUser.setId(anotherUserId);

    Account account = new Account();
    account.setId(accountId);
    account.setUser(anotherUser);

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> accountService.getStatement(command))
        .isInstanceOf(AccountAccessDeniedException.class)
        .hasMessage("Access denied. The account does not belong to the authenticated user.");
  }

  @Test
  void getStatement_withNonExistentAccount_throwsAccountAccessDeniedException() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    GetStatementCommand command = new GetStatementCommand(accountId, requesterId, 0, 20);

    when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> accountService.getStatement(command))
        .isInstanceOf(AccountAccessDeniedException.class)
        .hasMessage("Access denied. The account does not belong to the authenticated user.");
  }

  @Test
  void getBalance_withValidOwner_returnsBalance() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    BigDecimal expectedBalance = new BigDecimal("123.45");

    User owner = new User();
    owner.setId(requesterId);

    Account account = new Account();
    account.setId(accountId);
    account.setUser(owner);
    account.setBalance(expectedBalance);

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    BigDecimal actualBalance = accountService.getBalance(accountId, requesterId);

    assertThat(actualBalance).isEqualTo(expectedBalance);
  }

  @Test
  void getBalance_withDifferentUser_throwsAccountAccessDeniedException() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID anotherUserId = UUID.randomUUID();

    User anotherUser = new User();
    anotherUser.setId(anotherUserId);

    Account account = new Account();
    account.setId(accountId);
    account.setUser(anotherUser);

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> accountService.getBalance(accountId, requesterId))
        .isInstanceOf(AccountAccessDeniedException.class)
        .hasMessageContaining(
            "Access denied. The account does not belong to the authenticated user.");
  }

  @Test
  void getBalance_withNonExistentAccount_throwsAccountAccessDeniedException() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> accountService.getBalance(accountId, requesterId))
        .isInstanceOf(AccountAccessDeniedException.class)
        .hasMessageContaining(
            "Access denied. The account does not belong to the authenticated user.");
  }
}
