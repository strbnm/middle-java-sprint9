package ru.strbnm.accounts_service.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.strbnm.accounts_service.domain.*;
import ru.strbnm.accounts_service.exception.AccountNotFoundForCurrencyException;
import ru.strbnm.accounts_service.exception.UserAlreadyExistsException;
import ru.strbnm.accounts_service.exception.UserNotFoundException;
import ru.strbnm.accounts_service.mapper.UserMapper;
import ru.strbnm.accounts_service.repository.AccountRepository;
import ru.strbnm.accounts_service.repository.UserRepository;
import ru.strbnm.kafka.dto.NotificationMessage;

@Slf4j
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@EmbeddedKafka(topics = "notifications")
class UserServiceImplTest {

  @Autowired private DatabaseClient databaseClient;
  @Autowired SpringLiquibase liquibase;

  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private UserService userService;

    @Autowired
    private ConsumerFactory<String, NotificationMessage> consumerFactory;

    private Consumer<String, NotificationMessage> consumer;

  private static final String INIT_SCRIPT_PATH = "src/test/resources/scripts/INIT_STORE_RECORD.sql";
  private static final String CLEAN_SCRIPT_PATH =
      "src/test/resources/scripts/CLEAN_STORE_RECORD.sql";

  @BeforeAll
  void setupSchema() throws LiquibaseException {
    liquibase.afterPropertiesSet(); // Запускаем Liquibase вручную
    databaseClient.sql("SELECT 1").fetch().rowsUpdated().block(); // Ждем завершения

      consumer = consumerFactory.createConsumer();
      consumer.subscribe(List.of("notifications"));
  }

