package com.corebank.domain.repository;

import com.corebank.domain.entity.Transaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TransactionRepository
    extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {}
