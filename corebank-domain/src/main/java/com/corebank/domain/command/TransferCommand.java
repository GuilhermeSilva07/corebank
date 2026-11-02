package com.corebank.domain.command;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCommand(
    UUID requesterId, UUID originAccountId, UUID destinationAccountId, BigDecimal amount) {

  public TransferCommand {
    if (requesterId == null || originAccountId == null || destinationAccountId == null) {
      throw new IllegalArgumentException("Requester, origin and destination are required");
    }
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Amount must be greater than zero");
    }
    if (amount.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException("Amount must have at most two decimal places");
    }
  }
}
