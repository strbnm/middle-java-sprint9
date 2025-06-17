package ru.strbnm.exchange_service.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.strbnm.exchange_service.config.TestSecurityConfig;
import ru.strbnm.exchange_service.domain.ConvertedAmount;
import ru.strbnm.exchange_service.repository.ExchangeRateRepository;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-test"})
@AutoConfigureWebTestClient(timeout = "36000")
@Import(TestSecurityConfig.class)
public class ExchangeControllerIntegrationTest {

  @Autowired private DatabaseClient databaseClient;

  @Autowired private WebTestClient webTestClient;

  @Autowired private ExchangeRateRepository exchangeRateRepository;

  private static final String INIT_SCRIPT_PATH = "src/test/resources/scripts/INIT_STORE_RECORD.sql";
  private static final String CLEAN_SCRIPT_PATH =
      "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

  @BeforeEach
  void setupDatabase() {
    if (databaseClient == null) {
      throw new IllegalStateException(
          "DatabaseClient не инициализирован. Проверьте конфигурацию тестов.");
    }
    executeSqlScript(INIT_SCRIPT_PATH).block();
  }

  @AfterEach
  void cleanup() {
    if (databaseClient == null) {
      throw new IllegalStateException(
          "DatabaseClient не инициализирован. Проверьте конфигурацию тестов.");
    }
    executeSqlScript(CLEAN_SCRIPT_PATH).block();
  }

  @Test
  void testConvertCurrency_shouldReturnConvertedAmount() {

    webTestClient
        .mutateWith(mockJwt())
        .post()
        .uri("/api/v1/convert")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"from\": \"RUB\", \"to\": \"USD\", \"amount\": 1000.0}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ConvertedAmount.class)
        .value(
            response -> {
              assertNotNull(response);
              assertEquals(new BigDecimal("12.0000"), response.getAmount());
            });
  }

  @Test
  void testConvertCurrency_withNegativeAmount_shouldReturnBadRequest() {

    webTestClient
        .mutateWith(mockJwt())
        .post()
        .uri("/api/v1/convert")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"from\": \"RUB\", \"to\": \"USD\", \"amount\": -1000.0}")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void testConvertCurrency_Unauthorized_shouldReturn401() {

    webTestClient
        .post()
        .uri("/api/v1/convert")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"from\": \"RUB\", \"to\": \"USD\", \"amount\": 1000.0}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  private Mono<Void> executeSqlScript(String scriptPath) {
    try {
      String sql = new String(Files.readAllBytes(Paths.get(scriptPath)));
      return databaseClient.sql(sql).then();
    } catch (Exception e) {
      throw new RuntimeException("Ошибка при выполнении SQL-скрипта: " + scriptPath, e);
    }
  }
}
