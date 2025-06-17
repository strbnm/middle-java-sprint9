package ru.strbnm.transfer_service;

import static org.mockito.Mockito.*;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.strbnm.transfer_service.config.ContractTestSecurityConfig;
import ru.strbnm.transfer_service.entity.TransferTransactionInfo;
import ru.strbnm.transfer_service.repository.TransferTransactionInfoRepository;

@ActiveProfiles("contracts")
@Import(ContractTestSecurityConfig.class)
@AutoConfigureStubRunner(
        ids = {
                "ru.strbnm:accounts-service:+:stubs:7093",
                "ru.strbnm:blocker-service:+:stubs:7092",
                "ru.strbnm:exchange-service:+:stubs:7094"
        },
        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "http://localhost:8081/repository/maven-public/,http://nexus:8081/repository/maven-public/"
)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-contracts"})
@EmbeddedKafka(topics = "transfer-notifications")
public abstract class BaseContractTest {

  @Autowired protected WebTestClient webTestClient;
  @MockitoBean private TransferTransactionInfoRepository transferTransactionInfoRepository;


  @BeforeEach
  void setup() {
    RestAssuredWebTestClient.webTestClient(webTestClient);

    when(transferTransactionInfoRepository.save(any()))
            .thenAnswer(invocation -> {
              TransferTransactionInfo arg = invocation.getArgument(0);
              if (arg.getId() == null) arg.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
              return Mono.just(arg);
            });
  }
}
