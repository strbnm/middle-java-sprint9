package ru.strbnm.accounts_service.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import ru.strbnm.accounts_service.config.TestSecurityConfig;
import ru.strbnm.accounts_service.domain.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=application-test"})
@AutoConfigureWebTestClient
@Import(TestSecurityConfig.class)
class AccountsControllerIntegrationTest {

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired private WebTestClient webTestClient;


    private static final String INIT_SCRIPT_PATH = "src/test/resources/scripts/INIT_STORE_RECORD.sql";
    private static final String CLEAN_SCRIPT_PATH =
            "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

    @BeforeEach
    void setupDatabase() {
        if (databaseClient == null) {
            throw new IllegalStateException(
                    "DatabaseClient не инициализирован. Проверьте конфигурацию тестов.");
        }
        executeSqlScript(INIT_SCRIPT_PATH).block();
    }

    @AfterEach
    void cleanup() {
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
    void cashTransactionSuccess() {
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users/test_user1/cash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currency\": \"RUB\", \"amount\": 10000.0, \"action\": \"GET\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(OperationResponse.OperationStatusEnum.SUCCESS, operationResponse.getOperationStatus());
                            assertTrue(operationResponse.getErrors().isEmpty());
                        });
        // Сумма на счете на 10000 руб. меньше
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals(0, new BigDecimal("140000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.RUB)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                        });
      }

    @Test
    void cashTransactionFailed() {
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users/test_user1/cash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currency\": \"RUB\", \"amount\": 150001.0, \"action\": \"GET\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(OperationResponse.OperationStatusEnum.FAILED, operationResponse.getOperationStatus());
                            assertFalse(operationResponse.getErrors().isEmpty());
                            assertEquals(List.of("На счете недостаточно средств"), operationResponse.getErrors());
                        });

        // Сумма на счете не изменилась
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals(0, new BigDecimal("150000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.RUB)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                        });
    }

    @Test
    void cashTransactionUserNotFound() {
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users/test_user4/cash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currency\": \"RUB\", \"amount\": 150001.0, \"action\": \"GET\"}")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(ErrorResponse.class)
                .value(
                        errorResponse -> {
                            assertNotNull(errorResponse);
                            assertEquals(404, errorResponse.getStatusCode());
                            assertEquals(errorResponse.getMessage(), "Пользователь с логином test_user4 не существует");
                        });
    }

