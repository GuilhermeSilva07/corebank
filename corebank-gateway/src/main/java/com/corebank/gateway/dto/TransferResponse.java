package com.corebank.gateway.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
  private UUID transferId;
  private UUID originAccountId;
  private UUID destinationAccountId;
  private BigDecimal amount;
  private BigDecimal originBalanceAfter;
  private Instant createdAt;
}
