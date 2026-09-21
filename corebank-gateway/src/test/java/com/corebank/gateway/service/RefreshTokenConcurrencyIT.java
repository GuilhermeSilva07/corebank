package com.corebank.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.corebank.domain.entity.Address;
import com.corebank.domain.entity.User;
import com.corebank.domain.exception.InvalidRefreshTokenException;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.result.RefreshResult;
import com.corebank.domain.service.RefreshTokenService;
import java.time.Instant;
import java.time.LocalDate;
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
 * Proves the fix for the race condition found in review: two concurrent POST /auth/refresh calls
 * with the same refresh token must not both succeed. This is the same class of problem the
 * [TRANSFER] Concurrency test card addresses for balance checks, applied to token rotation.
 */
@SpringBootTest
class RefreshTokenConcurrencyIT {

  @Autowired private RefreshTokenService refreshTokenService;

  @Autowired private UserRepository userRepository;

  @Test
  void rotate_twoConcurrentRequestsWithSameToken_onlyOneSucceeds() throws InterruptedException {
    User user = createAndSaveUser();
    String rawRefreshToken = refreshTokenService.generateAndStore(user);

    int threadCount = 2;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger rejectedCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executorService.submit(
          () -> {
            readyLatch.countDown();
            try {
              startLatch.await();
              RefreshResult result = refreshTokenService.rotate(rawRefreshToken);
              if (result != null) {
                successCount.incrementAndGet();
              }
            } catch (InvalidRefreshTokenException e) {
              rejectedCount.incrementAndGet();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    readyLatch.await();
    startLatch.countDown();
    doneLatch.await(10, TimeUnit.SECONDS);
    executorService.shutdown();

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(rejectedCount.get()).isEqualTo(1);
  }

  private User createAndSaveUser() {
    User user = new User();
    user.setFullName("Concurrency Test User");
    user.setCpf(UUID.randomUUID().toString().substring(0, 11));
    user.setEmail(UUID.randomUUID() + "@corebank.com");
    user.setPasswordHash("irrelevant-for-this-test");
    user.setBirthDate(LocalDate.of(1995, 5, 20));
    user.setAddress(new Address("Rua Teste", "100", null, "Centro", "Sao Paulo", "SP", "01000000"));
    user.setCreatedAt(Instant.now());
    return userRepository.save(user);
  }
}
