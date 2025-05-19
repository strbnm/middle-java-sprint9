package ru.strbnm.notifications_service.contractTest;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.strbnm.notifications_service.contractTest.config.ContractTestSecurityConfig;

@ActiveProfiles("contracts")
@Import(ContractTestSecurityConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseContractTest {

  @Autowired protected WebTestClient webTestClient;

  @BeforeEach
  public void setup() {
    RestAssuredWebTestClient.webTestClient(webTestClient);
  }
}
