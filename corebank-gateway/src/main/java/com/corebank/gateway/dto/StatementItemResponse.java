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
public class StatementItemResponse {
  private UUID id;
  private String type;
  private BigDecimal amount;
  private Instant timestamp;
  private BigDecimal runningBalance;
}
