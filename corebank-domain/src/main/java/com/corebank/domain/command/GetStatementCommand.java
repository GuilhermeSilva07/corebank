package com.corebank.domain.command;

import java.util.UUID;

public record GetStatementCommand(UUID accountId, UUID requesterId, int page, int size) {
  public GetStatementCommand {
    if (page < 0) {
      throw new IllegalArgumentException("Page cannot be less than 0");
    }
    if (size < 1 || size > 100) {
      throw new IllegalArgumentException("Page size must be between 1 and 100");
    }
  }
}
