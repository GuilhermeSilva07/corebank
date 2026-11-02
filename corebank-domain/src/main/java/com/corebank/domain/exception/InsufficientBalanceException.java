package com.corebank.domain.exception;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class InsufficientBalanceException extends RuntimeException {

  private final BigDecimal shortfall;

  public InsufficientBalanceException(BigDecimal shortfall) {
    super("Insufficient balance: missing " + shortfall.toPlainString());
    this.shortfall = shortfall;
  }
}
