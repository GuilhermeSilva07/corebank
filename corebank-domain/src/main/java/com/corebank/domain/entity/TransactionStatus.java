package com.corebank.domain.entity;

/**
 * Lifecycle of a ledger entry. Synchronous movements are written as COMPLETED; a failed synchronous
 * movement rolls back and leaves no entry. PENDING/FAILED exist for async flows.
 */
public enum TransactionStatus {
  PENDING,
  COMPLETED,
  FAILED
}
