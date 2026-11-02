package com.corebank.domain.service;

import com.corebank.domain.command.TransferCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Transaction;
import com.corebank.domain.entity.TransactionChannel;
import com.corebank.domain.entity.TransactionStatus;
import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.exception.AccountAccessDeniedException;
import com.corebank.domain.exception.DestinationAccountNotFoundException;
import com.corebank.domain.exception.SelfTransferNotAllowedException;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.TransactionRepository;
import com.corebank.domain.result.TransferResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves money between two accounts of the bank. Both balance updates and both ledger legs commit
 * together or not at all.
 *
 * <p>Every money movement that touches account balances should go through here (or follow the same
 * locking rule): accounts are locked with {@code SELECT ... FOR UPDATE} in ascending id order, so
 * concurrent operations on the same accounts are serialized and cannot deadlock.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;

  @Transactional
  public TransferResult transfer(TransferCommand command) {
    UUID originId = command.originAccountId();
    UUID destinationId = command.destinationAccountId();

    if (originId.equals(destinationId)) {
      throw new SelfTransferNotAllowedException(
          "Origin and destination must be different accounts");
    }

    Optional<Account> origin;
    Optional<Account> destination;
    if (originId.compareTo(destinationId) < 0) {
      origin = accountRepository.findByIdForUpdate(originId);
      destination = accountRepository.findByIdForUpdate(destinationId);
    } else {
      destination = accountRepository.findByIdForUpdate(destinationId);
      origin = accountRepository.findByIdForUpdate(originId);
    }

    Account originAccount =
        origin
            .filter(a -> a.getUser().getId().equals(command.requesterId()))
            .orElseThrow(
                () ->
                    new AccountAccessDeniedException(
                        "Access denied. The account does not belong to the authenticated user."));
    Account destinationAccount =
        destination.orElseThrow(
            () -> new DestinationAccountNotFoundException("Destination account not found"));

    originAccount.debit(command.amount());
    destinationAccount.credit(command.amount());

    UUID transferId = UUID.randomUUID();
    Transaction debit = leg(transferId, originAccount, TransactionType.DEBIT, command);
    Transaction credit = leg(transferId, destinationAccount, TransactionType.CREDIT, command);
    transactionRepository.saveAll(List.of(debit, credit));

    return new TransferResult(
        transferId,
        originId,
        destinationId,
        command.amount(),
        originAccount.getBalance(),
        debit.getCreatedAt());
  }

  private Transaction leg(
      UUID transferId, Account account, TransactionType type, TransferCommand command) {
    return new Transaction(
        transferId,
        account,
        type,
        TransactionChannel.TRANSFER,
        TransactionStatus.COMPLETED,
        command.amount(),
        account.getBalance(),
        null,
        null);
  }
}
