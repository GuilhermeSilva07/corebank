package com.corebank.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Address;
import com.corebank.domain.entity.Transaction;
import com.corebank.domain.entity.TransactionChannel;
import com.corebank.domain.entity.TransactionStatus;
import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.TransactionRepository;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class TransferIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private JwtService jwtService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void transfer_success_returns201MovesBalancesAndWritesTwoLegs() throws Exception {
    Account origin = createAccount("100.00");
    Account destination = createAccount("10.00");

    String responseBody =
        transfer(origin, origin.getId(), destination.getId(), "40.00")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transferId").isNotEmpty())
            .andExpect(jsonPath("$.originAccountId").value(origin.getId().toString()))
            .andExpect(jsonPath("$.destinationAccountId").value(destination.getId().toString()))
            .andExpect(jsonPath("$.amount").value(40.00))
            .andExpect(jsonPath("$.originBalanceAfter").value(60.00))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String transferId = JsonPath.read(responseBody, "$.transferId");

    assertBalance(origin, "60.00");
    assertBalance(destination, "50.00");

    List<Transaction> legs =
        transactionRepository.findAll().stream()
            .filter(t -> t.getTransferId().toString().equals(transferId))
            .toList();
    assertThat(legs).hasSize(2);

    Transaction debit = legOf(legs, TransactionType.DEBIT);
    Transaction credit = legOf(legs, TransactionType.CREDIT);
    assertThat(debit.getAccount().getId()).isEqualTo(origin.getId());
    assertThat(debit.getBalanceAfter()).isEqualByComparingTo("60.00");
    assertThat(credit.getAccount().getId()).isEqualTo(destination.getId());
    assertThat(credit.getBalanceAfter()).isEqualByComparingTo("50.00");
    for (Transaction leg : legs) {
      assertThat(leg.getAmount()).isEqualByComparingTo("40.00");
      assertThat(leg.getChannel()).isEqualTo(TransactionChannel.TRANSFER);
      assertThat(leg.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }
  }

  @Test
  void transfer_entireBalance_leavesOriginAtZero() throws Exception {
    Account origin = createAccount("50.00");
    Account destination = createAccount("0.00");

    transfer(origin, origin.getId(), destination.getId(), "50.00")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.originBalanceAfter").value(0.00));

    assertBalance(origin, "0.00");
    assertBalance(destination, "50.00");
  }

  @Test
  void transfer_withoutToken_returns401() throws Exception {
    Account origin = createAccount("100.00");
    Account destination = createAccount("0.00");

    mockMvc
        .perform(
            post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(origin.getId(), destination.getId(), "10.00")))
        .andExpect(status().isUnauthorized());

    assertBalance(origin, "100.00");
  }

  @Test
  void transfer_fromAnotherUsersAccount_returns403AccessDenied() throws Exception {
    Account victim = createAccount("100.00");
    Account attacker = createAccount("0.00");

    transfer(attacker, victim.getId(), attacker.getId(), "10.00")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    assertBalance(victim, "100.00");
    assertBalance(attacker, "0.00");
  }

  @Test
  void transfer_fromNonExistentAccount_returnsSame403() throws Exception {
    Account requester = createAccount("100.00");

    transfer(requester, UUID.randomUUID(), requester.getId(), "10.00")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void transfer_toNonExistentAccount_returns404AndChangesNothing() throws Exception {
    Account origin = createAccount("100.00");

    transfer(origin, origin.getId(), UUID.randomUUID(), "10.00")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DESTINATION_ACCOUNT_NOT_FOUND"));

    assertBalance(origin, "100.00");
    assertNoLegsFor(origin);
  }

  @Test
  void transfer_toSameAccount_returns422() throws Exception {
    Account origin = createAccount("100.00");

    transfer(origin, origin.getId(), origin.getId(), "10.00")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SELF_TRANSFER_NOT_ALLOWED"));

    assertBalance(origin, "100.00");
    assertNoLegsFor(origin);
  }

  @Test
  void transfer_aboveBalance_returns422WithShortfallAndChangesNothing() throws Exception {
    Account origin = createAccount("30.00");
    Account destination = createAccount("10.00");

    transfer(origin, origin.getId(), destination.getId(), "50.00")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
        .andExpect(jsonPath("$.message").value("Insufficient balance: missing 20.00"));

    assertBalance(origin, "30.00");
    assertBalance(destination, "10.00");
    assertNoLegsFor(origin);
    assertNoLegsFor(destination);
  }

  @Test
  void transfer_withInvalidAmount_returns400ValidationError() throws Exception {
    Account origin = createAccount("100.00");
    Account destination = createAccount("0.00");

    for (String amount : new String[] {"0", "-5.00", "10.005"}) {
      transfer(origin, origin.getId(), destination.getId(), amount)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    }

    mockMvc
        .perform(
            post("/transfers")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(origin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"originAccountId": "%s", "destinationAccountId": "%s"}
                    """
                        .formatted(origin.getId(), destination.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));

    assertBalance(origin, "100.00");
  }

  private ResultActions transfer(
      Account requester, UUID originId, UUID destinationId, String amount) throws Exception {
    return mockMvc.perform(
        post("/transfers")
            .header(HttpHeaders.AUTHORIZATION, bearerFor(requester))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(originId, destinationId, amount)));
  }

  private String body(UUID originId, UUID destinationId, String amount) {
    return """
        {"originAccountId": "%s", "destinationAccountId": "%s", "amount": %s}
        """
        .formatted(originId, destinationId, amount);
  }

  private void assertBalance(Account account, String expected) {
    assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo(expected);
  }

  private void assertNoLegsFor(Account account) {
    assertThat(transactionRepository.findAll())
        .noneMatch(t -> t.getAccount().getId().equals(account.getId()));
  }

  private Transaction legOf(List<Transaction> legs, TransactionType type) {
    return legs.stream().filter(t -> t.getType() == type).findFirst().orElseThrow();
  }

  private String bearerFor(Account account) {
    return "Bearer " + jwtService.generateAccessToken(account.getUser().getId());
  }

  private Account createAccount(String balance) {
    User user = new User();
    user.setFullName("Transfer Test User");
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
