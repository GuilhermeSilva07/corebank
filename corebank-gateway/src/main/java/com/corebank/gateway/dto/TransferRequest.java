package com.corebank.gateway.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

  @NotNull(message = "The origin account is required")
  private UUID originAccountId;

  @NotNull(message = "The destination account is required")
  private UUID destinationAccountId;

  @NotNull(message = "The amount is required")
  @Positive(message = "The amount must be greater than zero")
  @Digits(integer = 13, fraction = 2, message = "The amount must have at most two decimal places")
  private BigDecimal amount;
}
