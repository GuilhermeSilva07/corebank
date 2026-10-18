package com.corebank.domain.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementItemResult(
    UUID id, String type, BigDecimal amount, Instant timestamp, BigDecimal runningBalance) {}
