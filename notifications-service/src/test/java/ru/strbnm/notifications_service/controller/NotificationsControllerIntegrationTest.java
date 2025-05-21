package ru.strbnm.notifications_service.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.strbnm.notifications_service.config.TestSecurityConfig;
import ru.strbnm.notifications_service.domain.ErrorResponse;
import ru.strbnm.notifications_service.domain.NotificationRequest;
import ru.strbnm.notifications_service.repository.NotificationRepository;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=application-test"})
@AutoConfigureWebTestClient
@Import(TestSecurityConfig.class)
public class NotificationsControllerIntegrationTest {

  @Autowired private DatabaseClient databaseClient;

  @Autowired private WebTestClient webTestClient;
  @Autowired private NotificationRepository notificationRepository;

  private static final String CLEAN_SCRIPT_PATH =
      "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

  @AfterEach
  void cleanup() {
    if (databaseClient == null) {
      throw new IllegalStateException(
          "DatabaseClient не инициализирован. Проверьте конфигурацию тестов.");
    }
    executeSqlScript().block();
  }

  @Test
  void testCreateNotification_shouldReturnCreatedWithSuccess() {

    webTestClient
        .mutateWith(mockJwt())
        .post()
        .uri("/api/v1/notifications")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"email\": \"test@example.ru\", \"message\": \"Пополнение счёта RUB на сумму 300.00 руб.\", \"application\": \"cash-service\"}")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(String.class)
        .value(
            response -> {
              assert response != null;
              assert response.equals("Success");
            });

    StepVerifier.create(notificationRepository.findById(1L))
        .assertNext(
            savedNotification -> {
              assertNotNull(savedNotification, "Объект не должен быть null.");
              assertEquals(
                  "test@example.ru",
                  savedNotification.getEmail(),
                  "Значение поля email должно быть равно 'test@example.ru'.");
              assertEquals(
                  "Пополнение счёта RUB на сумму 300.00 руб.",
                  savedNotification.getMessage(),
                  "Значение поля message должно быть равно 'Пополнение счёта RUB на сумму 300.00 руб.'");
              assertEquals(
                  NotificationRequest.ApplicationEnum.fromValue("cash-service"),
                  savedNotification.getApplication(),
                  "Значение поля application должно быть равно 'cash-service'");
              assertFalse(
                  savedNotification.isSent(), "Значение флага is_sent должно быть равно False");
            })
        .verifyComplete();
  }

  @Test
  void testCreateNotification_Unauthorized_shouldReturn401() {

    webTestClient
        .post()
        .uri("/api/v1/notifications")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"email\": \"test@example.ru\", \"message\": \"Пополнение счёта RUB на сумму 300.00 руб.\", \"application\": \"cash-service\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();

    StepVerifier.create(notificationRepository.findById(1L)).expectComplete().verify();
  }

    @Test
    void testCreateNotification_BadRequest_shouldReturn400() {

        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        "{\"email\": \"test@example.ru\", \"message\": \"Пополнение счёта RUB на сумму 300.00 руб.\", \"application\": \"crash-service\"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(
                        response -> {
                            assert response != null;
                            assert Objects.requireNonNull(response.getStatusCode()) == 400;
                            assert Objects.requireNonNull(response.getMessage()).equals("Unexpected value 'crash-service'");
                        });

        StepVerifier.create(notificationRepository.findById(1L)).expectComplete().verify();
    }

  private Mono<Void> executeSqlScript() {
    try {
      String sql =
          new String(
              Files.readAllBytes(
                  Paths.get(NotificationsControllerIntegrationTest.CLEAN_SCRIPT_PATH)));
      return databaseClient.sql(sql).then();
    } catch (Exception e) {
      throw new RuntimeException(
          "Ошибка при выполнении SQL-скрипта: "
              + NotificationsControllerIntegrationTest.CLEAN_SCRIPT_PATH,
          e);
    }
  }
}
