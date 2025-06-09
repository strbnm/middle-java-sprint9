package ru.strbnm.cash_service;

import static org.mockito.Mockito.*;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.strbnm.cash_service.config.ContractTestSecurityConfig;
import ru.strbnm.cash_service.entity.CashTransactionInfo;
import ru.strbnm.cash_service.entity.OutboxNotification;
import ru.strbnm.cash_service.repository.CashTransactionInfoRepository;
import ru.strbnm.cash_service.repository.OutboxNotificationRepository;

@ActiveProfiles("contracts")
@Import(ContractTestSecurityConfig.class)
@AutoConfigureStubRunner(
        ids = {
                "ru.strbnm:accounts-service:+:stubs:8082",
                "ru.strbnm:blocker-service:+:stubs:8083"
        },
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
        repositoryRoot = "http://localhost:8081/repository/maven-public/,http://nexus:8081/repository/maven-public/"
)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-contracts"})
public abstract class BaseContractTest {

  @Autowired protected WebTestClient webTestClient;
  @MockitoBean private CashTransactionInfoRepository cashTransactionInfoRepository;
  @MockitoBean private OutboxNotificationRepository outboxNotificationRepository;


  @BeforeEach
  void setup() {
    RestAssuredWebTestClient.webTestClient(webTestClient);

    when(cashTransactionInfoRepository.save(any()))
            .thenAnswer(invocation -> {
              CashTransactionInfo arg = invocation.getArgument(0);
              if (arg.getId() == null) arg.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
              return Mono.just(arg);
            });
    when(outboxNotificationRepository.save(any()))
            .thenAnswer(invocation -> {
              OutboxNotification arg = invocation.getArgument(0);
              if (arg.getId() == null) arg.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
              return Mono.just(arg);
            });
  }
}
