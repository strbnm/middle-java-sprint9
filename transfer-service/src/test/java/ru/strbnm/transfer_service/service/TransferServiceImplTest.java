package ru.strbnm.transfer_service.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
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
import ru.strbnm.transfer_service.config.AccountsWebClientConfig;
import ru.strbnm.transfer_service.config.BlockerWebClientConfig;
import ru.strbnm.transfer_service.config.ExchangeWebClientConfig;
import ru.strbnm.transfer_service.config.LiquibaseConfig;
import ru.strbnm.transfer_service.domain.TransferCurrencyEnum;
import ru.strbnm.transfer_service.domain.TransferOperationRequest;
import ru.strbnm.transfer_service.domain.TransferOperationResponse;
import ru.strbnm.transfer_service.entity.OutboxNotification;
import ru.strbnm.transfer_service.repository.OutboxNotificationRepository;
import ru.strbnm.transfer_service.repository.TransferTransactionInfoRepository;


@Slf4j
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DataR2dbcTest(properties = {"spring.config.name=application-test"})
@Import({AccountsWebClientConfig.class, BlockerWebClientConfig.class, ExchangeWebClientConfig.class, LiquibaseConfig.class, TransferServiceImpl.class})
@AutoConfigureStubRunner(
        ids = {
                "ru.strbnm:accounts-service:+:stubs:8082",
                "ru.strbnm:blocker-service:+:stubs:8081"
        },
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class TransferServiceImplTest {

  @Autowired private DatabaseClient databaseClient;
  @Autowired SpringLiquibase liquibase;

  @Autowired private TransferTransactionInfoRepository transferTransactionInfoRepository;
  @Autowired private OutboxNotificationRepository outboxNotificationRepository;

  @Autowired private TransferService transferService;

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
  void processTransferTransactionSuccess() {
    TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
            "test_user1",
            TransferCurrencyEnum.CNY,
            "test_user2",
            TransferCurrencyEnum.CNY,
            new BigDecimal("1000.0")
    );

    StepVerifier.create(transferService.processTransferTransaction(transferOperationRequest))
            .assertNext(
                    transferOperationResponse -> {
                      assertNotNull(transferOperationResponse, "Объект не должен быть null");
                      assertEquals(TransferOperationResponse.OperationStatusEnum.SUCCESS, transferOperationResponse.getOperationStatus());
                      assertTrue(transferOperationResponse.getErrors().isEmpty());
                    }
            ).verifyComplete();

    StepVerifier.create(transferTransactionInfoRepository.findAll())
            .assertNext(
                    cashTransactionInfo -> {
                      assertNotNull(cashTransactionInfo, "Объект не должен быть null");
                      assertEquals(1L, cashTransactionInfo.getId());
                      assertEquals("test_user1", cashTransactionInfo.getFromLogin());
                      assertEquals("test_user2", cashTransactionInfo.getToLogin());
                      assertEquals(TransferCurrencyEnum.CNY.name(), cashTransactionInfo.getFromCurrency());
                      assertEquals(TransferCurrencyEnum.CNY.name(), cashTransactionInfo.getToCurrency());
                      assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getFromAmount()));
                      assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getToAmount()));
                      assertFalse(cashTransactionInfo.isBlocked());
                      assertTrue(cashTransactionInfo.isSuccess());
                    }
            ).verifyComplete();

      StepVerifier.create(outboxNotificationRepository.findAll().collectList())
              .assertNext(outboxNotifications -> {
                  assertEquals(2, outboxNotifications.size());
                  assertTrue(
                          outboxNotifications.containsAll(List.of(
                                  OutboxNotification.builder()
                                          .id(1L)
                                          .transactionId(1L)
                                          .email("ivanov@example.ru")
                                          .message("Успешный перевод 1000.0CNY клиенту Петров Петр")
                                          .isSent(false)
                                          .build(),
                                  OutboxNotification.builder()
                                          .id(2L)
                                          .transactionId(1L)
                                          .email("petrov@example.ru")
                                          .message("Получен перевод 1000.0CNY от клиента Иванов Иван")
                                          .isSent(false)
                                          .build()
                          ))
                  );
              })
              .verifyComplete();
  }

