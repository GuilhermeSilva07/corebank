package com.corebank.domain.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResult(
    UUID transferId,
    UUID originAccountId,
    UUID destinationAccountId,
    BigDecimal amount,
    BigDecimal originBalanceAfter,
    Instant createdAt) {}
