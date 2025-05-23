package ru.strbnm.exchange_service;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_service.config.ContractTestSecurityConfig;
import ru.strbnm.exchange_service.domain.ExchangeRateRequest;
import ru.strbnm.exchange_service.domain.Rate;
import ru.strbnm.exchange_service.service.ExchangeService;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

@ActiveProfiles("contracts")
@Import(ContractTestSecurityConfig.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-contracts"})
public abstract class BaseContractTest {

  @Autowired protected WebTestClient webTestClient;
  @Autowired private DatabaseClient databaseClient;

  @MockitoBean private ExchangeService exchangeService;

  @BeforeEach
  public void setup() {
    RestAssuredWebTestClient.webTestClient(webTestClient);
    when(exchangeService.convert("RUB", "USD", BigDecimal.valueOf(1000.0))).thenReturn(Mono.just(BigDecimal.valueOf(12)));
    when(exchangeService.saveRates(any(ExchangeRateRequest.class))).thenReturn(Mono.empty());
    when(exchangeService.getRates()).thenReturn(Flux.just(
            Rate.builder()
                    .title("Юань")
                    .name("CNY")
                    .value(BigDecimal.valueOf(0.11))
                    .build(),
            Rate.builder()
                    .title("Российский рубль")
                    .name("RUB")
                    .value(BigDecimal.ONE)
                    .build(),
            Rate.builder()
                    .title("Американский доллар")
                    .name("USD")
                    .value(BigDecimal.valueOf(0.012))
                    .build()
    ));
  }

  @AfterEach
  void cleanup() {

  }

}