//    @Test
//    void processTransferTransactionFailed_MissingCurrencyAccount() {
//        TransferOperationRequest cashOperationRequest = new TransferOperationRequest(
//                "test_user1",
//                CashCurrencyEnum.USD,
//                new BigDecimal("1000.0"),
//                TransferOperationRequest.ActionEnum.GET
//        );
//
//        StepVerifier.create(transferService.processTransferTransaction(cashOperationRequest))
//                .assertNext(
//                        cashOperationResponse -> {
//                            assertNotNull(cashOperationResponse, "Объект не должен быть null");
//                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
//                            assertFalse(cashOperationResponse.getErrors().isEmpty());
//                            assertEquals(List.of("У Вас отсутствует счет в выбранной валюте"), cashOperationResponse.getErrors());
//                        }
//                ).verifyComplete();
//
//        StepVerifier.create(transferTransactionInfoRepository.findAll())
//                .assertNext(
//                        cashTransactionInfo -> {
//                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
//                            assertEquals(1L, cashTransactionInfo.getId());
//                            assertEquals("test_user1", cashTransactionInfo.getLogin());
//                            assertEquals(CashCurrencyEnum.USD.name(), cashTransactionInfo.getCurrency());
//                            assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getAmount()));
//                            assertEquals(TransferOperationRequest.ActionEnum.GET.name(), cashTransactionInfo.getAction());
//                            assertFalse(cashTransactionInfo.isBlocked());
//                            assertFalse(cashTransactionInfo.isSuccess());
//                        }
//                ).verifyComplete();
//
//        StepVerifier.create(outboxNotificationRepository.findAll())
//                .assertNext(
//                        outboxNotification -> {
//                            assertNotNull(outboxNotification, "Объект не должен быть null");
//                            assertEquals(1L, outboxNotification.getTransactionId());
//                            assertEquals("ivanov@example.ru", outboxNotification.getEmail());
//                            assertEquals("Отмена операции c наличными. Список ошибок: [У Вас отсутствует счет в выбранной валюте]", outboxNotification.getMessage());
//                        }
//                ).verifyComplete();
//    }
//
//    @Test
//    void processTransferTransactionFailed_BlockerExceedLimitOperation() {
//        TransferOperationRequest cashOperationRequest = new TransferOperationRequest(
//                "test_user1",
//                CashCurrencyEnum.USD,
//                new BigDecimal("2000.0"),
//                TransferOperationRequest.ActionEnum.GET
//        );
//
//        StepVerifier.create(transferService.processTransferTransaction(cashOperationRequest))
//                .assertNext(
//                        cashOperationResponse -> {
//                            assertNotNull(cashOperationResponse, "Объект не должен быть null");
//                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
//                            assertFalse(cashOperationResponse.getErrors().isEmpty());
//                            assertEquals(List.of("Превышена допустимая сумма снятия наличных"), cashOperationResponse.getErrors());
//                        }
//                ).verifyComplete();
//
//        StepVerifier.create(transferTransactionInfoRepository.findAll())
//                .assertNext(
//                        cashTransactionInfo -> {
//                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
//                            assertEquals(1L, cashTransactionInfo.getId());
//                            assertEquals("test_user1", cashTransactionInfo.getLogin());
//                            assertEquals(CashCurrencyEnum.USD.name(), cashTransactionInfo.getCurrency());
//                            assertEquals(0, new BigDecimal("2000.0").compareTo(cashTransactionInfo.getAmount()));
//                            assertEquals(TransferOperationRequest.ActionEnum.GET.name(), cashTransactionInfo.getAction());
//                            assertTrue(cashTransactionInfo.isBlocked());
//                            assertFalse(cashTransactionInfo.isSuccess());
//                        }
//                ).verifyComplete();
//
//        StepVerifier.create(outboxNotificationRepository.findAll())
//                .assertNext(
//                        outboxNotification -> {
//                            assertNotNull(outboxNotification, "Объект не должен быть null");
//                            assertEquals(1L, outboxNotification.getTransactionId());
//                            assertEquals("ivanov@example.ru", outboxNotification.getEmail());
//                            assertEquals("Блокировка операции: Превышена допустимая сумма снятия наличных", outboxNotification.getMessage());
//                        }
//                ).verifyComplete();
//    }
//
//    @Test
//    void processTransferTransactionFailed_InsufficientFunds() {
//        TransferOperationRequest cashOperationRequest = new TransferOperationRequest(
//                "test_user1",
//                CashCurrencyEnum.RUB,
//                new BigDecimal("100000.0"),
//                TransferOperationRequest.ActionEnum.GET
//        );
//
//        StepVerifier.create(transferService.processTransferTransaction(cashOperationRequest))
//                .assertNext(
//                        cashOperationResponse -> {
//                            assertNotNull(cashOperationResponse, "Объект не должен быть null");
//                            assertEquals(CashOperationResponse.OperationStatusEnum.FAILED, cashOperationResponse.getOperationStatus());
//                            assertFalse(cashOperationResponse.getErrors().isEmpty());
//                            assertEquals(List.of("На счете недостаточно средств"), cashOperationResponse.getErrors());
//                        }
//                ).verifyComplete();
//
//        StepVerifier.create(transferTransactionInfoRepository.findAll())
//                .assertNext(
//                        cashTransactionInfo -> {
//                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
//                            assertEquals(1L, cashTransactionInfo.getId());
//                            assertEquals("test_user1", cashTransactionInfo.getLogin());
//                            assertEquals(CashCurrencyEnum.RUB.name(), cashTransactionInfo.getCurrency());
//                            assertEquals(0, new BigDecimal("100000.0").compareTo(cashTransactionInfo.getAmount()));
//                            assertEquals(TransferOperationRequest.ActionEnum.GET.name(), cashTransactionInfo.getAction());
//                            assertFalse(cashTransactionInfo.isBlocked());
//                            assertFalse(cashTransactionInfo.isSuccess());
//                        }
//                ).verifyComplete();
//
//    StepVerifier.create(outboxNotificationRepository.findAll())
//        .assertNext(
//            outboxNotification -> {
//              assertNotNull(outboxNotification, "Объект не должен быть null");
//              assertEquals(1L, outboxNotification.getTransactionId());
//              assertEquals("ivanov@example.ru", outboxNotification.getEmail());
//              assertEquals(
//                  "Отмена операции c наличными. Список ошибок: [На счете недостаточно средств]",
//                  outboxNotification.getMessage());
//            })
//        .verifyComplete();
//    }

}