package ru.strbnm.exchange_service.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.strbnm.exchange_service.config.LiquibaseConfig;
import ru.strbnm.exchange_service.domain.ExchangeRateRequest;
import ru.strbnm.exchange_service.domain.Rate;
import ru.strbnm.exchange_service.repository.ExchangeRateRepository;

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DataR2dbcTest(properties = {"spring.config.name=application-test"})
@Import({LiquibaseConfig.class, ExchangeServiceImpl.class})
class ExchangeServiceImplTest {

  @Autowired private DatabaseClient databaseClient;
  @Autowired SpringLiquibase liquibase;

  @Autowired private ExchangeRateRepository exchangeRateRepository;

  @Autowired private ExchangeService exchangeService;

  private static final String INIT_SCRIPT_PATH = "src/test/resources/scripts/INIT_STORE_RECORD.sql";
  private static final String CLEAN_SCRIPT_PATH =
      "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

  @BeforeAll
  void setupSchema() throws LiquibaseException {
    liquibase.afterPropertiesSet(); // Запускаем Liquibase вручную
    databaseClient.sql("SELECT 1").fetch().rowsUpdated().block(); // Ждем завершения
  }

  @BeforeEach
  void setupDatabase() {
    if (databaseClient == null) {
      throw new IllegalStateException(
          "DatabaseClient не инициализирован. Проверьте конфигурацию тестов.");
    }
    executeSqlScript(INIT_SCRIPT_PATH).block();
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
  @DisplayName("Конвертация из базовой валюты (RUB -> USD)")
  void testFromBaseCurrency() {
    BigDecimal amount = new BigDecimal("100");

    Mono<BigDecimal> result = exchangeService.convert("RUB", "USD", amount);

    StepVerifier.create(result)
        .expectNext(new BigDecimal("1.2000")) // 100 * 0.012
        .verifyComplete();
  }

  @Test
  @DisplayName("Конвертация в базовую валюту (USD -> RUB)")
  void testToBaseCurrency() {
    BigDecimal amount = new BigDecimal("1");

    Mono<BigDecimal> result = exchangeService.convert("USD", "RUB", amount);

    StepVerifier.create(result)
        .expectNext(new BigDecimal("83.3333")) // 1 / 0.012
        .verifyComplete();
  }

  @Test
  @DisplayName("Конвертация через промежуточную валюту (USD -> CNY)")
  void testFromIntermediateCurrency() {
    BigDecimal amount = new BigDecimal("1");

    Mono<BigDecimal> result = exchangeService.convert("USD", "CNY", amount);

    StepVerifier.create(result)
        .expectNext(new BigDecimal("9.1667")) // 1 / 0.012 * 0.11
        .verifyComplete();
  }

  @Test
  @DisplayName("Конвертация одной и той же валюты (CNY -> CNY)")
  void testSameCurrency() {
    BigDecimal amount = new BigDecimal("100");

    Mono<BigDecimal> result = exchangeService.convert("CNY", "CNY", amount);

    StepVerifier.create(result).expectNext(amount).verifyComplete();
  }

  @Test
  @DisplayName("Сохранение новых курсов (saveRates)")
  void testSaveRates() {
    ExchangeRateRequest request =
        ExchangeRateRequest.builder()
            .timestamp(1748000000L)
            .rates(
                List.of(
                    new Rate("Рубль", "RUB", new BigDecimal("1")),
                    new Rate("Доллар", "USD", new BigDecimal("0.015")),
                    new Rate("Юань", "CNY", new BigDecimal("0.13"))))
            .build();

    Mono<Void> result = exchangeService.saveRates(request);

    StepVerifier.create(result).verifyComplete();

    // Проверяем, что новые курсы добавились
    StepVerifier.create(exchangeRateRepository.findByCurrencyCode("USD"))
        .expectNextMatches(rate -> rate.getRateToRub().equals(new BigDecimal("0.01500000")))
        .verifyComplete();
  }

  @Test
  @DisplayName("Получение всех актуальных курсов (getRates)")
  void testGetRates() {
    Flux<Rate> result = exchangeService.getRates();

    StepVerifier.create(result)
        .expectNextCount(3) // по 1 курсу на RUB, USD, CNY (последние)
        .verifyComplete();
  }
}
