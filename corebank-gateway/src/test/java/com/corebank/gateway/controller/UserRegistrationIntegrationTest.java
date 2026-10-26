package com.corebank.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corebank.domain.entity.Account;
import com.corebank.domain.entity.User;
import com.corebank.domain.repository.AccountRepository;
import com.corebank.domain.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class UserRegistrationIntegrationTest {

  private static final String RAW_PASSWORD = "SenhaForte123";

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void register_validRequest_returns201AndPersistsUserWithHashedPasswordAndAccount()
      throws Exception {
    String cpf = uniqueCpf();
    String email = uniqueEmail();

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(cpf, email, RAW_PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.fullName").value("QA Teste"))
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.accountNumber").isNotEmpty())
        .andExpect(jsonPath("$.password").doesNotExist())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());

    User saved = userRepository.findByEmail(email).orElseThrow();
    assertThat(saved.getCpf()).isEqualTo(cpf);
    assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD).startsWith("$2");

    Account account =
        accountRepository.findAll().stream()
            .filter(a -> a.getUser().getId().equals(saved.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(account.getAgencyNumber()).isEqualTo("0001");
  }

  @Test
  void register_duplicateCpf_returns409AndDoesNotCreateSecondUser() throws Exception {
    String cpf = uniqueCpf();
    registerSuccessfully(cpf, uniqueEmail());

    String secondEmail = uniqueEmail();
    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(cpf, secondEmail, RAW_PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));

    assertThat(userRepository.existsByEmail(secondEmail)).isFalse();
  }

  @Test
  void register_duplicateEmail_returns409AndDoesNotCreateSecondUser() throws Exception {
    String email = uniqueEmail();
    registerSuccessfully(uniqueCpf(), email);

    String secondCpf = uniqueCpf();
    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(secondCpf, email, RAW_PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));

    assertThat(userRepository.existsByCpf(secondCpf)).isFalse();
  }

  @Test
  void register_invalidInput_returns400WithFieldErrors() throws Exception {
    String invalidBody =
        """
        {
          "fullName": "",
          "cpf": "%s",
          "email": "not-an-email",
          "password": "123"
        }
        """
            .formatted(uniqueCpf());

    mockMvc
        .perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(invalidBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(
            jsonPath("$.fieldErrors[*].field")
                .value(hasItems("fullName", "email", "password", "birthDate", "address")));
  }

  private void registerSuccessfully(String cpf, String email) throws Exception {
    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody(cpf, email, RAW_PASSWORD)))
        .andExpect(status().isCreated());
  }

  private String validBody(String cpf, String email, String password) {
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
        .formatted(cpf, email, password);
  }

  private String uniqueCpf() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000L, 100_000_000_000L));
  }

  private String uniqueEmail() {
    return UUID.randomUUID() + "@corebank.com";
  }
}
