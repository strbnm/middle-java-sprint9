package ru.strbnm.transfer_service.controller;

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
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.strbnm.transfer_service.config.TestSecurityConfig;
import ru.strbnm.transfer_service.domain.TransferCurrencyEnum;
import ru.strbnm.transfer_service.domain.TransferOperationRequest;
import ru.strbnm.transfer_service.domain.TransferOperationResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-test"})
@AutoConfigureWebTestClient
@AutoConfigureStubRunner(
    ids = {
            "ru.strbnm:blocker-service:+:stubs:7096",
            "ru.strbnm:accounts-service:+:stubs:7097",
            "ru.strbnm:exchange-service:+:stubs:7098"
    },
    stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "http://localhost:8081/repository/maven-public/,http://nexus:8081/repository/maven-public/")
@Import(TestSecurityConfig.class)
class TransferControllerIntegrationTest {

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
  void transferTransactionSuccess() {
    TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
            "test_user1",
            TransferCurrencyEnum.CNY,
            "test_user2",
            TransferCurrencyEnum.CNY,
            new BigDecimal("1000.0")
    );
    webTestClient
            .mutateWith(mockJwt())
            .post()
            .uri("/api/v1/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(transferOperationRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(TransferOperationResponse.class)
            .value(
                    transferOperationResponse -> {
                      assertNotNull(transferOperationResponse);
                      assertEquals(TransferOperationResponse.OperationStatusEnum.SUCCESS, transferOperationResponse.getOperationStatus());
                      assertTrue(transferOperationResponse.getErrors().isEmpty());
                    });
  }

  @Test
  void transferTransactionWithCurrencyExchangeSuccess() {
    TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
            "test_user1",
            TransferCurrencyEnum.RUB,
            "test_user2",
            TransferCurrencyEnum.USD,
            new BigDecimal("1000.0")
    );
    webTestClient
            .mutateWith(mockJwt())
            .post()
            .uri("/api/v1/transfer")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(transferOperationRequest)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(TransferOperationResponse.class)
            .value(
                    transferOperationResponse -> {
                      assertNotNull(transferOperationResponse);
                      assertEquals(TransferOperationResponse.OperationStatusEnum.SUCCESS, transferOperationResponse.getOperationStatus());
                      assertTrue(transferOperationResponse.getErrors().isEmpty());
                    });
  }

    @Test
    void transferTransactionFailed_WhenTransferItselfBetweenSameAccounts() {
        TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
                "test_user1",
                TransferCurrencyEnum.RUB,
                "test_user1",
                TransferCurrencyEnum.RUB,
                new BigDecimal("1000.0")
        );
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transferOperationRequest)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody(TransferOperationResponse.class)
                .value(
                        transferOperationResponse -> {
                            assertNotNull(transferOperationResponse);
                            assertEquals(TransferOperationResponse.OperationStatusEnum.FAILED, transferOperationResponse.getOperationStatus());
                            assertFalse(transferOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("Перевести можно только между разными счетами"), transferOperationResponse.getErrors());
                        });
    }

    @Test
    void transferTransactionFailed_WhenTransferItselfMissingAccounts() {
        TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
                "test_user1",
                TransferCurrencyEnum.RUB,
                "test_user1",
                TransferCurrencyEnum.USD,
                new BigDecimal("1000.0")
        );
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(transferOperationRequest)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody(TransferOperationResponse.class)
                .value(
                        transferOperationResponse -> {
                            assertNotNull(transferOperationResponse);
                            assertEquals(TransferOperationResponse.OperationStatusEnum.FAILED, transferOperationResponse.getOperationStatus());
                            assertFalse(transferOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("У Вас отсутствует счет в выбранной валюте"), transferOperationResponse.getErrors());
                        });
    }
}
