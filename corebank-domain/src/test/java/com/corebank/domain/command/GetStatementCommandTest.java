package com.corebank.domain.command;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetStatementCommandTest {

  private final UUID accountId = UUID.randomUUID();
  private final UUID requesterId = UUID.randomUUID();

  @Test
  void create_withValidPagination_succeeds() {
    assertThatCode(() -> new GetStatementCommand(accountId, requesterId, 0, 50))
        .doesNotThrowAnyException();
  }

  @Test
  void create_withNegativePage_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new GetStatementCommand(accountId, requesterId, -1, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Page cannot be less than 0");
  }

  @Test
  void create_withSizeLessThanOne_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new GetStatementCommand(accountId, requesterId, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Page size must be between 1 and 100");
  }

  @Test
  void create_withSizeGreaterThanOneHundred_throwsIllegalArgumentException() {
    assertThatThrownBy(() -> new GetStatementCommand(accountId, requesterId, 0, 101))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Page size must be between 1 and 100");
  }
}
