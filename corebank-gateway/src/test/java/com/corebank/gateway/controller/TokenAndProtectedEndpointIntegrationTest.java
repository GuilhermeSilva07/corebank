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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class TokenAndProtectedEndpointIntegrationTest {

  @Value("${jwt.secret}")
  private String jwtSecret;

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
  void statement_expiredToken_returns401WithStandardBody() throws Exception {
    Account account = registerUserAndGetAccount();
    String expiredToken =
        tokenFor(account.getUser().getId().toString(), jwtSecret, new Date(0), new Date(1000));

    mockMvc
        .perform(
            get("/accounts/{id}/statement", account.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void statement_tokenSignedWithAnotherKey_returns401() throws Exception {
    Account account = registerUserAndGetAccount();
    String forgedToken =
        tokenFor(
            account.getUser().getId().toString(),
            "another-secret-key-with-more-than-32-characters-long",
            new Date(),
            new Date(System.currentTimeMillis() + 60_000));

    mockMvc
        .perform(
            get("/accounts/{id}/statement", account.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void statement_validSignatureButSubjectNotUuid_returns401() throws Exception {
    Account account = registerUserAndGetAccount();
    String token =
        tokenFor(
            "not-a-uuid", jwtSecret, new Date(), new Date(System.currentTimeMillis() + 60_000));

    mockMvc
        .perform(
            get("/accounts/{id}/statement", account.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void statement_ownerWithValidToken_returns200() throws Exception {
    Account account = registerUserAndGetAccount();

    mockMvc
        .perform(
            get("/accounts/{id}/statement", account.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(account)))
        .andExpect(status().isOk());
  }

  @Test
  void statement_accountOfAnotherUser_returns403AccessDenied() throws Exception {
    Account victimAccount = registerUserAndGetAccount();
    Account attackerAccount = registerUserAndGetAccount();

    mockMvc
        .perform(
            get("/accounts/{id}/statement", victimAccount.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(attackerAccount)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void statement_nonExistentAccount_returnsSame403AsAnotherUsersAccount() throws Exception {
    Account account = registerUserAndGetAccount();

    mockMvc
        .perform(
            get("/accounts/{id}/statement", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearerFor(account)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  private String tokenFor(String subject, String secret, Date issuedAt, Date expiration) {
    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(issuedAt)
        .setExpiration(expiration)
        .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
        .compact();
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