  @AfterAll
  void tearDown() {
      if (consumer != null) {consumer.close();}
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

  @Order(1)
  @Test
  void createUserOk_shouldReturnAccountOperationResponseWithSuccess() {
    UserRequest createUserRequestSuccess =
        new UserRequest(
            "test_user4",
            "Сидоров Иван",
            "sidorov@example.ru",
            LocalDate.parse("2000-01-01"));
    createUserRequestSuccess.setPassword("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa");

    StepVerifier.create(userService.createUser(createUserRequestSuccess))
        .assertNext(
            accountOperationResponse -> {
              assertNotNull(accountOperationResponse, "Объект не должен быть null.");
              assertEquals(
                  AccountOperationResponse.OperationStatusEnum.SUCCESS,
                  accountOperationResponse.getOperationStatus(),
                  "Статус должен быть SUCCESS");
              assertTrue(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
            })
        .verifyComplete();

    StepVerifier.create(userRepository.getUserWithRolesByLogin("test_user4"))
        .assertNext(
            userDetailResponse -> {
              assertNotNull(userDetailResponse, "Объект не должен быть null.");
              assertEquals(
                  "test_user4", userDetailResponse.getLogin(), "Логин должен быть test_user4");
              assertEquals(
                  "Сидоров Иван", userDetailResponse.getName(), "Имя должно  быть Сидоров Иван");
              assertEquals(
                  "sidorov@example.ru",
                  userDetailResponse.getEmail(),
                  "Email должен быть sidorov@example.ru");
              assertEquals(
                  LocalDate.parse("2000-01-01"),
                  userDetailResponse.getBirthdate(),
                  "Дата рождения должна быть 2000-01-01");
              assertEquals(
                  "ROLE_CLIENT",
                  userDetailResponse.getRoles().getFirst(),
                  "Роль должна быть ROLE_CLIENT");
            })
        .verifyComplete();

      // Проверяем, что в топик Kafka было отправлено сообщение
      NotificationMessage expected = NotificationMessage.builder()
              .email(createUserRequestSuccess.getEmail())
              .message("Вы успешно зарегистрированы.")
              .application("accounts-service")
              .build();
      ConsumerRecords<String, NotificationMessage> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
      NotificationMessage found = StreamSupport.stream(records.spliterator(), false)
              .map(ConsumerRecord::value)
              .filter(msg -> msg.equals(expected))
              .findFirst()
              .orElseThrow();
  }

  @Test
  void createUserInvalidFields_shouldReturnAccountOperationResponseWithFailedAndErrors() {
    UserRequest createUserRequestInvalidFields =
        new UserRequest(
            "test_user4",
            "",
            "",
            LocalDate.parse("2020-01-01"));
    createUserRequestInvalidFields.setPassword("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa");

    StepVerifier.create(userService.createUser(createUserRequestInvalidFields))
        .assertNext(
            accountOperationResponse -> {
              assertNotNull(accountOperationResponse, "Объект не должен быть null.");
              assertEquals(
                  AccountOperationResponse.OperationStatusEnum.FAILED,
                  accountOperationResponse.getOperationStatus(),
                  "Статус должен быть FAILED");
              assertFalse(
                  accountOperationResponse.getErrors().isEmpty(), "Список ошибок не должен быть пуст");
            })
        .verifyComplete();

    StepVerifier.create(userRepository.getUserWithRolesByLogin("test_user4"))
        .verifyComplete(); // Нет пользователя test_user4 в БД
  }

  @Test
  void createExistingUser_shouldReturnUserAlreadyExistsExceptionIfUserAlreadyExists() {
    UserRequest createUserRequestConflict =
        new UserRequest(
            "test_user1",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2000-01-01"));
    createUserRequestConflict.setPassword("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa");

    StepVerifier.create(userService.createUser(createUserRequestConflict))
        .verifyErrorSatisfies(
            throwable -> {
              assertInstanceOf(UserAlreadyExistsException.class, throwable);
              assertEquals(
                  "Пользователь с таким логином уже существует: test_user1",
                  throwable.getMessage());
            });
  }

  @Test
  void updateUserOk_shouldReturnAccountOperationResponseWithSuccess() {
    UserRequest updateUserRequestSuccess =
        new UserRequest(
            "test_user1",
            "Сидоров Иван",
            "sidorov@example.ru",
            LocalDate.parse("1999-01-01"));
    updateUserRequestSuccess.setPassword("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa");
    updateUserRequestSuccess.setAccounts(
        List.of(AccountCurrencyEnum.RUB, AccountCurrencyEnum.CNY, AccountCurrencyEnum.USD));

    // Счета до обновления
    StepVerifier.create(accountRepository.findUserCurrencyAccounts("test_user1"))
        .assertNext(
            account -> {
              assertEquals(AccountCurrencyEnum.CNY, account.getCurrency());
              assertEquals(0, account.getValue().compareTo(new BigDecimal("20000.0")));
              assertTrue(account.getExists());
            })
        .assertNext(
            account -> {
              assertEquals(AccountCurrencyEnum.RUB, account.getCurrency());
                assertEquals(0, account.getValue().compareTo(new BigDecimal("150000.0")));
              assertTrue(account.getExists());
            })
        .assertNext(
            account -> {
              assertEquals(AccountCurrencyEnum.USD, account.getCurrency());
              assertEquals(BigDecimal.ZERO, account.getValue());
              assertFalse(account.getExists());
            })
        .verifyComplete();

    StepVerifier.create(userService.updateUser(updateUserRequestSuccess))
        .assertNext(
            accountOperationResponse -> {
              assertNotNull(accountOperationResponse, "Объект не должен быть null.");
              assertEquals(
                  AccountOperationResponse.OperationStatusEnum.SUCCESS,
                  accountOperationResponse.getOperationStatus(),
                  "Статус должен быть SUCCESS");
              assertTrue(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
            })
        .verifyComplete();

    // После обновления
    StepVerifier.create(userRepository.getUserWithRolesByLogin("test_user1"))
        .assertNext(
            userDetailResponse -> {
              assertNotNull(userDetailResponse, "Объект не должен быть null.");
              assertEquals(
                  "test_user1", userDetailResponse.getLogin(), "Логин должен быть test_user1");
              assertEquals(
                  "Сидоров Иван", userDetailResponse.getName(), "Имя должно  быть Сидоров Иван");
              assertEquals(
                  "sidorov@example.ru",
                  userDetailResponse.getEmail(),
                  "Email должен быть sidorov@example.ru");
              assertEquals(
                  LocalDate.parse("1999-01-01"),
                  userDetailResponse.getBirthdate(),
                  "Дата рождения должна быть 1999-01-01");
              assertEquals(
                  "ROLE_CLIENT",
                  userDetailResponse.getRoles().getFirst(),
                  "Роль должна быть ROLE_CLIENT");
            })
        .verifyComplete();

      StepVerifier.create(accountRepository.findUserCurrencyAccounts("test_user1"))
              .assertNext(
                      account -> {
                          assertEquals(AccountCurrencyEnum.CNY, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("20000.0")));
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          assertEquals(AccountCurrencyEnum.RUB, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("150000.0")));
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          assertEquals(AccountCurrencyEnum.USD, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(BigDecimal.ZERO));
                          assertTrue(account.getExists());
                      })
              .verifyComplete();
  }

  @Test
  void updateUserPartInvalidFields_shouldReturnAccountOperationResponseWithFailedAndErrorsWithPartChangesInDatabase() {
    UserRequest updateUserRequestInvalidFields =
        new UserRequest(
            "test_user2",
            "",
            "",
            LocalDate.parse("1993-05-21"));
    updateUserRequestInvalidFields.setPassword("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa");
      updateUserRequestInvalidFields.setAccounts(
              List.of(AccountCurrencyEnum.RUB));

    StepVerifier.create(userService.updateUser(updateUserRequestInvalidFields))
        .assertNext(
            accountOperationResponse -> {
              assertNotNull(accountOperationResponse, "Объект не должен быть null.");
              assertEquals(
                  AccountOperationResponse.OperationStatusEnum.FAILED,
                  accountOperationResponse.getOperationStatus(),
                  "Статус должен быть FAILED");
              assertFalse(
                  accountOperationResponse.getErrors().isEmpty(), "Список ошибок не должен быть пуст");
              assertEquals(List.of(
                      "Баланс на счету CNY не равен 0",
                      "Баланс на счету USD не равен 0",
                      "Заполните поле Фамилия Имя",
                      "Заполните электронную почту"
              ), accountOperationResponse.getErrors());
            })
        .verifyComplete();

      // После обновления
      StepVerifier.create(userRepository.getUserWithRolesByLogin("test_user2"))
              .assertNext(
                      userDetailResponse -> {
                          assertNotNull(userDetailResponse, "Объект не должен быть null.");
                          assertEquals(
                                  "test_user2", userDetailResponse.getLogin(), "Логин должен быть test_user2");
                          assertEquals(
                                  "Петров Петр", userDetailResponse.getName(), "Имя должно  быть Петров Петр");
                          assertEquals(
                                  "petrov@example.ru",
                                  userDetailResponse.getEmail(),
                                  "Email должен быть petrov@example.ru");
                          assertEquals(
                                  LocalDate.parse("1993-05-21"),
                                  userDetailResponse.getBirthdate(),
                                  "Дата рождения должна быть 1993-05-21");
                          assertEquals(
                                  "ROLE_CLIENT",
                                  userDetailResponse.getRoles().getFirst(),
                                  "Роль должна быть ROLE_CLIENT");
                      })
              .verifyComplete();

      StepVerifier.create(accountRepository.findUserCurrencyAccounts("test_user2"))
              .assertNext(
                      account -> {
                          log.info("Счет CNY: {}", account);
                          assertEquals(AccountCurrencyEnum.CNY, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("12000.0")));
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          log.info("Счет RUB: {}", account);
                          assertEquals(AccountCurrencyEnum.RUB, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(BigDecimal.ZERO));
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          log.info("Счет USD: {}", account);
                          assertEquals(AccountCurrencyEnum.USD, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("1000.0")));
                          assertTrue(account.getExists());
                      })
              .verifyComplete();
  }

