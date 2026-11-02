package com.corebank.domain.entity;

/** Feature that originated a ledger entry. Stored as text, so new values need no migration. */
public enum TransactionChannel {
  TRANSFER,
  PIX,
  INVESTMENT,
  LOAN
}
