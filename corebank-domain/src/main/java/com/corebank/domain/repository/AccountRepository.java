package com.corebank.domain.repository;

import com.corebank.domain.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

  /**
   * Loads the account with a row lock ({@code SELECT ... FOR UPDATE}) held until the surrounding
   * transaction ends. Must be called inside a transaction.
   *
   * <p>When one operation locks several accounts, request the locks in ascending id order ({@link
   * UUID#compareTo}) so that concurrent operations on the same accounts cannot deadlock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id = :id")
  Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
