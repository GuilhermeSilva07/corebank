package com.corebank.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.corebank.domain.command.TransferCommand;
import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Address;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.TransactionRepository;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.service.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Forces a failure after the origin was debited (writing the ledger legs blows up) and proves the
 * whole transfer rolls back: no partial state.
 */
@SpringBootTest
class TransferRollbackIntegrationTest {

  @Autowired private TransferService transferService;
  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;

  @MockitoBean private TransactionRepository transactionRepository;

  @Test
  void transfer_failureWhileWritingLedger_leavesBothBalancesUntouched() {
    Account origin = createAccount("100.00");
    Account destination = createAccount("10.00");
    when(transactionRepository.saveAll(any())).thenThrow(new IllegalStateException("boom"));

    TransferCommand command =
        new TransferCommand(
            origin.getUser().getId(), origin.getId(), destination.getId(), new BigDecimal("40.00"));

    assertThatThrownBy(() -> transferService.transfer(command))
        .isInstanceOf(IllegalStateException.class);

    assertThat(accountRepository.findById(origin.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("100.00");
    assertThat(accountRepository.findById(destination.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("10.00");
  }

  private Account createAccount(String balance) {
    User user = new User();
    user.setFullName("Rollback Test User");
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
