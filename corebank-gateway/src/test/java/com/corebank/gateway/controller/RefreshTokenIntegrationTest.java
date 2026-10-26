package com.corebank.gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class RefreshTokenIntegrationTest {

  private static final String RAW_PASSWORD = "SenhaForte123";

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void refresh_validToken_rotatesAndReturnsNewTokens() throws Exception {
    String oldRefreshToken = registerAndLogin();

    MvcResult result =
        mockMvc
            .perform(
                post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody(oldRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andReturn();

    String newRefreshToken = readRefreshToken(result);
    assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
  }

  @Test
  void refresh_reusedOldToken_returns401() throws Exception {
    String oldRefreshToken = registerAndLogin();

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(oldRefreshToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(oldRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  private String registerAndLogin() throws Exception {
    String cpf = uniqueCpf();
    String email = uniqueEmail();

    mockMvc
        .perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(cpf, email)))
        .andExpect(status().isCreated());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(email, RAW_PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

    return readRefreshToken(loginResult);
  }

  private String readRefreshToken(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
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

  private String loginBody(String email, String password) {
    return """
        {
          "email": "%s",
          "password": "%s"
        }
        """
        .formatted(email, password);
  }

  private String refreshBody(String refreshToken) {
    return """
        {
          "refreshToken": "%s"
        }
        """
        .formatted(refreshToken);
  }

  private String uniqueCpf() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000L, 100_000_000_000L));
  }

  private String uniqueEmail() {
    return UUID.randomUUID() + "@corebank.com";
  }
}
