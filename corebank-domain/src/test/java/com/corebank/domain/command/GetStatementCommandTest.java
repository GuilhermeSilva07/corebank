package com.corebank.domain.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.exception.InvalidFilterException;
import com.corebank.domain.exception.InvalidPaginationException;
import java.time.LocalDate;
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
  void create_withExactBoundaryPagination_succeeds() {
    assertThatCode(() -> new GetStatementCommand(accountId, requesterId, 0, 1))
        .doesNotThrowAnyException();
    assertThatCode(() -> new GetStatementCommand(accountId, requesterId, 0, 100))
        .doesNotThrowAnyException();
  }

  @Test
  void create_withNegativePage_throwsInvalidPaginationException() {
    assertThatThrownBy(() -> new GetStatementCommand(accountId, requesterId, -1, 20))
        .isInstanceOf(InvalidPaginationException.class)
        .hasMessage("Page cannot be less than 0");
  }

  @Test
  void create_withSizeLessThanOne_throwsInvalidPaginationException() {
    assertThatThrownBy(() -> new GetStatementCommand(accountId, requesterId, 0, 0))
        .isInstanceOf(InvalidPaginationException.class)
        .hasMessage("Page size must be between 1 and 100");
  }

  @Test
  void create_withSizeGreaterThanOneHundred_throwsInvalidPaginationException() {
    assertThatThrownBy(() -> new GetStatementCommand(accountId, requesterId, 0, 101))
        .isInstanceOf(InvalidPaginationException.class)
        .hasMessage("Page size must be between 1 and 100");
  }

  @Test
  void create_withSameFromAndTo_succeeds() {
    LocalDate day = LocalDate.of(2026, 11, 2);
    assertThatCode(() -> new GetStatementCommand(accountId, requesterId, day, day, null, 0, 20))
        .doesNotThrowAnyException();
  }

  @Test
  void create_withOnlyFromOrOnlyTo_succeeds() {
    LocalDate day = LocalDate.of(2026, 11, 2);
    assertThatCode(() -> new GetStatementCommand(accountId, requesterId, day, null, null, 0, 20))
        .doesNotThrowAnyException();
    assertThatCode(() -> new GetStatementCommand(accountId, requesterId, null, day, null, 0, 20))
        .doesNotThrowAnyException();
  }

  @Test
  void create_withFromAfterTo_throwsInvalidFilterException() {
    LocalDate from = LocalDate.of(2026, 11, 5);
    LocalDate to = LocalDate.of(2026, 11, 1);
    assertThatThrownBy(
            () ->
                new GetStatementCommand(
                    accountId, requesterId, from, to, TransactionType.DEBIT, 0, 20))
        .isInstanceOf(InvalidFilterException.class)
        .hasMessage("'from' date cannot be after 'to' date");
  }

  @Test
  void create_withPaginationOnlyConstructor_leavesFiltersNull() {
    GetStatementCommand command = new GetStatementCommand(accountId, requesterId, 2, 30);

    assertThat(command.from()).isNull();
    assertThat(command.to()).isNull();
    assertThat(command.type()).isNull();
    assertThat(command.page()).isEqualTo(2);
    assertThat(command.size()).isEqualTo(30);
  }
}
