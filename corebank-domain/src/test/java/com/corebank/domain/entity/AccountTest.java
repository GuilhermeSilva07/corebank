package com.corebank.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corebank.domain.exception.InsufficientBalanceException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

  @Test
  void debit_withSufficientBalance_subtractsAmount() {
    Account account = accountWithBalance("100.00");

    account.debit(new BigDecimal("40.00"));

    assertThat(account.getBalance()).isEqualByComparingTo("60.00");
  }

  @Test
  void debit_ofEntireBalance_leavesZero() {
    Account account = accountWithBalance("50.00");

    account.debit(new BigDecimal("50.00"));

    assertThat(account.getBalance()).isEqualByComparingTo("0.00");
  }

  @Test
  void debit_aboveBalance_throwsWithShortfallAndKeepsBalance() {
    Account account = accountWithBalance("30.00");

    assertThatThrownBy(() -> account.debit(new BigDecimal("50.00")))
        .isInstanceOf(InsufficientBalanceException.class)
        .hasMessage("Insufficient balance: missing 20.00")
        .satisfies(
            e ->
                assertThat(((InsufficientBalanceException) e).getShortfall())
                    .isEqualByComparingTo("20.00"));

    assertThat(account.getBalance()).isEqualByComparingTo("30.00");
  }

  @Test
  void credit_addsAmount() {
    Account account = accountWithBalance("10.00");

    account.credit(new BigDecimal("40.00"));

    assertThat(account.getBalance()).isEqualByComparingTo("50.00");
  }

  @Test
  void debitAndCredit_withZeroOrNegativeAmount_throwIllegalArgument() {
    Account account = accountWithBalance("100.00");

    assertThatThrownBy(() -> account.debit(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.debit(new BigDecimal("-1.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.credit(BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> account.credit(new BigDecimal("-1.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(account.getBalance()).isEqualByComparingTo("100.00");
  }

  private Account accountWithBalance(String balance) {
    Account account = new Account();
    account.setBalance(new BigDecimal(balance));
    return account;
  }
}
