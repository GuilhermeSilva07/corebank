package com.corebank.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One leg of a money movement on an account. Append-only: every field is fixed at creation except
 * {@code status}. Legs of the same operation share {@code transferId}.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

  @Id @GeneratedValue private UUID id;

  @Column(name = "transfer_id", nullable = false, updatable = false)
  private UUID transferId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false, updatable = false)
  private Account account;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private TransactionType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, updatable = false)
  private TransactionChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TransactionStatus status;

  @Column(name = "amount", nullable = false, updatable = false)
  private BigDecimal amount;

  @Column(name = "balance_after", nullable = false, updatable = false)
  private BigDecimal balanceAfter;

  @Column(name = "description", updatable = false)
  private String description;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Transaction(
      UUID transferId,
      Account account,
      TransactionType type,
      TransactionChannel channel,
      TransactionStatus status,
      BigDecimal amount,
      BigDecimal balanceAfter,
      String description,
      String idempotencyKey) {
    this.transferId = transferId;
    this.account = account;
    this.type = type;
    this.channel = channel;
    this.status = status;
    this.amount = amount;
    this.balanceAfter = balanceAfter;
    this.description = description;
    this.idempotencyKey = idempotencyKey;
    this.createdAt = Instant.now();
  }

  public void markCompleted() {
    transitionFromPending(TransactionStatus.COMPLETED);
  }

  public void markFailed() {
    transitionFromPending(TransactionStatus.FAILED);
  }

  private void transitionFromPending(TransactionStatus target) {
    if (status != TransactionStatus.PENDING) {
      throw new IllegalStateException(
          "Only a PENDING transaction can become " + target + ", current status is " + status);
    }
    this.status = target;
  }
}
