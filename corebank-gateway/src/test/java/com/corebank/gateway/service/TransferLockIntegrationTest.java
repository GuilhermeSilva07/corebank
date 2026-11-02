package com.corebank.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.corebank.domain.command.TransferCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Address;
import com.corebank.domain.entity.User;
import com.corebank.domain.exception.InsufficientBalanceException;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.service.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test for the pessimistic lock with two threads. The full N-request battery that proves the
 * test fails without the lock belongs to the [TRANSFER] Concurrency test card.
 */
@SpringBootTest
class TransferLockIntegrationTest {

  @Autowired private TransferService transferService;
  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;

  @Test
  void twoSimultaneousDebitsExceedingBalance_onlyOneSucceeds() throws Exception {
    Account origin = createAccount("100.00");
    Account destinationA = createAccount("0.00");
    Account destinationB = createAccount("0.00");

    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger insufficient = new AtomicInteger();

    runTogether(
        List.of(
            () -> transferCounting(origin, destinationA, "70.00", succeeded, insufficient),
            () -> transferCounting(origin, destinationB, "70.00", succeeded, insufficient)));

    assertThat(succeeded.get()).isEqualTo(1);
    assertThat(insufficient.get()).isEqualTo(1);
    assertBalance(origin, "30.00");
  }

  @Test
  void oppositeTransfersAtTheSameTime_bothCompleteWithoutDeadlock() throws Exception {
    Account a = createAccount("100.00");
    Account b = createAccount("100.00");

    AtomicInteger succeeded = new AtomicInteger();
    AtomicInteger insufficient = new AtomicInteger();

    runTogether(
        List.of(
            () -> transferCounting(a, b, "30.00", succeeded, insufficient),
            () -> transferCounting(b, a, "50.00", succeeded, insufficient)));

    assertThat(succeeded.get()).isEqualTo(2);
    assertBalance(a, "120.00");
    assertBalance(b, "80.00");
  }

  private void transferCounting(
      Account origin,
      Account destination,
      String amount,
      AtomicInteger succeeded,
      AtomicInteger insufficient) {
    try {
      transferService.transfer(
          new TransferCommand(
              origin.getUser().getId(),
              origin.getId(),
              destination.getId(),
              new BigDecimal(amount)));
      succeeded.incrementAndGet();
    } catch (InsufficientBalanceException e) {
      insufficient.incrementAndGet();
    }
  }

  /** Starts all tasks at the same instant and fails the test if they do not finish in time. */
  private void runTogether(List<Runnable> tasks) throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
    CountDownLatch ready = new CountDownLatch(tasks.size());
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(tasks.size());

    for (Runnable task : tasks) {
      executor.submit(
          () -> {
            ready.countDown();
            try {
              start.await();
              task.run();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              done.countDown();
            }
          });
    }

    ready.await();
    start.countDown();
    boolean finished = done.await(10, TimeUnit.SECONDS);
    executor.shutdownNow();
    assertThat(finished).as("transfers finished without deadlock").isTrue();
  }

  private void assertBalance(Account account, String expected) {
    assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo(expected);
  }

  private Account createAccount(String balance) {
    User user = new User();
    user.setFullName("Lock Test User");
    user.setCpf(UUID.randomUUID().toString().substring(0, 11));
    user.setEmail(UUID.randomUUID() + "@corebank.com");
    user.setPasswordHash("irrelevant-for-this-test");
    user.setBirthDate(LocalDate.of(1995, 5, 20));
    user.setAddress(new Address("Rua Teste", "100", null, "Centro", "Sao Paulo", "SP", "01000000"));
    user.setCreatedAt(Instant.now());
    user = userRepository.save(user);

    Account account = new Account();
    account.setAccountNumber(UUID.randomUUID().toString().substring(0, 12));
    account.setAgencyNumber("0001");
    account.setBalance(new BigDecimal(balance));
    account.setUser(user);
    account.setCreatedAt(Instant.now());
    return accountRepository.save(account);
  }
}
