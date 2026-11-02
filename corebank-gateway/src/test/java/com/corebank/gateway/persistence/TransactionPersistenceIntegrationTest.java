package com.corebank.gateway.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Transaction;
import com.corebank.domain.entity.TransactionChannel;
import com.corebank.domain.entity.TransactionStatus;
import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.TransactionRepository;
import com.corebank.domain.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies the V4 schema and the Transaction mapping against real Postgres. ddl-auto=validate only
 * checks columns and types, so the CHECK, FK and UNIQUE constraints are exercised here explicitly.
 */
@SpringBootTest
class TransactionPersistenceIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void transfer_savesDebitAndCreditLegsSharingTransferId() throws Exception {
    Account origin = registerUserAndGetAccount();
    Account destination = registerUserAndGetAccount();
    UUID transferId = UUID.randomUUID();

    transactionRepository.saveAndFlush(
        leg(transferId, origin, TransactionType.DEBIT, "50.00", "key-" + transferId));
    transactionRepository.saveAndFlush(
        leg(transferId, destination, TransactionType.CREDIT, "50.00", null));

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT account_id, type, amount FROM transactions WHERE transfer_id = ?", transferId);

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .anySatisfy(
            r -> {
              assertThat(r.get("account_id")).isEqualTo(origin.getId());
              assertThat(r.get("type")).isEqualTo("DEBIT");
              assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("50.00");
            })
        .anySatisfy(
            r -> {
              assertThat(r.get("account_id")).isEqualTo(destination.getId());
              assertThat(r.get("type")).isEqualTo("CREDIT");
              assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("50.00");
            });
  }

  @Test
  void zeroAmount_isRejected() throws Exception {
    Account account = registerUserAndGetAccount();

    assertThatThrownBy(
            () ->
                transactionRepository.saveAndFlush(
                    leg(UUID.randomUUID(), account, TransactionType.CREDIT, "0.00", null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void negativeAmount_isRejected() throws Exception {
    Account account = registerUserAndGetAccount();

    assertThatThrownBy(
            () ->
                transactionRepository.saveAndFlush(
                    leg(UUID.randomUUID(), account, TransactionType.DEBIT, "-10.00", null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void nonExistentAccount_isRejected() {
    Account missing = accountRepository.getReferenceById(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                transactionRepository.saveAndFlush(
                    leg(UUID.randomUUID(), missing, TransactionType.CREDIT, "10.00", null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void duplicateIdempotencyKey_onSameAccount_isRejected() throws Exception {
    Account account = registerUserAndGetAccount();
    String key = "key-" + UUID.randomUUID();

    transactionRepository.saveAndFlush(
        leg(UUID.randomUUID(), account, TransactionType.DEBIT, "10.00", key));

    assertThatThrownBy(
            () ->
                transactionRepository.saveAndFlush(
                    leg(UUID.randomUUID(), account, TransactionType.DEBIT, "10.00", key)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void sameIdempotencyKey_onDifferentAccounts_isAccepted() throws Exception {
    Account first = registerUserAndGetAccount();
    Account second = registerUserAndGetAccount();
    String key = "key-" + UUID.randomUUID();

    transactionRepository.saveAndFlush(
        leg(UUID.randomUUID(), first, TransactionType.DEBIT, "10.00", key));
    transactionRepository.saveAndFlush(
        leg(UUID.randomUUID(), second, TransactionType.DEBIT, "10.00", key));

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE idempotency_key = ?", Integer.class, key);
    assertThat(count).isEqualTo(2);
  }

  @Test
  void entriesWithoutIdempotencyKey_onSameAccount_areAccepted() throws Exception {
    Account account = registerUserAndGetAccount();

    transactionRepository.saveAndFlush(
        leg(UUID.randomUUID(), account, TransactionType.CREDIT, "10.00", null));
    transactionRepository.saveAndFlush(
        leg(UUID.randomUUID(), account, TransactionType.CREDIT, "20.00", null));

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transactions WHERE account_id = ? AND idempotency_key IS NULL",
            Integer.class,
            account.getId());
    assertThat(count).isEqualTo(2);
  }

  @Test
  void nullDescription_isAccepted() throws Exception {
    Account account = registerUserAndGetAccount();

    Transaction saved =
        transactionRepository.saveAndFlush(
            new Transaction(
                UUID.randomUUID(),
                account,
                TransactionType.CREDIT,
                TransactionChannel.TRANSFER,
                TransactionStatus.COMPLETED,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                null,
                null));

    assertThat(transactionRepository.findById(saved.getId())).isPresent();
  }

  @Test
  void markCompleted_persistsStatusAndKeepsOtherFields() throws Exception {
    Account account = registerUserAndGetAccount();
    Transaction pending =
        transactionRepository.saveAndFlush(
            new Transaction(
                UUID.randomUUID(),
                account,
                TransactionType.CREDIT,
                TransactionChannel.PIX,
                TransactionStatus.PENDING,
                new BigDecimal("25.00"),
                new BigDecimal("125.00"),
                "pix in",
                null));

    Transaction before = transactionRepository.findById(pending.getId()).orElseThrow();
    before.markCompleted();
    transactionRepository.saveAndFlush(before);

    Transaction after = transactionRepository.findById(pending.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    assertThat(after.getTransferId()).isEqualTo(before.getTransferId());
    assertThat(after.getType()).isEqualTo(TransactionType.CREDIT);
    assertThat(after.getChannel()).isEqualTo(TransactionChannel.PIX);
    assertThat(after.getAmount()).isEqualByComparingTo("25.00");
    assertThat(after.getBalanceAfter()).isEqualByComparingTo("125.00");
    assertThat(after.getDescription()).isEqualTo("pix in");
    assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
  }

  private Transaction leg(
      UUID transferId, Account account, TransactionType type, String amount, String key) {
    return new Transaction(
        transferId,
        account,
        type,
        TransactionChannel.TRANSFER,
        TransactionStatus.COMPLETED,
        new BigDecimal(amount),
        new BigDecimal("100.00"),
        "test leg",
        key);
  }

  private Account registerUserAndGetAccount() throws Exception {
    String email = UUID.randomUUID() + "@corebank.com";

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(uniqueCpf(), email)))
        .andExpect(status().isCreated());

    User user = userRepository.findByEmail(email).orElseThrow();
    return accountRepository.findAll().stream()
        .filter(a -> a.getUser().getId().equals(user.getId()))
        .findFirst()
        .orElseThrow();
  }

  private String registerBody(String cpf, String email) {
    return """
        {
          "fullName": "QA Teste",
          "cpf": "%s",
          "email": "%s",
          "password": "SenhaForte123",
          "phone": "11999999999",
          "birthDate": "1995-05-20",
          "address": {
            "street": "Rua Teste",
            "number": "100",
            "neighborhood": "Centro",
            "city": "Sao Paulo",
            "state": "SP",
            "zipCode": "01000000"
          }
        }
        """
        .formatted(cpf, email);
  }

  private String uniqueCpf() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000L, 100_000_000_000L));
  }
}
