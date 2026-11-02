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
class JwtAuthenticationFilterIntegrationTest {

  private static final String RAW_PASSWORD = "SenhaForte123";

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
  void statement_validToken_reachesControllerWithCorrectPrincipal() throws Exception {
    Account account = registerUserAndGetAccount();
    String validToken = jwtService.generateAccessToken(account.getUser().getId());

    mockMvc
        .perform(
            get("/accounts/{id}/statement", account.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
        .andExpect(status().isOk());
  }

  @Test
  void statement_missingToken_returns401WithStandardBody() throws Exception {
    Account account = registerUserAndGetAccount();

    mockMvc
        .perform(get("/accounts/{id}/statement", account.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void statement_tamperedToken_returns401WithSameStandardBodyAsMissingToken() throws Exception {
    Account account = registerUserAndGetAccount();
    String validToken = jwtService.generateAccessToken(account.getUser().getId());
    String tamperedToken = validToken + "tampered";

    mockMvc
        .perform(
            get("/accounts/{id}/statement", account.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void login_withoutToken_stillSucceeds() throws Exception {
    String email = registerUserAndGetAccount().getUser().getEmail();

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """
                        .formatted(email, RAW_PASSWORD)))
        .andExpect(status().isOk());
  }

  private Account registerUserAndGetAccount() throws Exception {
    String cpf = uniqueCpf();
    String email = uniqueEmail();

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(cpf, email)))
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
          "password": "%s",
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
        .formatted(cpf, email, RAW_PASSWORD);
  }

  private String uniqueCpf() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000L, 100_000_000_000L));
  }

  private String uniqueEmail() {
    return UUID.randomUUID() + "@corebank.com";
  }
}
