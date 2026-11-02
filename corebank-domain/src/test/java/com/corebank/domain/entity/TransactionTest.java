package com.corebank.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionTest {

  private final UUID transferId = UUID.randomUUID();
  private final Account account = new Account();

  private Transaction newTransaction(TransactionStatus status) {
    return new Transaction(
        transferId,
        account,
        TransactionType.DEBIT,
        TransactionChannel.TRANSFER,
        status,
        new BigDecimal("50.00"),
        new BigDecimal("150.00"),
        "rent",
        "key-1");
  }

  @Test
  void constructor_setsAllFieldsAndCreatedAt() {
    Instant before = Instant.now();

    Transaction tx = newTransaction(TransactionStatus.COMPLETED);

    assertThat(tx.getTransferId()).isEqualTo(transferId);
    assertThat(tx.getAccount()).isSameAs(account);
    assertThat(tx.getType()).isEqualTo(TransactionType.DEBIT);
    assertThat(tx.getChannel()).isEqualTo(TransactionChannel.TRANSFER);
    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(tx.getAmount()).isEqualByComparingTo("50.00");
    assertThat(tx.getBalanceAfter()).isEqualByComparingTo("150.00");
    assertThat(tx.getDescription()).isEqualTo("rent");
    assertThat(tx.getIdempotencyKey()).isEqualTo("key-1");
    assertThat(tx.getCreatedAt()).isBetween(before, Instant.now());
  }

  @Test
  void markCompleted_fromPending_changesOnlyStatus() {
    Transaction tx = newTransaction(TransactionStatus.PENDING);
    Instant createdAt = tx.getCreatedAt();

    tx.markCompleted();

    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(tx.getCreatedAt()).isEqualTo(createdAt);
    assertThat(tx.getAmount()).isEqualByComparingTo("50.00");
    assertThat(tx.getBalanceAfter()).isEqualByComparingTo("150.00");
    assertThat(tx.getTransferId()).isEqualTo(transferId);
  }

  @Test
  void markFailed_fromPending_changesOnlyStatus() {
    Transaction tx = newTransaction(TransactionStatus.PENDING);
    Instant createdAt = tx.getCreatedAt();

    tx.markFailed();

    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
    assertThat(tx.getCreatedAt()).isEqualTo(createdAt);
    assertThat(tx.getAmount()).isEqualByComparingTo("50.00");
  }

  @Test
  void markFailed_onCompletedTransaction_isRejected() {
    Transaction tx = newTransaction(TransactionStatus.COMPLETED);

    assertThatThrownBy(tx::markFailed).isInstanceOf(IllegalStateException.class);
    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
  }

  @Test
  void markCompleted_onFailedTransaction_isRejected() {
    Transaction tx = newTransaction(TransactionStatus.FAILED);

    assertThatThrownBy(tx::markCompleted).isInstanceOf(IllegalStateException.class);
    assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
  }

  @Test
  void entity_exposesNoPublicSetters() {
    boolean hasPublicSetter =
        Arrays.stream(Transaction.class.getDeclaredMethods())
            .filter(m -> Modifier.isPublic(m.getModifiers()))
            .map(Method::getName)
            .anyMatch(name -> name.startsWith("set"));

    assertThat(hasPublicSetter).isFalse();
  }
}
