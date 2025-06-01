package ru.strbnm.cash_service.service;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.strbnm.cash_service.config.AccountsWebClientConfig;
import ru.strbnm.cash_service.config.BlockerWebClientConfig;
import ru.strbnm.cash_service.config.LiquibaseConfig;
import ru.strbnm.cash_service.domain.CashCurrencyEnum;
import ru.strbnm.cash_service.domain.CashOperationRequest;
import ru.strbnm.cash_service.domain.CashOperationResponse;
import ru.strbnm.cash_service.entity.CashTransactionInfo;
import ru.strbnm.cash_service.repository.CashTransactionInfoRepository;
import ru.strbnm.cash_service.repository.OutboxNotificationRepository;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DataR2dbcTest(properties = {"spring.config.name=application-test"})
@Import({AccountsWebClientConfig.class, BlockerWebClientConfig.class, LiquibaseConfig.class, CashServiceImpl.class})
@AutoConfigureStubRunner(
        ids = {
                "ru.strbnm:accounts-service:+:stubs:8082",
                "ru.strbnm:blocker-service:+:stubs:8081"
        },
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class CashServiceImplTest {

  @Autowired private DatabaseClient databaseClient;
  @Autowired SpringLiquibase liquibase;

  @Autowired private CashTransactionInfoRepository cashTransactionInfoRepository;
  @Autowired private OutboxNotificationRepository outboxNotificationRepository;

  @Autowired private CashService cashService;

  private static final String CLEAN_SCRIPT_PATH =
      "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

  @BeforeAll
  void setupSchema() throws LiquibaseException {
    liquibase.afterPropertiesSet(); // Запускаем Liquibase вручную
    databaseClient.sql("SELECT 1").fetch().rowsUpdated().block(); // Ждем завершения
  }

  @AfterEach
  void cleanupDatabase() {
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
  void processCashTransactionSuccess() {
    CashOperationRequest cashOperationRequest = new CashOperationRequest(
            "test_user1",
            CashCurrencyEnum.RUB,
            new BigDecimal("1000.0"),
            CashOperationRequest.ActionEnum.GET
    );

    StepVerifier.create(cashService.processCashTransaction(cashOperationRequest))
            .assertNext(
                    cashOperationResponse -> {
                      assertNotNull(cashOperationResponse, "Объект не должен быть null");
                      assertEquals(CashOperationResponse.OperationStatusEnum.SUCCESS, cashOperationResponse.getOperationStatus());
                      assertTrue(cashOperationResponse.getErrors().isEmpty());
                    }
            ).verifyComplete();

    StepVerifier.create(cashTransactionInfoRepository.findAll())
            .assertNext(
                    cashTransactionInfo -> {
                      assertNotNull(cashTransactionInfo, "Объект не должен быть null");
                      assertEquals(1L, cashTransactionInfo.getId());
                      assertEquals("test_user1", cashTransactionInfo.getLogin());
                      assertEquals(CashCurrencyEnum.RUB.name(), cashTransactionInfo.getCurrency());
                      assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getAmount()));
                      assertEquals(CashOperationRequest.ActionEnum.GET.name(), cashTransactionInfo.getAction());
                      assertFalse(cashTransactionInfo.isBlocked());
                      assertTrue(cashTransactionInfo.isSuccess());
                    }
            ).verifyComplete();

    StepVerifier.create(outboxNotificationRepository.findAll())
            .assertNext(
                    outboxNotification -> {
                      assertNotNull(outboxNotification, "Объект не должен быть null");
                      assertEquals(1L, outboxNotification.getTransactionId());
                      assertEquals("ivanov@example.ru", outboxNotification.getEmail());
                      assertEquals("Успешная операция снятия наличных в размере 1000.0RUB", outboxNotification.getMessage());
                    }
            ).verifyComplete();
  }

    @Test
    void processCashTransactionFailed_MissingCurrencyAccount() {
        CashOperationRequest cashOperationRequest = new CashOperationRequest(
                "test_user1",
                CashCurrencyEnum.USD,
                new BigDecimal("1000.0"),
                CashOperationRequest.ActionEnum.GET
        );

        StepVerifier.create(cashService.processCashTransaction(cashOperationRequest))
                .assertNext(
                        cashOperationResponse -> {
                            assertNotNull(cashOperationResponse, "Объект не должен быть null");
                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
                            assertFalse(cashOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("У Вас отсутствует счет в выбранной валюте"), cashOperationResponse.getErrors());
                        }
                ).verifyComplete();

        StepVerifier.create(cashTransactionInfoRepository.findAll())
                .assertNext(
                        cashTransactionInfo -> {
                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
                            assertEquals(1L, cashTransactionInfo.getId());
                            assertEquals("test_user1", cashTransactionInfo.getLogin());
                            assertEquals(CashCurrencyEnum.USD.name(), cashTransactionInfo.getCurrency());
                            assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getAmount()));
                            assertEquals(CashOperationRequest.ActionEnum.GET.name(), cashTransactionInfo.getAction());
                            assertFalse(cashTransactionInfo.isBlocked());
                            assertFalse(cashTransactionInfo.isSuccess());
                        }
                ).verifyComplete();

        StepVerifier.create(outboxNotificationRepository.findAll())
                .assertNext(
                        outboxNotification -> {
                            assertNotNull(outboxNotification, "Объект не должен быть null");
                            assertEquals(1L, outboxNotification.getTransactionId());
                            assertEquals("ivanov@example.ru", outboxNotification.getEmail());
                            assertEquals("Отмена операции c наличными. Список ошибок: [У Вас отсутствует счет в выбранной валюте]", outboxNotification.getMessage());
                        }
                ).verifyComplete();
    }

    @Test
    void processCashTransactionFailed_BlockerExceedLimitOperation() {
        CashOperationRequest cashOperationRequest = new CashOperationRequest(
                "test_user1",
                CashCurrencyEnum.USD,
                new BigDecimal("2000.0"),
                CashOperationRequest.ActionEnum.GET
        );

        StepVerifier.create(cashService.processCashTransaction(cashOperationRequest))
                .assertNext(
                        cashOperationResponse -> {
                            assertNotNull(cashOperationResponse, "Объект не должен быть null");
                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
                            assertFalse(cashOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("Превышена допустимая сумма снятия наличных"), cashOperationResponse.getErrors());
                        }
                ).verifyComplete();

        StepVerifier.create(cashTransactionInfoRepository.findAll())
                .assertNext(
                        cashTransactionInfo -> {
                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
                            assertEquals(1L, cashTransactionInfo.getId());
                            assertEquals("test_user1", cashTransactionInfo.getLogin());
                            assertEquals(CashCurrencyEnum.USD.name(), cashTransactionInfo.getCurrency());
                            assertEquals(0, new BigDecimal("2000.0").compareTo(cashTransactionInfo.getAmount()));
                            assertEquals(CashOperationRequest.ActionEnum.GET.name(), cashTransactionInfo.getAction());
                            assertTrue(cashTransactionInfo.isBlocked());
                            assertFalse(cashTransactionInfo.isSuccess());
                        }
                ).verifyComplete();

        StepVerifier.create(outboxNotificationRepository.findAll())
                .assertNext(
                        outboxNotification -> {
                            assertNotNull(outboxNotification, "Объект не должен быть null");
                            assertEquals(1L, outboxNotification.getTransactionId());
                            assertEquals("ivanov@example.ru", outboxNotification.getEmail());
                            assertEquals("Блокировка операции: Превышена допустимая сумма снятия наличных", outboxNotification.getMessage());
                        }
                ).verifyComplete();
    }

    @Test
    void processCashTransactionFailed_InsufficientFunds() {
        CashOperationRequest cashOperationRequest = new CashOperationRequest(
                "test_user1",
                CashCurrencyEnum.RUB,
                new BigDecimal("100000.0"),
                CashOperationRequest.ActionEnum.GET
        );

        StepVerifier.create(cashService.processCashTransaction(cashOperationRequest))
                .assertNext(
                        cashOperationResponse -> {
                            assertNotNull(cashOperationResponse, "Объект не должен быть null");
                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
                            assertFalse(cashOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("На счете недостаточно средств"), cashOperationResponse.getErrors());
                        }
                ).verifyComplete();

        StepVerifier.create(cashTransactionInfoRepository.findAll())
                .assertNext(
                        cashTransactionInfo -> {
                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
                            assertEquals(1L, cashTransactionInfo.getId());
                            assertEquals("test_user1", cashTransactionInfo.getLogin());
                            assertEquals(CashCurrencyEnum.RUB.name(), cashTransactionInfo.getCurrency());
                            assertEquals(0, new BigDecimal("100000.0").compareTo(cashTransactionInfo.getAmount()));
                            assertEquals(CashOperationRequest.ActionEnum.GET.name(), cashTransactionInfo.getAction());
                            assertFalse(cashTransactionInfo.isBlocked());
                            assertFalse(cashTransactionInfo.isSuccess());
                        }
                ).verifyComplete();

    StepVerifier.create(outboxNotificationRepository.findAll())
        .assertNext(
            outboxNotification -> {
              assertNotNull(outboxNotification, "Объект не должен быть null");
              assertEquals(1L, outboxNotification.getTransactionId());
              assertEquals("ivanov@example.ru", outboxNotification.getEmail());
              assertEquals(
                  "Отмена операции c наличными. Список ошибок: [На счете недостаточно средств]",
                  outboxNotification.getMessage());
            })
        .verifyComplete();
    }

}