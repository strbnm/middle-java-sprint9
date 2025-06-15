package ru.strbnm.front_ui.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import org.thymeleaf.spring6.context.webflux.ReactiveDataDriverContextVariable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.front_ui.client.accounts.domain.*;
import ru.strbnm.front_ui.client.cash.domain.CashCurrencyEnum;
import ru.strbnm.front_ui.client.cash.domain.CashOperationRequest;
import ru.strbnm.front_ui.client.cash.domain.CashOperationResponse;
import ru.strbnm.front_ui.client.transfer.domain.TransferCurrencyEnum;
import ru.strbnm.front_ui.client.transfer.domain.TransferOperationRequest;
import ru.strbnm.front_ui.client.transfer.domain.TransferOperationResponse;
import ru.strbnm.front_ui.dto.*;
import ru.strbnm.front_ui.service.FrontUiService;
import ru.strbnm.front_ui.utils.Currency;
import ru.strbnm.front_ui.utils.CurrentUserService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FrontUiController {

  private final FrontUiService frontUiService;
  private final CurrentUserService currentUserService;

  @GetMapping("/")
  public Mono<String> redirectToMain() {
    return Mono.just("redirect:/main");
  }

  @GetMapping("/main")
  @PreAuthorize("isAuthenticated()")
  public Mono<String> mainPage(Model model, WebSession session) {
    Flux<UserListResponseInner> usersFlux = frontUiService.getAllUsers();
    return currentUserService
        .getCurrentUserLogin()
        .flatMap(
            userLogin ->
                frontUiService
                    .getUserDetailByLogin(userLogin)
                    .doOnNext(
                        user -> {
                          model.addAttribute("login", userLogin);
                          model.addAttribute("name", user.getName());
                          model.addAttribute("email", user.getEmail());
                          model.addAttribute("birthdate", user.getBirthdate());
                            assert user.getAccounts() != null;
                            model.addAttribute("accounts", toAccountInfoDto(user.getAccounts()));
                          model.addAttribute("currency", Arrays.stream(Currency.values()).toList());
                          addSessionErrorsToModel(session, model, "passwordErrors");
                          addSessionErrorsToModel(session, model, "userAccountsErrors");
                          addSessionErrorsToModel(session, model, "cashErrors");
                          addSessionErrorsToModel(session, model, "transferErrors");
                          addSessionErrorsToModel(session, model, "transferOtherErrors");
                        })
                    .thenReturn("main")
                    .doOnSuccess(
                        viewName ->
                            model.addAttribute(
                                "users", new ReactiveDataDriverContextVariable(usersFlux))));
  }

  @PostMapping("/user/{login}/editPassword")
  @PreAuthorize("isAuthenticated()")
  public Mono<String> editPassword(
      @PathVariable String login,
      @ModelAttribute UpdateUserPasswordFormDto form,
      WebSession session) {
    return currentUserService.getCurrentUserLogin()
            .flatMap(userLogin -> {
                List<String> errors = new ArrayList<>();
                if (!userLogin.equals(login)) errors.add("Логин не совпадает с логином аутентифицированного пользователя. Операция отклонена.");
                if (form.getPassword().isEmpty()) errors.add("Пароль не может быть пустым");
                if (!form.getPassword().equals(form.getConfirm_password())) errors.add("Пароли должны совпадать");
                if (!errors.isEmpty()) {
                    session.getAttributes().put("passwordErrors", errors);
                    return Mono.just(new AccountOperationResponse(AccountOperationResponse.OperationStatusEnum.FAILED, errors));
                } else {
                    return frontUiService.updateUserPassword(userLogin, form.getPassword());
                }
            })
        .doOnNext(operationResponse -> handleAccountsResponseError(session, operationResponse, "passwordErrors"))
        .thenReturn("redirect:/main");
  }

  @PostMapping("/user/{login}/editUserAccounts")
  @PreAuthorize("isAuthenticated()")
  public Mono<String> editUserAccounts(
      @PathVariable String login,
      @ModelAttribute EditUserFormDto form,
      WebSession session) {
    return currentUserService.getCurrentUserLogin()
            .flatMap(userLogin -> {
                List<String> errors = new ArrayList<>();
                if (!userLogin.equals(login))  errors.add("Логин не совпадает с логином аутентифицированного пользователя. Операция отклонена.");
                if (form.getName().isEmpty()) errors.add("Заполните поле Фамилия Имя");
                if (form.getEmail().isEmpty()) errors.add("Заполните электронную почту");
                if (calculateAge(form.getBirthdate()) == 0) errors.add("Дата рождения не может быть пустой или позже текущей даты");
                if (calculateAge(form.getBirthdate()) < 18) errors.add("Вам должно быть больше 18 лет");
                if (!errors.isEmpty()) {
                    return Mono.just(new AccountOperationResponse(AccountOperationResponse.OperationStatusEnum.FAILED, errors));
                } else {
                    return frontUiService.updateUser(userLogin, UserRequest.builder()
                            .login(userLogin)
                            .name(form.getName())
                            .email(form.getEmail())
                            .birthdate(form.getBirthdate())
                            .accounts(form.getAccounts().stream()
                                    .map(AccountCurrencyEnum::fromValue)
                                    .toList())
                            .build());
                    }
            })
            .doOnNext(operationResponse -> handleAccountsResponseError(session, operationResponse, "userAccountsErrors"))
            .thenReturn("redirect:/main");
  }

  @PostMapping(value = "/user/{login}/cash", consumes = { "application/x-www-form-urlencoded" })
  @PreAuthorize("isAuthenticated()")
  public Mono<String> cashOperation(
      @PathVariable String login,
      @ModelAttribute CashFormDto form,
      WebSession session) {
      return currentUserService.getCurrentUserLogin()
              .flatMap(userLogin -> {
                  List<String> errors = new ArrayList<>();
                  if (!userLogin.equals(login))  errors.add("Логин не совпадает с логином аутентифицированного пользователя. Операция отклонена.");
                  if (form.getAmount().compareTo(BigDecimal.ZERO) <= 0) errors.add("Сумма операции не может быть отрицательной или равна 0");
                  if (!isValidCurrency(form.getCurrency())) errors.add("Валюта отсутствует в перечне доступных валют");
                  if (!errors.isEmpty()) {
                      return Mono.just(new CashOperationResponse(CashOperationResponse.OperationStatusEnum.FAILED, errors));
                  } else {
                      return frontUiService.performCashOperation(userLogin, CashOperationRequest.builder()
                              .login(userLogin)
                              .currency(CashCurrencyEnum.fromValue(form.getCurrency()))
                              .amount(form.getAmount())
                              .action(CashOperationRequest.ActionEnum.fromValue(form.getAction()))
                              .build()
                      );
                  }
              })
              .doOnNext(operationResponse -> handleCashResponseError(session, operationResponse))
              .thenReturn("redirect:/main");
  }


  @PostMapping("/user/{login}/transfer")
  @PreAuthorize("isAuthenticated()")
  public Mono<String> transfer(
      @PathVariable String login,
      @ModelAttribute TransferFormDto form,
      WebSession session) {
    return currentUserService
        .getCurrentUserLogin()
        .flatMap(
            userLogin -> {
                log.info("TransferForm: {}", form);
              List<String> errors = new ArrayList<>();
              if (!userLogin.equals(login))
                errors.add(
                    "Логин не совпадает с логином аутентифицированного пользователя. Операция отклонена.");
              if (form.getAmount().compareTo(BigDecimal.ZERO) <= 0)
                errors.add("Сумма операции не может быть отрицательной или равна 0");
              if (login.equals(form.getToLogin()) && form.getFromCurrency().equals(form.getToCurrency()))
                errors.add("Перевести можно только между разными счетами");
              if (!errors.isEmpty()) {
                return Mono.just(
                    new TransferOperationResponse(
                        TransferOperationResponse.OperationStatusEnum.FAILED, errors));
              } else {
                return frontUiService.performTransferOperation(
                    userLogin,
                    TransferOperationRequest.builder()
                        .fromLogin(userLogin)
                        .toLogin(form.getToLogin())
                        .fromCurrency(TransferCurrencyEnum.fromValue(form.getFromCurrency()))
                        .toCurrency(TransferCurrencyEnum.fromValue(form.getToCurrency()))
                        .amount(form.getAmount())
                        .build());
              }
            })
        .doOnNext(
            operationResponse -> {
              if (login.equals(form.getToLogin())) {
                handleTransferResponseError(session, operationResponse, "transferErrors");
              } else {
                handleTransferResponseError(session, operationResponse, "transferOtherErrors");
              }
            })
        .thenReturn("redirect:/main");
  }

  @GetMapping("/signup")
  public Mono<String> signupForm() {
    return Mono.just("signup");
  }

  @PostMapping("/signup")
  public Mono<String> signup(
      @ModelAttribute SignupFormDto form,
      Model model) {
      return Mono.just(form.getLogin())
              .flatMap(userLogin -> {
                  List<String> errors = new ArrayList<>();
                  if (userLogin.isEmpty())  errors.add("Заполните логин");
                  if (!form.getLogin().matches("^[A-Za-z\\d_-]+$")) errors.add("Логин может содержать только латинские буквы, цифры, дефис или подчёркивание");
                  if (form.getPassword().isEmpty()) errors.add("Заполните пароль");
                  if (form.getConfirm_password().isEmpty() || !form.getConfirm_password().equals(form.getPassword())) errors.add("Пароли не совпадают");
                  if (form.getName().isEmpty()) errors.add("Заполните поле Фамилия Имя");
                  if (form.getEmail().isEmpty()) errors.add("Заполните электронную почту");
                  if (calculateAge(form.getBirthdate()) == 0) errors.add("Дата рождения не может быть пустой или позже текущей даты");
                  if (calculateAge(form.getBirthdate()) < 18) errors.add("Вам должно быть больше 18 лет");
                  if (!errors.isEmpty()) {
                      model.addAttribute("login", form.getLogin());
                      model.addAttribute("name", form.getName());
                      model.addAttribute("email", form.getEmail());
                      model.addAttribute("birthdate", form.getBirthdate());
                      model.addAttribute("errors", errors);
                      return Mono.just("signup");
                  } else {
                      return frontUiService.createUser(
                              UserRequest.builder()
                                  .login(userLogin)
                                  .name(form.getName())
                                  .password(form.getPassword())
                                  .email(form.getEmail())
                                  .birthdate(form.getBirthdate())
                                  .build())
                              .flatMap(
                                      operationResponse -> {
                                          if (operationResponse.getOperationStatus() == AccountOperationResponse.OperationStatusEnum.FAILED) {
                                              model.addAttribute("login", form.getLogin());
                                              model.addAttribute("name", form.getName());
                                              model.addAttribute("email", form.getEmail());
                                              model.addAttribute("birthdate", form.getBirthdate());
                                              model.addAttribute("errors", errors);
                                              return Mono.just("signup");
                                          } else {
                                              return Mono.just("redirect:/main");
                                          }
                                              
                                      }
                              );
                  }
              });
  }

  private void addSessionErrorsToModel(WebSession session, Model model, String attributeName) {
    if (session.getAttributes().containsKey(attributeName)) {
      model.addAttribute(attributeName, session.getAttribute(attributeName));
      session.getAttributes().remove(attributeName);
    } else {
      model.addAttribute(attributeName, null);
    }
  }

    private int calculateAge(LocalDate birthdate) {
        if (birthdate == null || birthdate.isAfter(LocalDate.now())) {
            return 0; // или выбросить исключение
        }
        return Period.between(birthdate, LocalDate.now()).getYears();
    }

    private void handleAccountsResponseError(WebSession session, AccountOperationResponse operationResponse, String errorKey) {
        if (operationResponse.getOperationStatus() == AccountOperationResponse.OperationStatusEnum.FAILED) {
            session.getAttributes().put(errorKey, operationResponse.getErrors());
        }
    }

    private void handleCashResponseError(WebSession session, CashOperationResponse operationResponse) {
        if (operationResponse.getOperationStatus() == CashOperationResponse.OperationStatusEnum.FAILED) {
            session.getAttributes().put("cashErrors", operationResponse.getErrors());
        }
    }

    private void handleTransferResponseError(WebSession session, TransferOperationResponse operationResponse, String errorKey) {
        if (operationResponse.getOperationStatus() == TransferOperationResponse.OperationStatusEnum.FAILED) {
            session.getAttributes().put(errorKey, operationResponse.getErrors());
        }
    }

    public boolean isValidCurrency(String value) {
        return Arrays.stream(Currency.values())
                .anyMatch(currency -> currency.name().equalsIgnoreCase(value));
    }

    private List<AccountInfoDto> toAccountInfoDto(List<AccountInfoRow> accounts) {
      return accounts.stream()
              .map(
                      account -> AccountInfoDto.builder()
                              .currency(Currency.valueOf(account.getCurrency().name()))
                              .value(account.getValue())
                              .exists(account.getExists())
                              .build()
              ).toList();
    }
}
