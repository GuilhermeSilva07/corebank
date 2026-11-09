package com.corebank.domain.command;

import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.exception.InvalidFilterException;
import com.corebank.domain.exception.InvalidPaginationException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Query for the ledger entries of one account. {@code from}, {@code to} and {@code type} are
 * optional filters; {@code null} means "not filtered". Both dates are inclusive calendar days.
 */
public record GetStatementCommand(
    UUID accountId,
    UUID requesterId,
    LocalDate from,
    LocalDate to,
    TransactionType type,
    int page,
    int size) {

  public GetStatementCommand {
    if (page < 0) {
      throw new InvalidPaginationException("Page cannot be less than 0");
    }
    if (size < 1 || size > 100) {
      throw new InvalidPaginationException("Page size must be between 1 and 100");
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new InvalidFilterException("'from' date cannot be after 'to' date");
    }
  }

  /** Unfiltered query, used by the account statement. */
  public GetStatementCommand(UUID accountId, UUID requesterId, int page, int size) {
    this(accountId, requesterId, null, null, null, page, size);
  }
}
