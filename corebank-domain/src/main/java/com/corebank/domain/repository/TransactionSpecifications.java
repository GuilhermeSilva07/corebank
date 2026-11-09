package com.corebank.domain.repository;

import com.corebank.domain.entity.Transaction;
import com.corebank.domain.entity.TransactionStatus;
import com.corebank.domain.entity.TransactionType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for ledger queries. Combine them with {@link Specification#and}. */
public final class TransactionSpecifications {

  private TransactionSpecifications() {}

  public static Specification<Transaction> ofAccount(UUID accountId) {
    return (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
  }

  public static Specification<Transaction> completed() {
    return (root, query, cb) -> cb.equal(root.get("status"), TransactionStatus.COMPLETED);
  }

  /** Entries created at or after {@code start}. */
  public static Specification<Transaction> createdAtFrom(Instant start) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start);
  }

  /** Entries created strictly before {@code end}. */
  public static Specification<Transaction> createdAtBefore(Instant end) {
    return (root, query, cb) -> cb.lessThan(root.get("createdAt"), end);
  }

  public static Specification<Transaction> ofType(TransactionType type) {
    return (root, query, cb) -> cb.equal(root.get("type"), type);
  }
}
