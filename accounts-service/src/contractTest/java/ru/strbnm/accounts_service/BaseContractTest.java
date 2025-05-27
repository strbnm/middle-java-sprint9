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
  private OperationResponse successOperationResponse;
  private OperationResponse createUserFailed;
  private OperationResponse updateUserFailed;
  private OperationResponse updateUserPasswordFailed;
  private OperationResponse cashOperationFailed;
  private OperationResponse transferOperationFailed;
  private CashRequest cashRequestSuccess;
  private CashRequest cashRequestFailed;
  private TransferRequest transferRequestSuccess;
  private TransferRequest transferRequestFailed;
  private UserDetailResponse userDetailResponse;

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
    updateUserRequestSuccess.setAccounts(List.of(CurrencyEnum.RUB, CurrencyEnum.CNY));

    updatePasswordRequestSuccess =
        new UserPasswordRequest(
            "test_user1", "$2a$12$DpyrJV1Ob2RR7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6");

    updatePasswordRequestFailed =
        new UserPasswordRequest(
            "test_user1", "R7WZnEEUsVOShUOexUQIg.J/lzad8FNYty6/BDByo6");

    cashRequestSuccess =  new CashRequest(CurrencyEnum.RUB, new BigDecimal("1000.0"), CashRequest.ActionEnum.PUT);
    cashRequestFailed =  new CashRequest(CurrencyEnum.RUB, new BigDecimal("100000.0"), CashRequest.ActionEnum.GET);

    transferRequestSuccess = new TransferRequest(CurrencyEnum.RUB, CurrencyEnum.RUB, new BigDecimal("1000.0"), new BigDecimal("1000.0"), "test_user2");
    transferRequestFailed = new TransferRequest(CurrencyEnum.RUB, CurrencyEnum.RUB, new BigDecimal("1000.0"), new BigDecimal("1000.0"), "test_user1");

    userDetailResponse =
        new UserDetailResponse(
            "test_user1",
            "$2a$12$i3Mc.UTtmmFNgiqx0csrHe.dGbdVwXPbuEJ0T92InqlzX4YTzmwBa",
            "Иванов Иван",
            "ivanov@example.ru",
            LocalDate.parse("2000-05-21"),
            List.of("ROLE_CLIENT"));
    userDetailResponse.setAccounts(
        List.of(
            new AccountInfoRow(CurrencyEnum.RUB, new BigDecimal("150000.0"), true),
            new AccountInfoRow(CurrencyEnum.CNY, new BigDecimal("20000.0"), true),
            new AccountInfoRow(CurrencyEnum.USD, BigDecimal.ONE, false)));

    successOperationResponse =
        new OperationResponse(OperationResponse.OperationStatusEnum.SUCCESS, List.of());

    createUserFailed =
        new OperationResponse(
            OperationResponse.OperationStatusEnum.FAILED, List.of("Вам должно быть больше 18 лет"));
    updateUserFailed =
        new OperationResponse(
            OperationResponse.OperationStatusEnum.FAILED,
            List.of("Заполните поле Фамилия Имя", "Вам должно быть больше 18 лет"));
    updateUserPasswordFailed =
        new OperationResponse(
            OperationResponse.OperationStatusEnum.FAILED,
            List.of("Ошибка при сохранении изменений пароля. Операция отменена"));
    cashOperationFailed =
        new OperationResponse(
            OperationResponse.OperationStatusEnum.FAILED,
            List.of("На счете недостаточно средств"));
    
    transferOperationFailed =
            new OperationResponse(
                    OperationResponse.OperationStatusEnum.FAILED,
                    List.of("Перевести можно только между разными счетами"));

    when(userService.createUser(createUserRequestSuccess))
        .thenReturn(Mono.just(successOperationResponse));
    when(userService.createUser(createUserRequestFailed)).thenReturn(Mono.just(createUserFailed));

    when(userService.updateUser(updateUserRequestSuccess))
        .thenReturn(Mono.just(successOperationResponse));
    when(userService.createUser(updateUserRequestFailed)).thenReturn(Mono.just(updateUserFailed));

    when(userService.updateUserPassword(updatePasswordRequestSuccess))
        .thenReturn(Mono.just(successOperationResponse));
    when(userService.updateUserPassword(updatePasswordRequestFailed))
        .thenReturn(Mono.just(updateUserPasswordFailed));

    when(userService.getUserByLogin("test_user1")).thenReturn(Mono.just(userDetailResponse));
    when(userService.getUserByLogin("test_user4"))
        .thenReturn(
            Mono.error(
                new UserNotFoundException("Пользователь с логином test_user4 не существует")));

    when(userService.getUserList())
        .thenReturn(
            Flux.just(
                new UserListResponseInner("test_user1", "Иванов Иван"),
                new UserListResponseInner("test_user2", "Петров Петр")));

    when(userService.cashOperation(cashRequestSuccess, "test_user1")).thenReturn(Mono.just(successOperationResponse));
    when(userService.cashOperation(cashRequestFailed, "test_user1")).thenReturn(Mono.just(successOperationResponse));

    when(userService.transferOperation(transferRequestSuccess, "test_user1")).thenReturn(Mono.just(successOperationResponse));
    when(userService.transferOperation(transferRequestFailed, "test_user1")).thenReturn(Mono.just(transferOperationFailed));
  }

}