    @Test
    void cashTransactionUnauthorized() {
        webTestClient
                .post()
                .uri("/api/v1/users/test_user4/cash")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currency\": \"RUB\", \"amount\": 150001.0, \"action\": \"GET\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void createUserSuccess() {
        UserRequest createUserRequestSuccess =
                new UserRequest(
                        "test_user4",
                        "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                        "Сидоров Иван",
                        "sidorov@example.ru",
                        LocalDate.parse("2000-01-01"));
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createUserRequestSuccess)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(
                                    OperationResponse.OperationStatusEnum.SUCCESS,
                                    operationResponse.getOperationStatus());
                            assertTrue(operationResponse.getErrors().isEmpty());
                        });

        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user4")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals("Сидоров Иван", userDetailResponse.getName());
                            assertEquals("sidorov@example.ru", userDetailResponse.getEmail());
                        });
      }

    @Test
    void createUserAlreadyExists() {
        UserRequest createUserRequestSuccess =
                new UserRequest(
                        "test_user3",
                        "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                        "Сидоров Иван",
                        "sidorov@example.ru",
                        LocalDate.parse("2000-01-01"));
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createUserRequestSuccess)
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody(ErrorResponse.class)
                .value(
                        errorResponse -> {
                            assertNotNull(errorResponse);
                            assertEquals(409, errorResponse.getStatusCode());
                            assertEquals("Пользователь с таким логином уже существует: test_user3", errorResponse.getMessage());
                        });
    }

    @Test
    void getUser() {
        AccountInfoRow rubAccount = new AccountInfoRow(CurrencyEnum.RUB, BigDecimal.ZERO, false);
        AccountInfoRow usdAccount = new AccountInfoRow(CurrencyEnum.USD, BigDecimal.ZERO, false);
        AccountInfoRow cnyAccount = new AccountInfoRow(CurrencyEnum.CNY, new BigDecimal("5000.0"), true);
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertNotNull(userDetailResponse, "Объект не должен быть null.");
                            assertEquals(
                                    "test_user3", userDetailResponse.getLogin(), "Логин должен быть test_user4");
                            assertEquals(
                                    "Сидоров Степан", userDetailResponse.getName(), "Имя должно  быть Сидоров Иван");
                            assertEquals(
                                    "sidorov@example.ru",
                                    userDetailResponse.getEmail(),
                                    "Email должен быть sidorov@example.ru");
                            assertEquals(
                                    LocalDate.parse("1980-05-21"),
                                    userDetailResponse.getBirthdate(),
                                    "Дата рождения должна быть 1980-05-21");
                            assertEquals(
                                    "ROLE_CLIENT",
                                    userDetailResponse.getRoles().getFirst(),
                                    "Роль должна быть ROLE_CLIENT");
                            assertThat(userDetailResponse.getAccounts())
                                    .usingRecursiveComparison()
                                    .withComparatorForType((a, b) -> ((BigDecimal) a).compareTo((BigDecimal) b), BigDecimal.class)
                                    .ignoringCollectionOrder()
                                    .isEqualTo(List.of(cnyAccount, rubAccount, usdAccount));
                        });
      }

    @Test
    void getUserNotFound() {
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user4")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(ErrorResponse.class)
                .value(
                        errorResponse -> {
                            assertNotNull(errorResponse);
                            assertEquals(404, errorResponse.getStatusCode());
                            assertEquals("Пользователь с логином test_user4 не существует", errorResponse.getMessage());
                        });
    }

    @Test
    void getUserList() {
        List<UserListResponseInner> expected = List.of(
                new UserListResponseInner("test_user1", "Иванов Иван"),
                new UserListResponseInner("test_user2", "Петров Петр"),
                new UserListResponseInner("test_user3", "Сидоров Степан")
        );
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(UserListResponseInner.class)
                .value(actual -> assertThat(actual).containsExactlyElementsOf(expected));
      }

    @Test
    void transferTransactionOtherSuccess() {
    webTestClient
        .mutateWith(mockJwt())
        .post()
        .uri("/api/v1/users/test_user1/transfer")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"fromCurrency\": \"CNY\", \"toCurrency\": \"CNY\",\"fromAmount\": 15000.0, \"toAmount\": 15000.0,\"toLogin\": \"test_user2\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(OperationResponse.class)
        .value(
            operationResponse -> {
              assertNotNull(operationResponse);
              assertEquals(
                  OperationResponse.OperationStatusEnum.SUCCESS,
                  operationResponse.getOperationStatus());
              assertTrue(operationResponse.getErrors().isEmpty());
            });

        // Остаток на счете 5000 юаней
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals(0, new BigDecimal("5000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.CNY)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                        });

        // Остаток на счете 27000 юаней
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user2")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals(0, new BigDecimal("27000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.CNY)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                        });
    }

    @Test
    void transferTransactionItselfSuccess() {
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users/test_user1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        "{\"fromCurrency\": \"RUB\", \"toCurrency\": \"CNY\",\"fromAmount\": 100000.0, \"toAmount\": 10000.0,\"toLogin\": \"test_user1\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(
                                    OperationResponse.OperationStatusEnum.SUCCESS,
                                    operationResponse.getOperationStatus());
                            assertTrue(operationResponse.getErrors().isEmpty());
                        });

        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals(0, new BigDecimal("50000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.RUB)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                            assertEquals(0, new BigDecimal("30000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.CNY)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                        });
    }

    @Test
    void transferTransactionFailed_WithMissingItselfAccount() {
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users/test_user1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        "{\"fromCurrency\": \"USD\", \"toCurrency\": \"CNY\",\"fromAmount\": 1000.0, \"toAmount\": 10000.0,\"toLogin\": \"test_user2\"}")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(ErrorResponse.class)
                .value(
                        errorResponse -> {
                            assertNotNull(errorResponse);
                            assertEquals(404, errorResponse.getStatusCode());
                            assertEquals(errorResponse.getMessage(), "У Вас отсутствует счет в выбранной валюте");
                        });

        // Остаток на счете 12000 юаней (без изменения)
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user2")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals(0, new BigDecimal("12000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.CNY)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                        });
    }

    @Test
    void transferTransactionFailed_WithMissingOtherAccount() {
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/v1/users/test_user1/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        "{\"fromCurrency\": \"RUB\", \"toCurrency\": \"RUB\",\"fromAmount\": 10000.0, \"toAmount\": 10000.0,\"toLogin\": \"test_user2\"}")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(ErrorResponse.class)
                .value(
                        errorResponse -> {
                            assertNotNull(errorResponse);
                            assertEquals(404, errorResponse.getStatusCode());
                            assertEquals(errorResponse.getMessage(), "У клиента Петров Петр отсутствует счет в выбранной валюте");
                        });

        // Остаток на счете 150000 руб. (без изменения)
        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals(0, new BigDecimal("150000.0").compareTo(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.RUB)
                                            .map(AccountInfoRow::getValue)
                                            .findFirst().get()));
                        });
    }

  @Test
  void transferTransactionUnauthorized() {
    webTestClient
        .post()
        .uri("/api/v1/users/test_user1/transfer")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"fromCurrency\": \"RUB\", \"toCurrency\": \"RUB\",\"fromAmount\": 10000.0, \"toAmount\": 10000.0,\"toLogin\": \"test_user2\"}")
        .exchange()
        .expectStatus()
        .isUnauthorized();

    }

    @Test
    void updateUserSuccess() {
        UserRequest updateUserRequestSuccess =
                new UserRequest(
                        "test_user3",
                        "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                        "Сидоров Иван",
                        "sidorov@example.ru",
                        LocalDate.parse("2000-01-01"));
        updateUserRequestSuccess.setAccounts(List.of(CurrencyEnum.CNY, CurrencyEnum.RUB));
        webTestClient
                .mutateWith(mockJwt())
                .put()
                .uri("/api/v1/users/test_user3")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateUserRequestSuccess)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(
                                    OperationResponse.OperationStatusEnum.SUCCESS,
                                    operationResponse.getOperationStatus());
                            assertTrue(operationResponse.getErrors().isEmpty());
                        });

        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals("Сидоров Иван", userDetailResponse.getName());
                            assertEquals("sidorov@example.ru", userDetailResponse.getEmail());
                            assertTrue(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.RUB)
                                            .map(AccountInfoRow::getExists)
                                            .findFirst().get());
                        });
    }

    @Test
    void updateUserFailed() {
        UserRequest updateUserRequestSuccess =
                new UserRequest(
                        "test_user3",
                        "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                        "Сидоров Иван",
                        "sidorov@example.ru",
                        LocalDate.parse("2000-01-01"));
        updateUserRequestSuccess.setAccounts(List.of(CurrencyEnum.RUB));
        webTestClient
                .mutateWith(mockJwt())
                .put()
                .uri("/api/v1/users/test_user3")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateUserRequestSuccess)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(
                                    OperationResponse.OperationStatusEnum.FAILED,
                                    operationResponse.getOperationStatus());
                            assertFalse(operationResponse.getErrors().isEmpty());
                            assertEquals(List.of("Баланс на счету CNY не равен 0"), operationResponse.getErrors());
                        });

        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals("Сидоров Иван", userDetailResponse.getName());
                            assertEquals("sidorov@example.ru", userDetailResponse.getEmail());
                            assertTrue(
                                    userDetailResponse.getAccounts().stream()
                                            .filter(accountInfoRow -> accountInfoRow.getCurrency() == CurrencyEnum.RUB)
                                            .map(AccountInfoRow::getExists)
                                            .findFirst().get());
                        });
    }

    @Test
    void updateUserPasswordSuccess() {
        UserPasswordRequest userPasswordRequestSuccess = new UserPasswordRequest(
                "test_user1", "$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6"
        );
        webTestClient
                .mutateWith(mockJwt())
                .patch()
                .uri("/api/v1/users/test_user3/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(userPasswordRequestSuccess)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(
                                    OperationResponse.OperationStatusEnum.SUCCESS,
                                    operationResponse.getOperationStatus());
                            assertTrue(operationResponse.getErrors().isEmpty());
                        });

        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals("$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6", userDetailResponse.getPassword());
                        });
      }

    @Test
    void updateUserPasswordFailed() {
        UserPasswordRequest userPasswordRequestFailed = new UserPasswordRequest(
                "test_user1", "b2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6"
        );
        webTestClient
                .mutateWith(mockJwt())
                .patch()
                .uri("/api/v1/users/test_user3/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(userPasswordRequestFailed)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody(OperationResponse.class)
                .value(
                        operationResponse -> {
                            assertNotNull(operationResponse);
                            assertEquals(
                                    OperationResponse.OperationStatusEnum.FAILED,
                                    operationResponse.getOperationStatus());
                            assertFalse(operationResponse.getErrors().isEmpty());
                            assertEquals(List.of("Ошибка при сохранении изменений пароля. Операция отменена"), operationResponse.getErrors());
                        });

        webTestClient
                .mutateWith(mockJwt())
                .get()
                .uri("/api/v1/users/test_user1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(UserDetailResponse.class)
                .value(
                        userDetailResponse -> {
                            assertNotNull(userDetailResponse);
                            assertEquals("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa", userDetailResponse.getPassword());
                        });
    }
}