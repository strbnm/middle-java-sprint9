package ru.strbnm.transfer_service.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.strbnm.kafka.dto.NotificationMessage;
import ru.strbnm.transfer_service.config.AccountsWebClientConfig;
import ru.strbnm.transfer_service.config.BlockerWebClientConfig;
import ru.strbnm.transfer_service.config.ExchangeWebClientConfig;
import ru.strbnm.transfer_service.config.LiquibaseConfig;
import ru.strbnm.transfer_service.domain.TransferCurrencyEnum;
import ru.strbnm.transfer_service.domain.TransferOperationRequest;
import ru.strbnm.transfer_service.domain.TransferOperationResponse;
import ru.strbnm.transfer_service.repository.TransferTransactionInfoRepository;

@Slf4j
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties = {"spring.config.name=application-test"})
@Import({AccountsWebClientConfig.class, BlockerWebClientConfig.class, ExchangeWebClientConfig.class, LiquibaseConfig.class, TransferServiceImpl.class})
@AutoConfigureStubRunner(
        ids = {
                "ru.strbnm:accounts-service:+:stubs:7097",
                "ru.strbnm:blocker-service:+:stubs:7096",
                "ru.strbnm:exchange-service:+:stubs:7098"
        },
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "http://localhost:8081/repository/maven-public/,http://nexus:8081/repository/maven-public/"
)
@EmbeddedKafka(topics = "transfer-notifications")
class TransferServiceImplTest {

  @Autowired private DatabaseClient databaseClient;
  @Autowired SpringLiquibase liquibase;

  @Autowired private TransferTransactionInfoRepository transferTransactionInfoRepository;
  @Autowired private TransferService transferService;

    @Autowired
    private ConsumerFactory<String, NotificationMessage> consumerFactory;

    private Consumer<String, NotificationMessage> consumer;

  private static final String CLEAN_SCRIPT_PATH =
      "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

  @BeforeAll
  void setupSchema() throws LiquibaseException {
    liquibase.afterPropertiesSet(); // Запускаем Liquibase вручную
    databaseClient.sql("SELECT 1").fetch().rowsUpdated().block(); // Ждем завершения

      consumer = consumerFactory.createConsumer();
      consumer.subscribe(List.of("transfer-notifications"));
  }

    @AfterAll
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
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

      // Проверяем, что в топик Kafka было отправлено сообщение
      NotificationMessage expected1 = NotificationMessage.builder()
              .email("ivanov@example.ru")  //email для "test_user1" в контракте accounts-service
              .message("Успешный перевод 1000.0CNY клиенту Петров Петр")
              .application("transfer-service")
              .build();
      NotificationMessage expected2 = NotificationMessage.builder()
              .email("petrov@example.ru")  //email для "test_user1" в контракте accounts-service
              .message("Получен перевод 1000.0CNY от клиента Иванов Иван")
              .application("transfer-service")
              .build();
      ConsumerRecords<String, NotificationMessage> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
      List<NotificationMessage> actualMessages = StreamSupport.stream(records.spliterator(), false)
              .map(ConsumerRecord::value)
              .toList();

      assertThat(actualMessages).contains(expected1, expected2);

  }

    @Test
    void processTransferTransactionWithCurrencyExchangeSuccess() {
        TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
                "test_user1",
                TransferCurrencyEnum.RUB,
                "test_user2",
                TransferCurrencyEnum.USD,
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
                            assertEquals(TransferCurrencyEnum.RUB.name(), cashTransactionInfo.getFromCurrency());
                            assertEquals(TransferCurrencyEnum.USD.name(), cashTransactionInfo.getToCurrency());
                            assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getFromAmount()));
                            assertEquals(0, new BigDecimal("12.0").compareTo(cashTransactionInfo.getToAmount()));
                            assertFalse(cashTransactionInfo.isBlocked());
                            assertTrue(cashTransactionInfo.isSuccess());
                        }
                ).verifyComplete();
    }

    @Test
    void processTransferTransactionFailed_WhenTransferItselfBetweenSameAccounts() {
        TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
                "test_user1",
                TransferCurrencyEnum.RUB,
                "test_user1",
                TransferCurrencyEnum.RUB,
                new BigDecimal("1000.0")
        );

        StepVerifier.create(transferService.processTransferTransaction(transferOperationRequest))
                .assertNext(
                        transferOperationResponse -> {
                            assertNotNull(transferOperationResponse, "Объект не должен быть null");
                            assertEquals(TransferOperationResponse.OperationStatusEnum.FAILED, transferOperationResponse.getOperationStatus());
                            assertFalse(transferOperationResponse.getErrors().isEmpty());
                            assertEquals(List.of("Перевести можно только между разными счетами"), transferOperationResponse.getErrors());
                        }
                ).verifyComplete();

        StepVerifier.create(transferTransactionInfoRepository.findAll())
                .assertNext(
                        cashTransactionInfo -> {
                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
                            assertEquals(1L, cashTransactionInfo.getId());
                            assertEquals("test_user1", cashTransactionInfo.getFromLogin());
                            assertEquals("test_user1", cashTransactionInfo.getToLogin());
                            assertEquals(TransferCurrencyEnum.RUB.name(), cashTransactionInfo.getFromCurrency());
                            assertEquals(TransferCurrencyEnum.RUB.name(), cashTransactionInfo.getToCurrency());
                            assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getFromAmount()));
                            assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getToAmount()));
                            assertFalse(cashTransactionInfo.isBlocked());
                            assertFalse(cashTransactionInfo.isSuccess());
                        }
                ).verifyComplete();
    }

    @Test
    void processTransferTransactionFailed_WhenTransferItselfMissingAccounts() {
        TransferOperationRequest transferOperationRequest = new TransferOperationRequest(
                "test_user1",
                TransferCurrencyEnum.RUB,
                "test_user1",
                TransferCurrencyEnum.USD,
                new BigDecimal("1000.0")
        );

    StepVerifier.create(transferService.processTransferTransaction(transferOperationRequest))
        .assertNext(
            transferOperationResponse -> {
              assertNotNull(transferOperationResponse, "Объект не должен быть null");
              assertEquals(
                  TransferOperationResponse.OperationStatusEnum.FAILED,
                  transferOperationResponse.getOperationStatus());
              assertFalse(transferOperationResponse.getErrors().isEmpty());
              assertEquals(
                  List.of("У Вас отсутствует счет в выбранной валюте"),
                  transferOperationResponse.getErrors());
            })
        .verifyComplete();

        StepVerifier.create(transferTransactionInfoRepository.findAll())
                .assertNext(
                        cashTransactionInfo -> {
                            assertNotNull(cashTransactionInfo, "Объект не должен быть null");
                            assertEquals(1L, cashTransactionInfo.getId());
                            assertEquals("test_user1", cashTransactionInfo.getFromLogin());
                            assertEquals("test_user1", cashTransactionInfo.getToLogin());
                            assertEquals(TransferCurrencyEnum.RUB.name(), cashTransactionInfo.getFromCurrency());
                            assertEquals(TransferCurrencyEnum.USD.name(), cashTransactionInfo.getToCurrency());
                            assertEquals(0, new BigDecimal("1000.0").compareTo(cashTransactionInfo.getFromAmount()));
                            assertEquals(0, new BigDecimal("12.0").compareTo(cashTransactionInfo.getToAmount()));
                            assertFalse(cashTransactionInfo.isBlocked());
                            assertFalse(cashTransactionInfo.isSuccess());
                        }
                ).verifyComplete();
    }
}