package com.corebank.domain.entity;

import com.corebank.domain.exception.InsufficientBalanceException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Account {

  @Id @GeneratedValue private UUID id;

  @Column(name = "account_number", nullable = false, unique = true)
  private String accountNumber;

  @Column(name = "agency_number", nullable = false)
  private String agencyNumber;

  @Column(name = "balance", nullable = false)
  private BigDecimal balance;

  @ManyToOne
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /**
   * Removes {@code amount} from the balance. Leaves the balance untouched and throws when it does
   * not cover the amount. Callers that move money must hold a row lock on this account.
   */
  public void debit(BigDecimal amount) {
    requirePositive(amount);
    if (balance.compareTo(amount) < 0) {
      throw new InsufficientBalanceException(amount.subtract(balance));
    }
    balance = balance.subtract(amount);
  }

  /** Adds {@code amount} to the balance. */
  public void credit(BigDecimal amount) {
    requirePositive(amount);
    balance = balance.add(amount);
  }

  private static void requirePositive(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Amount must be greater than zero");
    }
  }
}
