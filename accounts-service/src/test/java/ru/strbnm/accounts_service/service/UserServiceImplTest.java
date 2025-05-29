package ru.strbnm.accounts_service.service;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.strbnm.accounts_service.config.LiquibaseConfig;
import ru.strbnm.accounts_service.domain.AccountInfoRow;
import ru.strbnm.accounts_service.domain.CurrencyEnum;
import ru.strbnm.accounts_service.domain.OperationResponse;
import ru.strbnm.accounts_service.domain.UserRequest;
import ru.strbnm.accounts_service.entity.Account;
import ru.strbnm.accounts_service.exception.UserAlreadyExistsException;
import ru.strbnm.accounts_service.exception.UserNotFoundException;
import ru.strbnm.accounts_service.mapper.UserMapper;
import ru.strbnm.accounts_service.repository.AccountRepository;
import ru.strbnm.accounts_service.repository.OutboxNotificationRepository;
import ru.strbnm.accounts_service.repository.UserRepository;

@Slf4j
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DataR2dbcTest(properties = {"spring.config.name=application-test"})
@Import({LiquibaseConfig.class, UserServiceImpl.class, UserServiceImplTest.TestConfig.class})
class UserServiceImplTest {

  @Autowired private DatabaseClient databaseClient;
  @Autowired SpringLiquibase liquibase;

  @Autowired private UserRepository userRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private OutboxNotificationRepository outboxNotificationRepository;

