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

    @BeforeEach
  void setup() {
    RestAssuredWebTestClient.webTestClient(webTestClient);

      UserRequest createUserRequestSuccess = new UserRequest(
              "test_user1",
              "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
              "Иванов Иван",
              "ivanov@example.ru",
              LocalDate.parse("2000-01-01"));

      UserRequest createUserRequestFailed = new UserRequest(
              "test_user1",
              "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
              "Иванов Иван",
              "ivanov@example.ru",
              LocalDate.parse("2010-01-01"));

      UserRequest updateUserRequestSuccess = new UserRequest(
              "test_user1",
              "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
              "Иванов Иван",
              "test@example.ru",
              LocalDate.parse("1999-01-01"));

        UserRequest updateUserRequestFailed = new UserRequest(
                "test_user1",
                "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
                "",
                "test@example.ru",
                LocalDate.parse("2020-01-01"));
    updateUserRequestSuccess.setAccounts(List.of(AccountCurrencyEnum.RUB, AccountCurrencyEnum.CNY));

        UserPasswordRequest updatePasswordRequestSuccess = new UserPasswordRequest(
                "test_user1", "$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6");

        UserPasswordRequest updatePasswordRequestFailed = new UserPasswordRequest(
                "test_user1", "R7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6");

        CashRequest cashRequestSuccessPut = new CashRequest(AccountCurrencyEnum.RUB, new BigDecimal("1000.0"), CashRequest.ActionEnum.PUT);
        CashRequest cashRequestSuccessGet = new CashRequest(AccountCurrencyEnum.RUB, new BigDecimal("1000.0"), CashRequest.ActionEnum.GET);
        CashRequest cashRequestFailed = new CashRequest(AccountCurrencyEnum.RUB, new BigDecimal("100000.0"), CashRequest.ActionEnum.GET);
        CashRequest cashRequestFailedMissingAccount = new CashRequest(AccountCurrencyEnum.USD, new BigDecimal("1000.0"), CashRequest.ActionEnum.GET);

        TransferRequest transferRequestSuccess1 = new TransferRequest(AccountCurrencyEnum.CNY, AccountCurrencyEnum.CNY, new BigDecimal("1000.0"), new BigDecimal("1000.0"), "test_user2");
        TransferRequest transferRequestSuccess2 = new TransferRequest(AccountCurrencyEnum.RUB, AccountCurrencyEnum.USD, new BigDecimal("1000.0"), new BigDecimal("12.0"), "test_user2");
        TransferRequest transferRequestFailed = new TransferRequest(AccountCurrencyEnum.RUB, AccountCurrencyEnum.RUB, new BigDecimal("1000.0"), new BigDecimal("1000.0"), "test_user1");
        TransferRequest transferRequestFailedMissingAccount = new TransferRequest(AccountCurrencyEnum.RUB, AccountCurrencyEnum.USD, new BigDecimal("1000.0"), new BigDecimal("12.0"), "test_user1");
        TransferRequest transferRequestFailedInfluenceFunds = new TransferRequest(AccountCurrencyEnum.RUB, AccountCurrencyEnum.CNY, new BigDecimal("200000.0"), new BigDecimal("22000.0"), "test_user2");

        UserDetailResponse userDetailResponse1 = new UserDetailResponse(
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

        UserDetailResponse userDetailResponse2 = new UserDetailResponse(
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

        AccountOperationResponse successAccountOperationResponse = new AccountOperationResponse(AccountOperationResponse.OperationStatusEnum.SUCCESS, List.of());

        AccountOperationResponse createUserFailed = new AccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.FAILED, List.of("Вам должно быть больше 18 лет"));
        AccountOperationResponse updateUserFailed = new AccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.FAILED,
                List.of("Заполните поле Фамилия Имя", "Вам должно быть больше 18 лет"));
        AccountOperationResponse updateUserPasswordFailed = new AccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.FAILED,
                List.of("Ошибка при сохранении изменений пароля. Операция отменена"));
        AccountOperationResponse cashOperationFailed = new AccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.FAILED,
                List.of("На счете недостаточно средств"));
        AccountOperationResponse cashOperationFailedMissingAccount = new AccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.FAILED,
                List.of("У Вас отсутствует счет в выбранной валюте"));

        AccountOperationResponse transferOperationFailed = new AccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.FAILED,
                List.of("Перевести можно только между разными счетами"));
    AccountOperationResponse transferOperationFailedMissingAccount =
        new AccountOperationResponse(
            AccountOperationResponse.OperationStatusEnum.FAILED,
            List.of("У Вас отсутствует счет в выбранной валюте"));
    AccountOperationResponse transferOperationFailedInfluenceFunds =
        new AccountOperationResponse(
            AccountOperationResponse.OperationStatusEnum.FAILED,
            List.of("На счете недостаточно средств"));

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

    when(userService.transferOperation(transferRequestSuccess1, "test_user1")).thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.transferOperation(transferRequestSuccess2, "test_user1")).thenReturn(Mono.just(successAccountOperationResponse));
    when(userService.transferOperation(transferRequestFailed, "test_user1")).thenReturn(Mono.just(transferOperationFailed));
    when(userService.transferOperation(transferRequestFailedMissingAccount, "test_user1")).thenReturn(Mono.just(transferOperationFailedMissingAccount));
    when(userService.transferOperation(transferRequestFailedInfluenceFunds, "test_user1")).thenReturn(Mono.just(transferOperationFailedInfluenceFunds));
  }

}
