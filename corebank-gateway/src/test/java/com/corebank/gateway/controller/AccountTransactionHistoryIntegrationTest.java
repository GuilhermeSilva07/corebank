package com.corebank.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.Address;
import com.corebank.domain.entity.TransactionStatus;
import com.corebank.domain.entity.TransactionType;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.service.JwtService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Covers the account-statement spec against a real Postgres. Ledger rows are inserted with JDBC so
 * that {@code created_at} can be set explicitly (the entity always stamps {@code Instant.now()}).
 */
@SpringBootTest
class AccountTransactionHistoryIntegrationTest {

  private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  // --- Access -------------------------------------------------------------------------------

  @Test
  void history_missingToken_returns401() throws Exception {
    Account account = createAccount("0.00");

    mockMvc
        .perform(get("/accounts/{id}/transactions", account.getId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void history_accountOfAnotherUser_returns403AccessDenied() throws Exception {
    Account victim = createAccount("0.00");
    Account attacker = createAccount("0.00");
    insertEntry(victim, TransactionType.CREDIT, "10.00", "10.00", sp("2026-11-01T10:00"));

    mockMvc
        .perform(history(victim, attacker))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.items").doesNotExist());
  }

  @Test
  void history_nonExistentAccount_returnsSame403AsAnotherUsersAccount() throws Exception {
    Account account = createAccount("0.00");

    mockMvc
        .perform(
            get("/accounts/{id}/transactions", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(account)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  // --- Content and ordering -----------------------------------------------------------------

  @Test
  void history_accountWithoutMovements_returnsEmptyList() throws Exception {
    Account account = createAccount("0.00");

    mockMvc
        .perform(history(account, account))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void history_listsEntriesWithRunningBalanceNewestFirst() throws Exception {
    Account account = createAccount("60.00");
    UUID credit =
        insertEntry(account, TransactionType.CREDIT, "100.00", "100.00", sp("2026-11-01T10:00"));
    UUID debit =
        insertEntry(account, TransactionType.DEBIT, "40.00", "60.00", sp("2026-11-02T10:00"));

    mockMvc
        .perform(history(account, account))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(debit.toString()))
        .andExpect(jsonPath("$.items[0].type").value("DEBIT"))
        .andExpect(jsonPath("$.items[0].amount").value(40.00))
        .andExpect(jsonPath("$.items[0].runningBalance").value(60.00))
        .andExpect(jsonPath("$.items[1].id").value(credit.toString()))
        .andExpect(jsonPath("$.items[1].type").value("CREDIT"))
        .andExpect(jsonPath("$.items[1].runningBalance").value(100.00))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void history_ordersByCreationNewestFirst() throws Exception {
    Account account = createAccount("0.00");
    UUID nov1 =
        insertEntry(account, TransactionType.CREDIT, "1.00", "1.00", sp("2026-11-01T10:00"));
    UUID nov3 =
        insertEntry(account, TransactionType.CREDIT, "1.00", "3.00", sp("2026-11-03T10:00"));
    UUID nov2 =
        insertEntry(account, TransactionType.CREDIT, "1.00", "2.00", sp("2026-11-02T10:00"));

    assertThat(itemIds(history(account, account)))
        .containsExactly(nov3.toString(), nov2.toString(), nov1.toString());
  }

  @Test
  void history_neverListsEntriesOfOtherAccounts() throws Exception {
    Account mine = createAccount("0.00");
    Account other = createAccount("0.00");
    UUID myEntry = insertEntry(mine, TransactionType.DEBIT, "5.00", "0.00", sp("2026-11-01T10:00"));
    insertEntry(other, TransactionType.CREDIT, "5.00", "5.00", sp("2026-11-01T10:00"));

    assertThat(itemIds(history(mine, mine))).containsExactly(myEntry.toString());
  }

  @Test
  void history_hidesPendingAndFailedEntries() throws Exception {
    Account account = createAccount("0.00");
    UUID completed =
        insertEntry(account, TransactionType.CREDIT, "10.00", "10.00", sp("2026-11-01T10:00"));
    insertEntry(
        account,
        TransactionType.DEBIT,
        "3.00",
        "7.00",
        sp("2026-11-02T10:00"),
        TransactionStatus.PENDING);
    insertEntry(
        account,
        TransactionType.DEBIT,
        "4.00",
        "6.00",
        sp("2026-11-03T10:00"),
        TransactionStatus.FAILED);

    mockMvc
        .perform(history(account, account))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(completed.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  // --- Pagination ---------------------------------------------------------------------------

  @Test
  void history_secondPage_returnsOldestEntriesAndTotals() throws Exception {
    Account account = createAccount("0.00");
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      ids.add(
          insertEntry(
              account,
              TransactionType.CREDIT,
              "1.00",
              (i + 1) + ".00",
              sp("2026-11-01T00:00").plusSeconds(i * 60L)));
    }

    List<String> secondPage = itemIds(history(account, account).param("page", "1"));

    // ids are in creation order, the page is newest first: the 5 oldest come reversed.
    assertThat(secondPage)
        .containsExactly(
            ids.get(4).toString(),
            ids.get(3).toString(),
            ids.get(2).toString(),
            ids.get(1).toString(),
            ids.get(0).toString());
    mockMvc
        .perform(history(account, account).param("page", "1"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(25))
        .andExpect(jsonPath("$.totalPages").value(2));
  }

  @Test
  void history_pagingThroughEqualTimestamps_neverRepeatsNorSkips() throws Exception {
    Account account = createAccount("0.00");
    Instant sameInstant = sp("2026-11-01T10:00");
    List<String> inserted = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      inserted.add(
          insertEntry(account, TransactionType.CREDIT, "1.00", "1.00", sameInstant).toString());
    }

    List<String> seen = new ArrayList<>();
    for (int page = 0; page < 3; page++) {
      seen.addAll(
          itemIds(
              history(account, account).param("page", String.valueOf(page)).param("size", "2")));
    }

    assertThat(seen)
        .hasSize(5)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(inserted);
  }

  @Test
  void history_pageBeyondTheLast_returnsEmptyListWithTotal() throws Exception {
    Account account = createAccount("0.00");
    for (int i = 0; i < 5; i++) {
      insertEntry(account, TransactionType.CREDIT, "1.00", "1.00", sp("2026-11-01T10:00"));
    }

    mockMvc
        .perform(history(account, account).param("page", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.totalElements").value(5));
  }

  @Test
  void history_invalidPagination_returns400InvalidPagination() throws Exception {
    Account account = createAccount("0.00");

    for (String[] params :
        new String[][] {{"size", "0"}, {"size", "101"}, {"page", "-1"}, {"page", "abc"}}) {
      mockMvc
          .perform(history(account, account).param(params[0], params[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }
  }

  // --- Period filter ------------------------------------------------------------------------

  @Test
  void history_periodBoundsAreInclusive() throws Exception {
    Account account = createAccount("0.00");
    UUID nov1 =
        insertEntry(account, TransactionType.CREDIT, "1.00", "1.00", sp("2026-11-01T00:00"));
    UUID nov2Late =
        insertEntry(account, TransactionType.CREDIT, "1.00", "2.00", sp("2026-11-02T23:59"));
    insertEntry(account, TransactionType.CREDIT, "1.00", "3.00", sp("2026-11-03T00:00"));
    insertEntry(account, TransactionType.CREDIT, "1.00", "0.00", sp("2026-10-31T23:59"));

    assertThat(
            itemIds(
                history(account, account).param("from", "2026-11-01").param("to", "2026-11-02")))
        .containsExactly(nov2Late.toString(), nov1.toString());
  }

  @Test
  void history_dayBoundaryFollowsSaoPauloTime() throws Exception {
    Account account = createAccount("0.00");
    // 01:00 UTC on Nov 3 is 22:00 on Nov 2 in Sao Paulo.
    UUID entry =
        insertEntry(
            account, TransactionType.CREDIT, "1.00", "1.00", Instant.parse("2026-11-03T01:00:00Z"));

    assertThat(
            itemIds(
                history(account, account).param("from", "2026-11-02").param("to", "2026-11-02")))
        .containsExactly(entry.toString());
    assertThat(
            itemIds(
                history(account, account).param("from", "2026-11-03").param("to", "2026-11-03")))
        .isEmpty();
  }

  @Test
  void history_onlyFrom_listsEverythingFromThatDayOn() throws Exception {
    Account account = createAccount("0.00");
    insertEntry(account, TransactionType.CREDIT, "1.00", "1.00", sp("2026-11-01T23:59"));
    UUID nov2 =
        insertEntry(account, TransactionType.CREDIT, "1.00", "2.00", sp("2026-11-02T00:00"));
    UUID nov5 =
        insertEntry(account, TransactionType.CREDIT, "1.00", "3.00", sp("2026-11-05T10:00"));

    assertThat(itemIds(history(account, account).param("from", "2026-11-02")))
        .containsExactly(nov5.toString(), nov2.toString());
  }

  @Test
  void history_onlyTo_listsEverythingUpToAndIncludingThatDay() throws Exception {
    Account account = createAccount("0.00");
    UUID oct = insertEntry(account, TransactionType.CREDIT, "1.00", "1.00", sp("2026-10-15T10:00"));
    UUID nov2 =
        insertEntry(account, TransactionType.CREDIT, "1.00", "2.00", sp("2026-11-02T23:59"));
    insertEntry(account, TransactionType.CREDIT, "1.00", "3.00", sp("2026-11-03T00:00"));

    assertThat(itemIds(history(account, account).param("to", "2026-11-02")))
        .containsExactly(nov2.toString(), oct.toString());
  }

  @Test
  void history_fromAfterTo_returns400InvalidFilter() throws Exception {
    Account account = createAccount("0.00");

    mockMvc
        .perform(history(account, account).param("from", "2026-11-05").param("to", "2026-11-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
  }

  @Test
  void history_malformedDate_returns400InvalidFilterNamingTheParameter() throws Exception {
    Account account = createAccount("0.00");

    mockMvc
        .perform(history(account, account).param("from", "01/11/2026"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
  }

  // --- Direction filter and composition -----------------------------------------------------

  @Test
  void history_typeDebit_listsOnlyDebits() throws Exception {
    Account account = createAccount("0.00");
    UUID debit1 =
        insertEntry(account, TransactionType.DEBIT, "1.00", "9.00", sp("2026-11-01T10:00"));
    insertEntry(account, TransactionType.CREDIT, "10.00", "19.00", sp("2026-11-02T10:00"));
    UUID debit2 =
        insertEntry(account, TransactionType.DEBIT, "1.00", "18.00", sp("2026-11-03T10:00"));

    mockMvc
        .perform(history(account, account).param("type", "DEBIT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    assertThat(itemIds(history(account, account).param("type", "DEBIT")))
        .containsExactly(debit2.toString(), debit1.toString());
  }

  @Test
  void history_unknownType_returns400InvalidFilterNamingTheParameter() throws Exception {
    Account account = createAccount("0.00");

    mockMvc
        .perform(history(account, account).param("type", "FOO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("type"));
  }

  @Test
  void history_periodAndTypeCompose() throws Exception {
    Account account = createAccount("0.00");
    UUID nov1Debit =
        insertEntry(account, TransactionType.DEBIT, "1.00", "9.00", sp("2026-11-01T10:00"));
    insertEntry(account, TransactionType.CREDIT, "5.00", "14.00", sp("2026-11-01T11:00"));
    insertEntry(account, TransactionType.DEBIT, "1.00", "13.00", sp("2026-11-03T10:00"));

    MockHttpServletRequestBuilder request =
        history(account, account)
            .param("from", "2026-11-01")
            .param("to", "2026-11-02")
            .param("type", "DEBIT");

    mockMvc.perform(request).andExpect(jsonPath("$.totalElements").value(1));
    assertThat(itemIds(request)).containsExactly(nov1Debit.toString());
  }

  // --- Shared contract with the statement ---------------------------------------------------

  @Test
  void statement_afterRealTransfer_listsDebitAndEqualsUnfilteredHistory() throws Exception {
    Account origin = createAccount("100.00");
    Account destination = createAccount("10.00");

    mockMvc
        .perform(
            post("/transfers")
                .header(HttpHeaders.AUTHORIZATION, bearerFor(origin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"originAccountId": "%s", "destinationAccountId": "%s", "amount": 40.00}
                    """
                        .formatted(origin.getId(), destination.getId())))
        .andExpect(status().isCreated());

    String statement =
        mockMvc
            .perform(
                get("/accounts/{id}/statement", origin.getId())
                    .header(HttpHeaders.AUTHORIZATION, bearerFor(origin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].type").value("DEBIT"))
            .andExpect(jsonPath("$.items[0].amount").value(40.00))
            .andExpect(jsonPath("$.items[0].runningBalance").value(60.00))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String history =
        mockMvc
            .perform(history(origin, origin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(statement).isEqualTo(history);
  }

  // --- Helpers ------------------------------------------------------------------------------

  private MockHttpServletRequestBuilder history(Account account, Account requester) {
    return get("/accounts/{id}/transactions", account.getId())
        .header(HttpHeaders.AUTHORIZATION, bearerFor(requester));
  }

  private List<String> itemIds(MockHttpServletRequestBuilder request) throws Exception {
    String body =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.items[*].id");
  }

  /** A wall-clock time in Sao Paulo, e.g. {@code sp("2026-11-02T23:59")}. */
  private static Instant sp(String localDateTime) {
    return LocalDateTime.parse(localDateTime).atZone(SAO_PAULO).toInstant();
  }

  private UUID insertEntry(
      Account account, TransactionType type, String amount, String balanceAfter, Instant at) {
    return insertEntry(account, type, amount, balanceAfter, at, TransactionStatus.COMPLETED);
  }

  private UUID insertEntry(
      Account account,
      TransactionType type,
      String amount,
      String balanceAfter,
      Instant at,
      TransactionStatus status) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO transactions
          (id, transfer_id, account_id, type, channel, status, amount, balance_after, created_at)
        VALUES (?, ?, ?, ?, 'TRANSFER', ?, ?, ?, ?)
        """,
        id,
        UUID.randomUUID(),
        account.getId(),
        type.name(),
        status.name(),
        new BigDecimal(amount),
        new BigDecimal(balanceAfter),
        OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    return id;
  }

  private String bearerFor(Account account) {
    return "Bearer " + jwtService.generateAccessToken(account.getUser().getId());
  }

  private Account createAccount(String balance) {
    User user = new User();
    user.setFullName("History Test User");
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
