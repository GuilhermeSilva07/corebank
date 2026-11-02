package com.corebank.domain.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferCommandTest {

  private final UUID requester = UUID.randomUUID();
  private final UUID origin = UUID.randomUUID();
  private final UUID destination = UUID.randomUUID();

  @Test
  void validCommand_isCreated() {
    TransferCommand command =
        new TransferCommand(requester, origin, destination, new BigDecimal("10.50"));

    assertThat(command.amount()).isEqualByComparingTo("10.50");
  }

  @Test
  void trailingZerosBeyondTwoDecimals_areAccepted() {
    TransferCommand command =
        new TransferCommand(requester, origin, destination, new BigDecimal("10.500"));

    assertThat(command.amount()).isEqualByComparingTo("10.50");
  }

  @Test
  void nullIds_areRejected() {
    BigDecimal amount = BigDecimal.TEN;
    assertThatThrownBy(() -> new TransferCommand(null, origin, destination, amount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TransferCommand(requester, null, destination, amount))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TransferCommand(requester, origin, null, amount))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullZeroOrNegativeAmount_isRejected() {
    assertThatThrownBy(() -> new TransferCommand(requester, origin, destination, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TransferCommand(requester, origin, destination, BigDecimal.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new TransferCommand(requester, origin, destination, new BigDecimal("-5.00")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void moreThanTwoDecimalPlaces_isRejected() {
    assertThatThrownBy(
            () -> new TransferCommand(requester, origin, destination, new BigDecimal("10.005")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
