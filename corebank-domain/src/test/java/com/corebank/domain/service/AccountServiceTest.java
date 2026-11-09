package com.corebank.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.corebank.domain.command.GetStatementCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Transaction;
import com.corebank.domain.entity.TransactionChannel;
import com.corebank.domain.entity.TransactionStatus;
import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.entity.User;
import com.corebank.domain.exception.AccountAccessDeniedException;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.TransactionRepository;
import com.corebank.domain.result.StatementItemResult;
import com.corebank.domain.result.StatementResult;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

  @Mock private AccountRepository accountRepository;
  @Mock private TransactionRepository transactionRepository;
  @InjectMocks private AccountService accountService;

  private Account ownedAccount(UUID accountId, UUID ownerId) {
    User owner = new User();
    owner.setId(ownerId);

    Account account = new Account();
    account.setId(accountId);
    account.setUser(owner);

    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
    return account;
  }

  private static Transaction ledgerEntry(
      Account account, TransactionType type, String amount, String balanceAfter, Instant at) {
    Transaction transaction =
        new Transaction(
            UUID.randomUUID(),
            account,
            type,
            TransactionChannel.TRANSFER,
            TransactionStatus.COMPLETED,
            new BigDecimal(amount),
            new BigDecimal(balanceAfter),
            null,
            null);
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(transaction, "createdAt", at);
    return transaction;
  }

  @SuppressWarnings("unchecked")
  private void stubEmptyPage() {
    when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
  }

  @SuppressWarnings("unchecked")
  private Specification<Transaction> captureSpecification() {
    ArgumentCaptor<Specification<Transaction>> captor =
        ArgumentCaptor.forClass(Specification.class);
    verify(transactionRepository).findAll(captor.capture(), any(Pageable.class));
    return captor.getValue();
  }

  /** Runs the specification against a mocked CriteriaBuilder so its predicates can be verified. */
  @SuppressWarnings("unchecked")
  private static CriteriaBuilder applyToMockCriteria(Specification<Transaction> specification) {
    Root<Transaction> root = mock(Root.class, RETURNS_DEEP_STUBS);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder cb = mock(CriteriaBuilder.class);
    specification.toPredicate(root, query, cb);
    return cb;
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatement_withValidOwner_mapsLedgerEntriesAndPageTotals() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    Account account = ownedAccount(accountId, requesterId);
    Instant newer = Instant.parse("2026-11-03T12:00:00Z");
    Instant older = Instant.parse("2026-11-01T12:00:00Z");
    Transaction debit = ledgerEntry(account, TransactionType.DEBIT, "40.00", "60.00", newer);
    Transaction credit = ledgerEntry(account, TransactionType.CREDIT, "100.00", "100.00", older);
    when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(debit, credit), PageRequest.of(1, 2), 5));

    StatementResult result =
        accountService.getStatement(new GetStatementCommand(accountId, requesterId, 1, 2));

    assertThat(result.items())
        .containsExactly(
            new StatementItemResult(
                debit.getId(), "DEBIT", new BigDecimal("40.00"), newer, new BigDecimal("60.00")),
            new StatementItemResult(
                credit.getId(),
                "CREDIT",
                new BigDecimal("100.00"),
                older,
                new BigDecimal("100.00")));
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.totalElements()).isEqualTo(5);
    assertThat(result.totalPages()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatement_requestsNewestFirstWithIdTiebreakAndCommandPagination() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    ownedAccount(accountId, requesterId);
    stubEmptyPage();

    accountService.getStatement(new GetStatementCommand(accountId, requesterId, 3, 15));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(transactionRepository).findAll(any(Specification.class), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
    assertThat(pageable.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatement_withoutFilters_restrictsOnlyToAccountAndCompletedEntries() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    ownedAccount(accountId, requesterId);
    stubEmptyPage();

    accountService.getStatement(new GetStatementCommand(accountId, requesterId, 0, 20));

    CriteriaBuilder cb = applyToMockCriteria(captureSpecification());
    verify(cb).equal(any(Expression.class), eq(accountId));
    verify(cb).equal(any(Expression.class), eq(TransactionStatus.COMPLETED));
    verify(cb, never()).equal(any(Expression.class), any(TransactionType.class));
    verify(cb, never()).greaterThanOrEqualTo(any(Expression.class), any(Instant.class));
    verify(cb, never()).lessThan(any(Expression.class), any(Instant.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatement_withPeriodAndType_convertsDatesToSaoPauloDayBoundaries() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    ownedAccount(accountId, requesterId);
    stubEmptyPage();
    LocalDate day = LocalDate.of(2026, 11, 2);

    accountService.getStatement(
        new GetStatementCommand(accountId, requesterId, day, day, TransactionType.DEBIT, 0, 20));

    CriteriaBuilder cb = applyToMockCriteria(captureSpecification());
    verify(cb).equal(any(Expression.class), eq(accountId));
    verify(cb).equal(any(Expression.class), eq(TransactionStatus.COMPLETED));
    verify(cb).equal(any(Expression.class), eq(TransactionType.DEBIT));
    // Sao Paulo is UTC-3: the local day 2026-11-02 spans [03:00Z on the 2nd, 03:00Z on the 3rd).
    verify(cb)
        .greaterThanOrEqualTo(any(Expression.class), eq(Instant.parse("2026-11-02T03:00:00Z")));
    verify(cb).lessThan(any(Expression.class), eq(Instant.parse("2026-11-03T03:00:00Z")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatement_withOnlyFrom_addsNoUpperBound() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    ownedAccount(accountId, requesterId);
    stubEmptyPage();

    accountService.getStatement(
        new GetStatementCommand(
            accountId, requesterId, LocalDate.of(2026, 11, 2), null, null, 0, 20));

    CriteriaBuilder cb = applyToMockCriteria(captureSpecification());
    verify(cb)
        .greaterThanOrEqualTo(any(Expression.class), eq(Instant.parse("2026-11-02T03:00:00Z")));
    verify(cb, never()).lessThan(any(Expression.class), any(Instant.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getStatement_withOnlyTo_addsNoLowerBound() {
    UUID accountId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    ownedAccount(accountId, requesterId);
    stubEmptyPage();

    accountService.getStatement(
        new GetStatementCommand(
            accountId, requesterId, null, LocalDate.of(2026, 11, 2), null, 0, 20));

    CriteriaBuilder cb = applyToMockCriteria(captureSpecification());
    verify(cb).lessThan(any(Expression.class), eq(Instant.parse("2026-11-03T03:00:00Z")));
    verify(cb, never()).greaterThanOrEqualTo(any(Expression.class), any(Instant.class));
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
    verifyNoInteractions(transactionRepository);
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
    verifyNoInteractions(transactionRepository);
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
