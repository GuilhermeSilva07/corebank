package com.corebank.gateway.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.UserRepository;
import com.corebank.domain.service.JwtService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AccountBalanceIntegrationTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JwtService jwtService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void balance_ownerWithValidToken_returns200WithBalance() throws Exception {
    Account account = registerUserAndGetAccount();
    account.setBalance(new BigDecimal("123.45"));
    accountRepository.save(account);

    mockMvc
        .perform(
            get("/accounts/{id}/balance", account.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(account)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(123.45));
  }

  @Test
  void balance_accountOfAnotherUser_returns403AccessDenied() throws Exception {
    Account victimAccount = registerUserAndGetAccount();
    Account attackerAccount = registerUserAndGetAccount();

    mockMvc
        .perform(
            get("/accounts/{id}/balance", victimAccount.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(attackerAccount)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void balance_nonExistentAccount_returnsSame403AsAnotherUsersAccount() throws Exception {
    Account account = registerUserAndGetAccount();

    mockMvc
        .perform(
            get("/accounts/{id}/balance", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(account)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void balance_missingToken_returns401() throws Exception {
    Account account = registerUserAndGetAccount();

    mockMvc
        .perform(get("/accounts/{id}/balance", account.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  private String bearerFor(Account account) {
    return "Bearer " + jwtService.generateAccessToken(account.getUser().getId());
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
