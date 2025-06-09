package ru.strbnm.exchange_generator.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=application-test",
                "spring.rest.exchange-service.url=http://localhost:8080"
        })
@AutoConfigureStubRunner(
    ids = "ru.strbnm:exchange-service:+:stubs:8080",
    stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "http://localhost:8081/repository/maven-public/,http://nexus:8081/repository/maven-public/"
)
class ExchangeGeneratorStubTest {

    @Autowired
    private ExchangeGenerator exchangeGenerator;

    @Test
    void shouldSendRatesSuccessfully() {
        StepVerifier.create(exchangeGenerator.generateAndSendRates())
            .expectNext("Success") // тело ответа, как указано в контракте
            .verifyComplete();
    }
}