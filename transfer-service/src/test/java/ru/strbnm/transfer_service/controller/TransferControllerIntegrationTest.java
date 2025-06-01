package ru.strbnm.transfer_service.controller;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.strbnm.transfer_service.config.TestSecurityConfig;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-test"})
@AutoConfigureWebTestClient
@AutoConfigureStubRunner(
    ids = {
            "ru.strbnm:blocker-service:+:stubs:8081",
            "ru.strbnm:accounts-service:+:stubs:8082",
            "ru.strbnm:exchange-service:+:stubs:8083"
    },
    stubsMode = StubRunnerProperties.StubsMode.LOCAL)
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

//  @Test
//  void cashTransactionSuccess() {
//    CashOperationRequest cashOperationRequest = new CashOperationRequest(
//            "test_user1",
//            CashCurrencyEnum.RUB,
//            new BigDecimal("1000.0"),
//            CashOperationRequest.ActionEnum.GET
//    );
//    webTestClient
//            .mutateWith(mockJwt())
//            .post()
//            .uri("/api/v1/cash")
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(cashOperationRequest)
//            .exchange()
//            .expectStatus()
//            .isOk()
//            .expectBody(CashOperationResponse.class)
//            .value(
//                    cashOperationResponse -> {
//                      assertNotNull(cashOperationResponse);
//                      assertEquals(CashOperationResponse.OperationStatusEnum.SUCCESS, cashOperationResponse.getOperationStatus());
//                      assertTrue(cashOperationResponse.getErrors().isEmpty());
//                    });
//  }
//
//  @Test
//  void cashTransactionFailed_MissingCurrencyAccount() {
//    CashOperationRequest cashOperationRequest = new CashOperationRequest(
//            "test_user1",
//            CashCurrencyEnum.USD,
//            new BigDecimal("1000.0"),
//            CashOperationRequest.ActionEnum.GET
//    );
//    webTestClient
//            .mutateWith(mockJwt())
//            .post()
//            .uri("/api/v1/cash")
//            .contentType(MediaType.APPLICATION_JSON)
//            .bodyValue(cashOperationRequest)
//            .exchange()
//            .expectStatus()
//            .isEqualTo(422)
//            .expectBody(CashOperationResponse.class)
//            .value(
//                    cashOperationResponse -> {
//                      assertNotNull(cashOperationResponse);
//                      assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
//                      assertFalse(cashOperationResponse.getErrors().isEmpty());
//                      assertEquals(List.of("У Вас отсутствует счет в выбранной валюте"), cashOperationResponse.getErrors());
//                    });
//  }
//
//    @Test
//    void cashTransactionFailed_BlockerExceedLimitOperation() {
//        CashOperationRequest cashOperationRequest = new CashOperationRequest(
//                "test_user1",
//                CashCurrencyEnum.USD,
//                new BigDecimal("2000.0"),
//                CashOperationRequest.ActionEnum.GET
//        );
//        webTestClient
//                .mutateWith(mockJwt())
//                .post()
//                .uri("/api/v1/cash")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(cashOperationRequest)
//                .exchange()
//                .expectStatus()
//                .isEqualTo(422)
//                .expectBody(CashOperationResponse.class)
//                .value(
//                        cashOperationResponse -> {
//                            assertNotNull(cashOperationResponse);
//                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
//                            assertFalse(cashOperationResponse.getErrors().isEmpty());
//                            assertEquals(List.of("Превышена допустимая сумма снятия наличных"), cashOperationResponse.getErrors());
//                        });
//    }
//
//    @Test
//    void cashTransactionFailed_InsufficientFunds() {
//        CashOperationRequest cashOperationRequest = new CashOperationRequest(
//                "test_user1",
//                CashCurrencyEnum.RUB,
//                new BigDecimal("100000.0"),
//                CashOperationRequest.ActionEnum.GET
//        );
//        webTestClient
//                .mutateWith(mockJwt())
//                .post()
//                .uri("/api/v1/cash")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(cashOperationRequest)
//                .exchange()
//                .expectStatus()
//                .isEqualTo(422)
//                .expectBody(CashOperationResponse.class)
//                .value(
//                        cashOperationResponse -> {
//                            assertNotNull(cashOperationResponse);
//                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
//                            assertFalse(cashOperationResponse.getErrors().isEmpty());
//                            assertEquals(List.of("На счете недостаточно средств"), cashOperationResponse.getErrors());
//                        });
//    }
//
//    @Test
//    void cashTransactionNonUnauthorized() {
//        CashOperationRequest cashOperationRequest = new CashOperationRequest(
//                "test_user1",
//                CashCurrencyEnum.RUB,
//                new BigDecimal("1000.0"),
//                CashOperationRequest.ActionEnum.GET
//        );
//        webTestClient
//                .post()
//                .uri("/api/v1/cash")
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(cashOperationRequest)
//                .exchange()
//                .expectStatus()
//                .isUnauthorized();
//    }
}