  @Test
  void updateNotExistingUser_shouldReturnUserNotFoundError() {
    UserRequest updateUserRequestNotFound =
        new UserRequest(
            "test_user4",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2000-01-01"));
    updateUserRequestNotFound.setPassword("$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa");

    StepVerifier.create(userService.updateUser(updateUserRequestNotFound))
        .verifyErrorSatisfies(
            throwable -> {
              assertInstanceOf(UserNotFoundException.class, throwable);
              assertEquals(
                  "Пользователь с логином test_user4 не существует",
                  throwable.getMessage());
            });
  }

  @Test
  void getUserList_shouldReturnUserListResponse() {
      StepVerifier.create(userService.getUserList())
              .assertNext(existingUser -> {
                  assertNotNull(existingUser, "Объект не должен быть null.");
                  assertEquals(
                          "test_user1", existingUser.getLogin(), "Логин должен быть test_user1");
                  assertEquals(
                          "Иванов Иван", existingUser.getName(), "Имя должно  быть Иванов Иван");
              })
              .assertNext(existingUser -> {
                  assertNotNull(existingUser, "Объект не должен быть null.");
                  assertEquals(
                          "test_user2", existingUser.getLogin(), "Логин должен быть test_user2");
                  assertEquals(
                          "Петров Петр", existingUser.getName(), "Имя должно  быть Петров Петр");
              })
              .assertNext(existingUser -> {
                  assertNotNull(existingUser, "Объект не должен быть null.");
                  assertEquals(
                          "test_user3", existingUser.getLogin(), "Логин должен быть test_user3");
                  assertEquals(
                          "Сидоров Степан", existingUser.getName(), "Имя должно  быть Сидоров Степан");
              })
              .verifyComplete();
  }

  @Test
  void getUserByLogin_shouldReturnUserDetailResponse() {
      AccountInfoRow rubAccount = new AccountInfoRow(AccountCurrencyEnum.RUB, BigDecimal.ZERO, false);
      AccountInfoRow usdAccount = new AccountInfoRow(AccountCurrencyEnum.USD, BigDecimal.ZERO, false);
      AccountInfoRow cnyAccount = new AccountInfoRow(AccountCurrencyEnum.CNY, new BigDecimal("5000.0"), true);
      StepVerifier.create(userService.getUserByLogin("test_user3"))
              .assertNext(userDetailResponse -> {
                  assertNotNull(userDetailResponse, "Объект не должен быть null.");
                  assertEquals(
                          "test_user3", userDetailResponse.getLogin(), "Логин должен быть test_user2");
                  assertEquals(
                          "Сидоров Степан", userDetailResponse.getName(), "Имя должно  быть Сидоров Степан");
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

              })
              .verifyComplete();
  }

    @Test
    void getUserByLoginNotExistingUser_shouldReturnUserNotFoundError() {
        StepVerifier.create(userService.getUserByLogin("test_user4"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(UserNotFoundException.class, throwable);
                            assertEquals(
                                    "Пользователь с логином test_user4 не существует",
                                    throwable.getMessage());
                        });
    }

  @Test
  void updateUserPasswordOk_shouldReturnAccountOperationResponseWithSuccess() {
      UserPasswordRequest userPasswordRequestSuccess = new UserPasswordRequest(
              "test_user1", "$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6"
      );
      StepVerifier.create(userService.updateUserPassword(userPasswordRequestSuccess))
              .assertNext(
                      accountOperationResponse -> {
                          assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                          assertEquals(
                                  AccountOperationResponse.OperationStatusEnum.SUCCESS,
                                  accountOperationResponse.getOperationStatus(),
                                  "Статус должен быть SUCCESS");
                          assertTrue(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
                      })
              .verifyComplete();
  }
    @Test
    void updateUserPasswordInvalidHash_shouldReturnAccountOperationResponseWithFailed() {
        UserPasswordRequest userPasswordRequestFailed = new UserPasswordRequest(
                "test_user1", "b2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6"
        );
        StepVerifier.create(userService.updateUserPassword(userPasswordRequestFailed))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.FAILED,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть FAILED");
                            assertFalse(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть не пуст");
                            assertEquals(List.of("Ошибка при сохранении изменений пароля. Операция отменена"), accountOperationResponse.getErrors());
                        })
                .verifyComplete();
    }

    @Test
    void updateUserPasswordNotExistingUser_shouldReturnUserNotFoundError() {
        UserPasswordRequest userPasswordRequestFailed = new UserPasswordRequest(
                "test_user4", "$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6"
        );
        StepVerifier.create(userService.updateUserPassword(userPasswordRequestFailed))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(UserNotFoundException.class, throwable);
                            assertEquals(
                                    "Пользователь с логином test_user4 не существует",
                                    throwable.getMessage());
                        });
    }

  @Test
  void cashOperationOk_shouldReturnAccountOperationResponseWithSuccess() {
      CashRequest cashRequestSuccess = new CashRequest(
              AccountCurrencyEnum.RUB,
              new BigDecimal("10000.0"),
              CashRequest.ActionEnum.GET
      );

      StepVerifier.create(userService.getUserByLogin("test_user1"))
                      .assertNext(userDetailResponse -> {
                          assertNotNull(userDetailResponse, "Объект не должен быть null.");
                          assertEquals(0, new BigDecimal("150000.0").compareTo(
                                  userDetailResponse.getAccounts().stream()
                                  .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                  .map(AccountInfoRow::getValue)
                                  .findFirst().get()));
                      }).verifyComplete();

      StepVerifier.create(userService.cashOperation(cashRequestSuccess, "test_user1"))
              .assertNext(
                      accountOperationResponse -> {
                          assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                          assertEquals(
                                  AccountOperationResponse.OperationStatusEnum.SUCCESS,
                                  accountOperationResponse.getOperationStatus(),
                                  "Статус должен быть SUCCESS");
                          assertTrue(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
                      })
              .verifyComplete();

      StepVerifier.create(userService.getUserByLogin("test_user1"))
              .assertNext(userDetailResponse -> {
                  assertNotNull(userDetailResponse, "Объект не должен быть null.");
                  assertEquals(0, new BigDecimal("140000.0").compareTo(
                          userDetailResponse.getAccounts().stream()
                                  .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                  .map(AccountInfoRow::getValue)
                                  .findFirst().get()));
              }).verifyComplete();
  }

    @Test
    void cashOperationWithMissingAccount_shouldReturnAccountOperationResponseWithFailed() {
        CashRequest cashRequestFailed = new CashRequest(
                AccountCurrencyEnum.USD,
                new BigDecimal("10000.0"),
                CashRequest.ActionEnum.GET
        );
        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.cashOperation(cashRequestFailed, "test_user1"))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.FAILED,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть FAILED");
                            assertFalse(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть не пуст");
                            assertEquals(List.of("У Вас отсутствует счет в выбранной валюте"), accountOperationResponse.getErrors());
                        })
                .verifyComplete();

        // Счет не появился
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();
    }

    @Test
    void cashOperationWithInsufficientFunds_shouldReturnAccountOperationResponseWithFailed() {
        CashRequest cashRequestFailed = new CashRequest(
                AccountCurrencyEnum.CNY,
                new BigDecimal("20001.0"),
                CashRequest.ActionEnum.GET
        );

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.cashOperation(cashRequestFailed, "test_user1"))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.FAILED,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть FAILED");
                            assertFalse(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть не пуст");
                            assertEquals(List.of("На счете недостаточно средств"), accountOperationResponse.getErrors());
                        })
                .verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void cashOperationNotExistingUser_shouldReturnUserNotFoundError() {
        CashRequest cashRequestFailed = new CashRequest(
                AccountCurrencyEnum.CNY,
                new BigDecimal("20001.0"),
                CashRequest.ActionEnum.GET
        );
        StepVerifier.create(userService.cashOperation(cashRequestFailed, "test_user4"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(UserNotFoundException.class, throwable);
                            assertEquals(
                                    "Пользователь с логином test_user4 не существует",
                                    throwable.getMessage());
                        });
    }

    @Test
    void transferOperationOtherOk_shouldReturnAccountOperationResponseWithSuccess() {
    TransferRequest transferRequestOtherSuccess = new TransferRequest(
            AccountCurrencyEnum.CNY,
            AccountCurrencyEnum.CNY,
            new BigDecimal("10000.0"),
            new BigDecimal("10000.0"),
            "test_user2"
    );

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.SUCCESS,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть SUCCESS");
                            assertTrue(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
                        })
                .verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("10000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("22000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void transferOperationOtherWithMissingAccountIfself_shouldReturnAccountNotFoundForCurrencyException() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.USD,
                AccountCurrencyEnum.CNY,
                new BigDecimal("1000.0"),
                new BigDecimal("10000.0"),
                "test_user2"
        );

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(AccountNotFoundForCurrencyException.class, throwable);
                            assertEquals(
                                    "У Вас отсутствует счет в выбранной валюте",
                                    throwable.getMessage());
                        });

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void transferOperationOtherWithMissingAccountOther_shouldReturnAccountNotFoundForCurrencyException() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.CNY,
                AccountCurrencyEnum.RUB,
                new BigDecimal("1000.0"),
                new BigDecimal("10000.0"),
                "test_user2"
        );

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(AccountNotFoundForCurrencyException.class, throwable);
                            assertEquals(
                                    "У клиента Петров Петр отсутствует счет в выбранной валюте",
                                    throwable.getMessage());
                        });

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }


    @Test
    void transferOperationOtherWithInsufficientFunds_shouldReturnAccountOperationResponseWithFailed() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.CNY,
                AccountCurrencyEnum.CNY,
                new BigDecimal("20001.0"),
                new BigDecimal("10000.0"),
                "test_user2"
        );

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.FAILED,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть FAILED");
                            assertFalse(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть не пуст");
                            assertEquals(List.of("На счете недостаточно средств"), accountOperationResponse.getErrors());
                        })
                .verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void transferOperationItselfOk_shouldReturnAccountOperationResponseWithSuccess() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.CNY,
                AccountCurrencyEnum.RUB,
                new BigDecimal("10000.0"),
                new BigDecimal("100000.0"),
                "test_user1"
        );

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                    assertEquals(0, new BigDecimal("150000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.SUCCESS,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть SUCCESS");
                            assertTrue(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
                        })
                .verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("10000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                    assertEquals(0, new BigDecimal("250000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void transferOperationItselfWithMissingAccount_shouldReturnAccountNotFoundForCurrencyException() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.USD,
                AccountCurrencyEnum.CNY,
                new BigDecimal("1000.0"),
                new BigDecimal("10000.0"),
                "test_user1"
        );

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(AccountNotFoundForCurrencyException.class, throwable);
                            assertEquals(
                                    "У Вас отсутствует счет в выбранной валюте",
                                    throwable.getMessage());
                        });

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void transferOperationItselfWithMissingAccountOther_shouldReturnAccountNotFoundForCurrencyException() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.CNY,
                AccountCurrencyEnum.USD,
                new BigDecimal("1000.0"),
                new BigDecimal("10000.0"),
                "test_user1"
        );

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(AccountNotFoundForCurrencyException.class, throwable);
                            assertEquals(
                                    "У Вас отсутствует счет в выбранной валюте",
                                    throwable.getMessage());
                        });

        // Отсутствует счет в USD
        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertFalse(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.USD)
                                    .map(AccountInfoRow::getExists)
                                    .findFirst().get());
                }).verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user2"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("12000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }


    @Test
    void transferOperationItselfWithInsufficientFunds_shouldReturnAccountOperationResponseWithFailed() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.RUB,
                AccountCurrencyEnum.CNY,
                new BigDecimal("150001.0"),
                new BigDecimal("10000.0"),
                "test_user1"
        );

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                    assertEquals(0, new BigDecimal("150000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.FAILED,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть FAILED");
                            assertFalse(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть не пуст");
                            assertEquals(List.of("На счете недостаточно средств"), accountOperationResponse.getErrors());
                        })
                .verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("20000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.CNY)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                    assertEquals(0, new BigDecimal("150000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void transferOperationItselfWithWithSameAccounts_shouldReturnAccountOperationResponseWithFailed() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.RUB,
                AccountCurrencyEnum.RUB,
                new BigDecimal("10000.0"),
                new BigDecimal("10000.0"),
                "test_user1"
        );

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("150000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .assertNext(
                        accountOperationResponse -> {
                            assertNotNull(accountOperationResponse, "Объект не должен быть null.");
                            assertEquals(
                                    AccountOperationResponse.OperationStatusEnum.FAILED,
                                    accountOperationResponse.getOperationStatus(),
                                    "Статус должен быть FAILED");
                            assertFalse(accountOperationResponse.getErrors().isEmpty(), "Список ошибок должен быть не пуст");
                            assertEquals(List.of("Перевести можно только между разными счетами"), accountOperationResponse.getErrors());
                        })
                .verifyComplete();

        StepVerifier.create(userService.getUserByLogin("test_user1"))
                .assertNext(userDetailResponse -> {
                    assertNotNull(userDetailResponse, "Объект не должен быть null.");
                    assertEquals(0, new BigDecimal("150000.0").compareTo(
                            userDetailResponse.getAccounts().stream()
                                    .filter(accountInfoRow -> accountInfoRow.getCurrency() == AccountCurrencyEnum.RUB)
                                    .map(AccountInfoRow::getValue)
                                    .findFirst().get()));
                }).verifyComplete();
    }

    @Test
    void transferOperationNotExistingFromUser_shouldReturnUserNotFoundError() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.RUB,
                AccountCurrencyEnum.RUB,
                new BigDecimal("10000.0"),
                new BigDecimal("10000.0"),
                "test_user1"
        );

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user4"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(UserNotFoundException.class, throwable);
                            assertEquals(
                                    "Пользователь с логином test_user4 не существует",
                                    throwable.getMessage());
                        });
    }

    @Test
    void transferOperationNotExistingToUser_shouldReturnUserNotFoundError() {
        TransferRequest transferRequestOtherSuccess = new TransferRequest(
                AccountCurrencyEnum.RUB,
                AccountCurrencyEnum.RUB,
                new BigDecimal("10000.0"),
                new BigDecimal("10000.0"),
                "test_user4"
        );

        StepVerifier.create(userService.transferOperation(transferRequestOtherSuccess, "test_user1"))
                .verifyErrorSatisfies(
                        throwable -> {
                            assertInstanceOf(UserNotFoundException.class, throwable);
                            assertEquals(
                                    "Пользователь с логином test_user4 не существует",
                                    throwable.getMessage());
                        });
    }

  @TestConfiguration
  static class TestConfig {
    @Bean
    public UserMapper userMapper() {
      return Mappers.getMapper(UserMapper.class);
    }

  }

}
