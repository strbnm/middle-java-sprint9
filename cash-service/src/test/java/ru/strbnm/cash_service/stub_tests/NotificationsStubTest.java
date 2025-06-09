package ru.strbnm.cash_service.stub_tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import ru.strbnm.cash_service.client.notifications.api.NotificationsServiceApi;
import ru.strbnm.cash_service.client.notifications.domain.NotificationRequest;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.name=application-test",
                "spring.rest.notifications-service.url=http://localhost:8080"
        })
@AutoConfigureStubRunner(
    ids = "ru.strbnm:notifications-service:+:stubs:8080",
    stubsMode = StubRunnerProperties.StubsMode.REMOTE,
    repositoryRoot = "http://localhost:8081/repository/maven-public/,http://nexus:8081/repository/maven-public/"
)
class NotificationsStubTest {

    @Autowired
    private NotificationsServiceApi notificationsServiceApi;

    @Test
    void shouldSendNotificationSuccessfully() {
        NotificationRequest notificationRequest = new NotificationRequest();
        notificationRequest.setEmail("ivanov@example.ru");
        notificationRequest.setMessage("Успешная операция пополнения счета на сумму 1000.0RUB");
        notificationRequest.setApplication(NotificationRequest.ApplicationEnum.CASH_SERVICE);
        StepVerifier.create(notificationsServiceApi.notificationCreate(notificationRequest))
            .expectNext("Success") // тело ответа, как указано в контракте
            .verifyComplete();
    }
}