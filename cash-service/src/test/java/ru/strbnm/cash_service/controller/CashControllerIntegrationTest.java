package ru.strbnm.cash_service.controller;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.strbnm.cash_service.config.TestSecurityConfig;
import ru.strbnm.cash_service.domain.CashCurrencyEnum;
import ru.strbnm.cash_service.domain.CashOperationRequest;
import ru.strbnm.cash_service.domain.CashOperationResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-test"})
@AutoConfigureWebTestClient(timeout = "36000")
@AutoConfigureStubRunner(
    ids = {
            "ru.strbnm:accounts-service:+:stubs:7086",
            "ru.strbnm:blocker-service:+:stubs:7085"
    },
    stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "http://localhost:8081/repository/maven-public/,http://nexus:8081/repository/maven-public/")
@Import(TestSecurityConfig.class)
@EmbeddedKafka(topics = "notifications")
class CashControllerIntegrationTest {

  @Autowired private DatabaseClient databaseClient;

  @Autowired private WebTestClient webTestClient;

  private static final String CLEAN_SCRIPT_PATH =
      "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

  @AfterEach
  void cleanup() {
    if (databaseClient == null) {
      throw new IllegalStateException(
          "DatabaseClient не инициализирован. Проверьте конфигурацию тестов.");
    }
    executeSqlScript(CLEAN_SCRIPT_PATH).block();
  }

  private Mono<Void> executeSqlScript(String scriptPath) {
    try {
      String sql = new String(Files.readAllBytes(Paths.get(scriptPath)));
      return databaseClient.sql(sql).then();
    } catch (Exception e) {
      throw new RuntimeException("Ошибка при выполнении SQL-скрипта: " + scriptPath, e);
    }
  }

  @Test
  void cashTransactionSuccess() {
    CashOperationRequest cashOperationRequest = new CashOperationRequest(
            "test_user1",
            CashCurrencyEnum.RUB,
            new BigDecimal("1000.0"),
            CashOperationRequest.ActionEnum.GET
    );
    webTestClient
            .mutateWith(mockJwt())
            .post()
            .uri("/api/v1/cash")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(cashOperationRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CashOperationResponse.class)
            .value(
                    cashOperationResponse -> {
                      assertNotNull(cashOperationResponse);
                      assertEquals(CashOperationResponse.OperationStatusEnum.SUCCESS, cashOperationResponse.getOperationStatus());
                      assertTrue(cashOperationResponse.getErrors().isEmpty());
                    });
  }

  @Test
  void cashTransactionFailed_MissingCurrencyAccount() {
    CashOperationRequest cashOperationRequest = new CashOperationRequest(
            "test_user1",
            CashCurrencyEnum.USD,
            new BigDecimal("1000.0"),
            CashOperationRequest.ActionEnum.GET
    );
    webTestClient
            .mutateWith(mockJwt())
            .post()
            .uri("/api/v1/cash")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(cashOperationRequest)
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody(CashOperationResponse.class)
            .value(
                    cashOperationResponse -> {
                      assertNotNull(cashOperationResponse);
                      assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
                      assertFalse(cashOperationResponse.getErrors().isEmpty());
                      assertEquals(List.of("У Вас отсутствует счет в выбранной валюте"), cashOperationResponse.getErrors());
                    });
  }

    @Test
    void cashTransactionFailed_BlockerExceedLimitOperation() {
        CashOperationRequest cashOperationRequest = new CashOperationRequest(
                "test_user1",
                CashCurrencyEnum.USD,
                new BigDecimal("2000.0"),
                CashOperationRequest.ActionEnum.GET
        );
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/cash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cashOperationRequest)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody(CashOperationResponse.class)
                .value(
                        cashOperationResponse -> {
                            assertNotNull(cashOperationResponse);
                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
                            assertFalse(cashOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("Превышена допустимая сумма снятия наличных"), cashOperationResponse.getErrors());
                        });
    }

    @Test
    void cashTransactionFailed_InsufficientFunds() {
        CashOperationRequest cashOperationRequest = new CashOperationRequest(
                "test_user1",
                CashCurrencyEnum.RUB,
                new BigDecimal("100000.0"),
                CashOperationRequest.ActionEnum.GET
        );
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/cash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cashOperationRequest)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody(CashOperationResponse.class)
                .value(
                        cashOperationResponse -> {
                            assertNotNull(cashOperationResponse);
                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
                            assertFalse(cashOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("На счете недостаточно средств"), cashOperationResponse.getErrors());
                        });
    }

    @Test
    void cashTransactionNonUnauthorized() {
        CashOperationRequest cashOperationRequest = new CashOperationRequest(
                "test_user1",
                CashCurrencyEnum.RUB,
                new BigDecimal("1000.0"),
                CashOperationRequest.ActionEnum.GET
        );
        webTestClient
                .post()
                .uri("/api/v1/cash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cashOperationRequest)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
