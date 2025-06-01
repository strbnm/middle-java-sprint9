package ru.strbnm.accounts_service;

import static org.mockito.Mockito.*;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.config.ContractTestSecurityConfig;
import ru.strbnm.accounts_service.domain.*;
import ru.strbnm.accounts_service.exception.UserNotFoundException;
import ru.strbnm.accounts_service.service.UserService;

@ActiveProfiles("contracts")
@Import(ContractTestSecurityConfig.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.config.name=application-contracts"})
public abstract class BaseContractTest {

  @Autowired protected WebTestClient webTestClient;
  @MockitoBean private UserService userService;

  private UserRequest createUserRequestSuccess;
  private UserRequest createUserRequestFailed;
  private UserRequest updateUserRequestSuccess;
  private UserRequest updateUserRequestFailed;
  private UserPasswordRequest updatePasswordRequestSuccess;
  private UserPasswordRequest updatePasswordRequestFailed;
  private AccountOperationResponse successAccountOperationResponse;
  private AccountOperationResponse createUserFailed;
  private AccountOperationResponse updateUserFailed;
  private AccountOperationResponse updateUserPasswordFailed;
  private AccountOperationResponse cashOperationFailed;
  private AccountOperationResponse cashOperationFailedMissingAccount;
  private AccountOperationResponse transferOperationFailed;
  private CashRequest cashRequestSuccessPut;
  private CashRequest cashRequestSuccessGet;
  private CashRequest cashRequestFailed;
  private CashRequest cashRequestFailedMissingAccount;
  private TransferRequest transferRequestSuccess;
  private TransferRequest transferRequestFailed;
  private UserDetailResponse userDetailResponse1;
  private UserDetailResponse userDetailResponse2;

  @BeforeEach
  void setup() {
    RestAssuredWebTestClient.webTestClient(webTestClient);

    createUserRequestSuccess =
        new UserRequest(
            "test_user1",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2000-01-01"));

    createUserRequestFailed =
        new UserRequest(
            "test_user1",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2010-01-01"));

    updateUserRequestSuccess =
        new UserRequest(
            "test_user1",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Иванов Иван",
            "test@example.ru",
            LocalDate.parse("1999-01-01"));

    updateUserRequestFailed =
        new UserRequest(
            "test_user1",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "",
            "test@example.ru",
            LocalDate.parse("2020-01-01"));
    updateUserRequestSuccess.setAccounts(List.of(AccountCurrencyEnum.RUB, AccountCurrencyEnum.CNY));

    updatePasswordRequestSuccess =
        new UserPasswordRequest(
            "test_user1", "$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6");

    updatePasswordRequestFailed =
        new UserPasswordRequest(
            "test_user1", "R7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6");

    cashRequestSuccessPut =  new CashRequest(AccountCurrencyEnum.RUB, new BigDecimal("1000.0"), CashRequest.ActionEnum.PUT);
    cashRequestSuccessGet =  new CashRequest(AccountCurrencyEnum.RUB, new BigDecimal("1000.0"), CashRequest.ActionEnum.GET);
    cashRequestFailed =  new CashRequest(AccountCurrencyEnum.RUB, new BigDecimal("100000.0"), CashRequest.ActionEnum.GET);
    cashRequestFailedMissingAccount =  new CashRequest(AccountCurrencyEnum.USD, new BigDecimal("1000.0"), CashRequest.ActionEnum.GET);

    transferRequestSuccess = new TransferRequest(AccountCurrencyEnum.CNY, AccountCurrencyEnum.CNY, new BigDecimal("1000.0"), new BigDecimal("1000.0"), "test_user2");
    transferRequestFailed = new TransferRequest(AccountCurrencyEnum.RUB, AccountCurrencyEnum.RUB, new BigDecimal("1000.0"), new BigDecimal("1000.0"), "test_user1");

    userDetailResponse1 =
        new UserDetailResponse(
            "test_user1",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2000-05-21"),
            List.of("ROLE_CLIENT"));
    userDetailResponse1.setAccounts(
        List.of(
            new AccountInfoRow(AccountCurrencyEnum.RUB, new BigDecimal("150000.0"), true),
            new AccountInfoRow(AccountCurrencyEnum.CNY, new BigDecimal("20000.0"), true),
            new AccountInfoRow(AccountCurrencyEnum.USD, BigDecimal.ZERO, false)));

    userDetailResponse2 =
            new UserDetailResponse(
                    "test_user2",
                    "$2a$12$8iuXDswC26EzrjL0qWNOiuzwZi5/zGuuJY7gaEkoPnaIPZodfm.xi",
                    "Петров Петр",
                    "petrov@example.ru",
                    LocalDate.parse("1990-05-21"),
                    List.of("ROLE_CLIENT"));
    userDetailResponse2.setAccounts(
            List.of(
                    new AccountInfoRow(AccountCurrencyEnum.RUB, BigDecimal.ZERO, false),
                    new AccountInfoRow(AccountCurrencyEnum.CNY, new BigDecimal("12000.0"), true),
                    new AccountInfoRow(AccountCurrencyEnum.USD,  new BigDecimal("1000.0"), true)));

    successAccountOperationResponse =
        new AccountOperationResponse(AccountOperationResponse.OperationStatusEnum.SUCCESS, List.of());

    createUserFailed =
        new AccountOperationResponse(
            AccountOperationResponse.OperationStatusEnum.FAILED, List.of("Вам должно быть больше 18 лет"));
    updateUserFailed =
        new AccountOperationResponse(
            AccountOperationResponse.OperationStatusEnum.FAILED,
            List.of("Заполните поле Фамилия Имя", "Вам должно быть больше 18 лет"));
    updateUserPasswordFailed =
        new AccountOperationResponse(
            AccountOperationResponse.OperationStatusEnum.FAILED,
            List.of("Ошибка при сохранении изменений пароля. Операция отменена"));
    cashOperationFailed =
        new AccountOperationResponse(
            AccountOperationResponse.OperationStatusEnum.FAILED,
            List.of("На счете недостаточно средств"));
    cashOperationFailedMissingAccount =
        new AccountOperationResponse(
            AccountOperationResponse.OperationStatusEnum.FAILED,
            List.of("У Вас отсутствует счет в выбранной валюте"));

    transferOperationFailed =
            new AccountOperationResponse(
                    AccountOperationResponse.OperationStatusEnum.FAILED,
                    List.of("Перевести можно только между разными счетами"));

    when(userService.createUser(createUserRequestSuccess))
        .thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.createUser(createUserRequestFailed)).thenReturn(Mono.just(createUserFailed));

    when(userService.updateUser(updateUserRequestSuccess))
        .thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.updateUser(updateUserRequestFailed)).thenReturn(Mono.just(updateUserFailed));

    when(userService.updateUserPassword(updatePasswordRequestSuccess))
        .thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.updateUserPassword(updatePasswordRequestFailed))
        .thenReturn(Mono.just(updateUserPasswordFailed));

    when(userService.getUserByLogin("test_user1")).thenReturn(Mono.just(userDetailResponse1));
    when(userService.getUserByLogin("test_user2")).thenReturn(Mono.just(userDetailResponse2));
    when(userService.getUserByLogin("test_user4"))
        .thenReturn(
            Mono.error(
                new UserNotFoundException("Пользователь с логином test_user4 не существует")));

    when(userService.getUserList())
        .thenReturn(
            Flux.just(
                new UserListResponseInner("test_user1", "Иванов Иван"),
                new UserListResponseInner("test_user2", "Петров Петр")));

    when(userService.cashOperation(cashRequestSuccessPut, "test_user1")).thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.cashOperation(cashRequestSuccessGet, "test_user1")).thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.cashOperation(cashRequestFailed, "test_user1")).thenReturn(Mono.just(cashOperationFailed));
    when(userService.cashOperation(cashRequestFailedMissingAccount, "test_user1")).thenReturn(Mono.just(cashOperationFailedMissingAccount));

    when(userService.transferOperation(transferRequestSuccess, "test_user1")).thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.transferOperation(transferRequestFailed, "test_user1")).thenReturn(Mono.just(transferOperationFailed));
  }

}