  @Autowired private UserService userService;

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
  void createUserOk_shouldReturnOperationResponseWithSuccess() {
    UserRequest createUserRequestSuccess =
        new UserRequest(
            "test_user4",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Сидоров Иван",
            "sidorov@example.ru",
            LocalDate.parse("2000-01-01"));

    StepVerifier.create(userService.createUser(createUserRequestSuccess))
        .assertNext(
            operationResponse -> {
              assertNotNull(operationResponse, "Объект не должен быть null.");
              assertEquals(
                  OperationResponse.OperationStatusEnum.SUCCESS,
                  operationResponse.getOperationStatus(),
                  "Статус должен быть SUCCESS");
              assertTrue(operationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
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
  }

  @Test
  void createUserInvalidFields_shouldReturnOperationResponseWithFailedAndErrors() {
    UserRequest createUserRequestInvalidFields =
        new UserRequest(
            "test_user4",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "",
            "",
            LocalDate.parse("2020-01-01"));

    StepVerifier.create(userService.createUser(createUserRequestInvalidFields))
        .assertNext(
            operationResponse -> {
              assertNotNull(operationResponse, "Объект не должен быть null.");
              assertEquals(
                  OperationResponse.OperationStatusEnum.FAILED,
                  operationResponse.getOperationStatus(),
                  "Статус должен быть FAILED");
              assertFalse(
                  operationResponse.getErrors().isEmpty(), "Список ошибок не должен быть пуст");
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
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2000-01-01"));

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
  void updateUserOk_shouldReturnOperationResponseWithSuccess() {
    UserRequest updateUserRequestSuccess =
        new UserRequest(
            "test_user1",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Сидоров Иван",
            "sidorov@example.ru",
            LocalDate.parse("1999-01-01"));
    updateUserRequestSuccess.setAccounts(
        List.of(CurrencyEnum.RUB, CurrencyEnum.CNY, CurrencyEnum.USD));

    // Счета до обновления
    StepVerifier.create(accountRepository.findUserCurrencyAccounts("test_user1"))
        .assertNext(
            account -> {
              assertEquals(CurrencyEnum.CNY, account.getCurrency());
              assertEquals(0, account.getValue().compareTo(new BigDecimal("20000.0")));
              assertTrue(account.getExists());
            })
        .assertNext(
            account -> {
              assertEquals(CurrencyEnum.RUB, account.getCurrency());
                assertEquals(0, account.getValue().compareTo(new BigDecimal("150000.0")));
              assertTrue(account.getExists());
            })
        .assertNext(
            account -> {
              assertEquals(CurrencyEnum.USD, account.getCurrency());
              assertEquals(BigDecimal.ZERO, account.getValue());
              assertFalse(account.getExists());
            })
        .verifyComplete();

    StepVerifier.create(userService.updateUser(updateUserRequestSuccess))
        .assertNext(
            operationResponse -> {
              assertNotNull(operationResponse, "Объект не должен быть null.");
              assertEquals(
                  OperationResponse.OperationStatusEnum.SUCCESS,
                  operationResponse.getOperationStatus(),
                  "Статус должен быть SUCCESS");
              assertTrue(operationResponse.getErrors().isEmpty(), "Список ошибок должен быть пуст");
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
                          assertEquals(CurrencyEnum.CNY, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("20000.0")));
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          assertEquals(CurrencyEnum.RUB, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("150000.0")));
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          assertEquals(CurrencyEnum.USD, account.getCurrency());
                          assertEquals(BigDecimal.ZERO, account.getValue());
                          assertTrue(account.getExists());
                      })
              .verifyComplete();
  }

  @Test
  void updateUserPartInvalidFields_shouldReturnOperationResponseWithFailedAndErrorsWithPartChangesInDatabase() {
    UserRequest updateUserRequestInvalidFields =
        new UserRequest(
            "test_user2",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "",
            "",
            LocalDate.parse("1993-05-21"));
      updateUserRequestInvalidFields.setAccounts(
              List.of(CurrencyEnum.RUB));

    StepVerifier.create(userService.updateUser(updateUserRequestInvalidFields))
        .assertNext(
            operationResponse -> {
              assertNotNull(operationResponse, "Объект не должен быть null.");
              assertEquals(
                  OperationResponse.OperationStatusEnum.FAILED,
                  operationResponse.getOperationStatus(),
                  "Статус должен быть FAILED");
              assertFalse(
                  operationResponse.getErrors().isEmpty(), "Список ошибок не должен быть пуст");
              assertEquals(List.of(
                      "Баланс на счету CNY не равен 0",
                      "Баланс на счету USD не равен 0",
                      "Заполните поле Фамилия Имя",
                      "Заполните электронную почту"
              ), operationResponse.getErrors());
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
                          assertEquals(CurrencyEnum.CNY, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("12000.0")));
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          log.info("Счет RUB: {}", account);
                          assertEquals(CurrencyEnum.RUB, account.getCurrency());
                          assertEquals(BigDecimal.ZERO, account.getValue());
                          assertTrue(account.getExists());
                      })
              .assertNext(
                      account -> {
                          log.info("Счет USD: {}", account);
                          assertEquals(CurrencyEnum.USD, account.getCurrency());
                          assertEquals(0, account.getValue().compareTo(new BigDecimal("1000.0")));
                          assertTrue(account.getExists());
                      })
              .verifyComplete();
  }

  @Test
  void updateExistingUser_shouldReturnUserNotFoundError() {
    UserRequest updateUserRequestNotFound =
        new UserRequest(
            "test_user4",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2000-01-01"));

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
  void getUserList() {
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
  void getUserByLogin() {
      AccountInfoRow rubAccount = new AccountInfoRow(CurrencyEnum.RUB, BigDecimal.ZERO, false);
      AccountInfoRow usdAccount = new AccountInfoRow(CurrencyEnum.USD, BigDecimal.ZERO, false);
      AccountInfoRow cnyAccount = new AccountInfoRow(CurrencyEnum.CNY, new BigDecimal("5000.0"), true);
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
  void updateUserPassword() {}

  @Test
  void cashOperation() {}

  @Test
  void transferOperation() {}

  @TestConfiguration
  static class TestConfig {
    @Bean
    public UserMapper userMapper() {
      return Mappers.getMapper(UserMapper.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
    }
  }
}
