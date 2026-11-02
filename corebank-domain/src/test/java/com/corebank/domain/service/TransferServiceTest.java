package com.corebank.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.corebank.domain.command.TransferCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Transaction;
import com.corebank.domain.entity.TransactionChannel;
import com.corebank.domain.entity.TransactionStatus;
import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.entity.User;
import com.corebank.domain.exception.AccountAccessDeniedException;
import com.corebank.domain.exception.DestinationAccountNotFoundException;
import com.corebank.domain.exception.InsufficientBalanceException;
import com.corebank.domain.exception.SelfTransferNotAllowedException;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.TransactionRepository;
import com.corebank.domain.result.TransferResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

  // Fixed ids so the expected lock order is known: LOW < HIGH.
  private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID HIGH = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Mock private AccountRepository accountRepository;
  @Mock private TransactionRepository transactionRepository;
  @InjectMocks private TransferService transferService;

  @Captor private ArgumentCaptor<List<Transaction>> legsCaptor;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  void transfer_success_movesMoneyAndWritesBothLegs() {
    Account origin = account(LOW, ownerId, "100.00");
    Account destination = account(HIGH, UUID.randomUUID(), "10.00");
    stubLock(origin);
    stubLock(destination);

    TransferResult result = transferService.transfer(command(LOW, HIGH, "40.00"));

    assertThat(origin.getBalance()).isEqualByComparingTo("60.00");
    assertThat(destination.getBalance()).isEqualByComparingTo("50.00");

    verify(transactionRepository).saveAll(legsCaptor.capture());
    List<Transaction> legs = legsCaptor.getValue();
    assertThat(legs).hasSize(2);
    Transaction debit = legs.get(0);
    Transaction credit = legs.get(1);

    assertThat(debit.getType()).isEqualTo(TransactionType.DEBIT);
    assertThat(debit.getAccount()).isSameAs(origin);
    assertThat(debit.getBalanceAfter()).isEqualByComparingTo("60.00");
    assertThat(credit.getType()).isEqualTo(TransactionType.CREDIT);
    assertThat(credit.getAccount()).isSameAs(destination);
    assertThat(credit.getBalanceAfter()).isEqualByComparingTo("50.00");
    for (Transaction leg : legs) {
      assertThat(leg.getTransferId()).isEqualTo(result.transferId());
      assertThat(leg.getAmount()).isEqualByComparingTo("40.00");
      assertThat(leg.getChannel()).isEqualTo(TransactionChannel.TRANSFER);
      assertThat(leg.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
      assertThat(leg.getDescription()).isNull();
      assertThat(leg.getIdempotencyKey()).isNull();
    }

    assertThat(result.originAccountId()).isEqualTo(LOW);
    assertThat(result.destinationAccountId()).isEqualTo(HIGH);
    assertThat(result.amount()).isEqualByComparingTo("40.00");
    assertThat(result.originBalanceAfter()).isEqualByComparingTo("60.00");
    assertThat(result.createdAt()).isEqualTo(debit.getCreatedAt());
  }

  @Test
  void transfer_toSameAccount_isRejectedWithoutTouchingRepositories() {
    assertThatThrownBy(() -> transferService.transfer(command(LOW, LOW, "10.00")))
        .isInstanceOf(SelfTransferNotAllowedException.class);

    verifyNoInteractions(accountRepository, transactionRepository);
  }

  @Test
  void transfer_fromAnotherUsersAccount_isAccessDenied() {
    Account origin = account(LOW, UUID.randomUUID(), "100.00");
    Account destination = account(HIGH, UUID.randomUUID(), "10.00");
    stubLock(origin);
    stubLock(destination);

    assertThatThrownBy(() -> transferService.transfer(command(LOW, HIGH, "10.00")))
        .isInstanceOf(AccountAccessDeniedException.class);

    assertThat(origin.getBalance()).isEqualByComparingTo("100.00");
    assertThat(destination.getBalance()).isEqualByComparingTo("10.00");
    verify(transactionRepository, never()).saveAll(any());
  }

  @Test
  void transfer_fromNonExistentAccount_isAccessDenied() {
    Account destination = account(HIGH, UUID.randomUUID(), "10.00");
    when(accountRepository.findByIdForUpdate(LOW)).thenReturn(Optional.empty());
    stubLock(destination);

    assertThatThrownBy(() -> transferService.transfer(command(LOW, HIGH, "10.00")))
        .isInstanceOf(AccountAccessDeniedException.class);

    verify(transactionRepository, never()).saveAll(any());
  }

  @Test
  void transfer_toNonExistentAccount_isNotFoundAndWritesNothing() {
    Account origin = account(LOW, ownerId, "100.00");
    stubLock(origin);
    when(accountRepository.findByIdForUpdate(HIGH)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> transferService.transfer(command(LOW, HIGH, "10.00")))
        .isInstanceOf(DestinationAccountNotFoundException.class);

    assertThat(origin.getBalance()).isEqualByComparingTo("100.00");
    verify(transactionRepository, never()).saveAll(any());
  }

  @Test
  void transfer_aboveBalance_isRejectedAndWritesNothing() {
    Account origin = account(LOW, ownerId, "30.00");
    Account destination = account(HIGH, UUID.randomUUID(), "10.00");
    stubLock(origin);
    stubLock(destination);

    assertThatThrownBy(() -> transferService.transfer(command(LOW, HIGH, "50.00")))
        .isInstanceOf(InsufficientBalanceException.class)
        .hasMessage("Insufficient balance: missing 20.00");

    assertThat(origin.getBalance()).isEqualByComparingTo("30.00");
    assertThat(destination.getBalance()).isEqualByComparingTo("10.00");
    verify(transactionRepository, never()).saveAll(any());
  }

  @Test
  void transfer_locksAccountsInAscendingIdOrder_fromLowToHigh() {
    stubLock(account(LOW, ownerId, "100.00"));
    stubLock(account(HIGH, UUID.randomUUID(), "0.00"));

    transferService.transfer(command(LOW, HIGH, "10.00"));

    InOrder order = inOrder(accountRepository);
    order.verify(accountRepository).findByIdForUpdate(LOW);
    order.verify(accountRepository).findByIdForUpdate(HIGH);
  }

  @Test
  void transfer_locksAccountsInAscendingIdOrder_fromHighToLow() {
    stubLock(account(HIGH, ownerId, "100.00"));
    stubLock(account(LOW, UUID.randomUUID(), "0.00"));

    transferService.transfer(command(HIGH, LOW, "10.00"));

    InOrder order = inOrder(accountRepository);
    order.verify(accountRepository).findByIdForUpdate(LOW);
    order.verify(accountRepository).findByIdForUpdate(HIGH);
  }

  private TransferCommand command(UUID origin, UUID destination, String amount) {
    return new TransferCommand(ownerId, origin, destination, new BigDecimal(amount));
  }

  private void stubLock(Account account) {
    when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
  }

  private Account account(UUID id, UUID userId, String balance) {
    User user = new User();
    user.setId(userId);
    Account account = new Account();
    account.setId(id);
    account.setUser(user);
    account.setBalance(new BigDecimal(balance));
    return account;
  }
}
