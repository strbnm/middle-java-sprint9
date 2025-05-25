package ru.strbnm.accounts_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.retry.Retry;
import ru.strbnm.accounts_service.client.exchange.api.ExchangeServiceApi;
import ru.strbnm.accounts_service.client.notifications.api.NotificationsServiceApi;
import ru.strbnm.accounts_service.client.notifications.domain.NotificationRequest;
import ru.strbnm.accounts_service.domain.*;
import ru.strbnm.accounts_service.dto.AccountCheckResult;
import ru.strbnm.accounts_service.entity.Account;
import ru.strbnm.accounts_service.entity.Role;
import ru.strbnm.accounts_service.entity.User;
import ru.strbnm.accounts_service.entity.UserRole;
import ru.strbnm.accounts_service.exception.AccountNotFoundForCurrencyException;
import ru.strbnm.accounts_service.exception.UserAlreadyExistsException;
import ru.strbnm.accounts_service.exception.UserNotFoundException;
import ru.strbnm.accounts_service.mapper.UserMapper;
import ru.strbnm.accounts_service.repository.AccountRepository;
import ru.strbnm.accounts_service.repository.RoleRepository;
import ru.strbnm.accounts_service.repository.UserRepository;
import ru.strbnm.accounts_service.repository.UserRoleRepository;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final AccountRepository accountRepository;
  private final NotificationsServiceApi notificationsServiceApi;
  private final UserMapper userMapper;

  private final String FROM_CURRENCY = "fromCurrency";
  private final String TO_CURRENCY = "toCurrency";
  private final String NOT_ITSELS_ACCOUNT = "У Вас отсутствует счет в выбранной валюте";
    private final String NOT_FOUND_USER = "Пользователь с логином %s не существует";


  @Autowired
  public UserServiceImpl(
      UserRepository userRepository,
      RoleRepository roleRepository,
      UserRoleRepository userRoleRepository,
      AccountRepository accountRepository,
      NotificationsServiceApi notificationsServiceApi,
      ExchangeServiceApi exchangeServiceApi,
      UserMapper userMapper) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
    this.accountRepository = accountRepository;
    this.notificationsServiceApi = notificationsServiceApi;
    this.userMapper = userMapper;
  }

  @Override
  public Mono<UserDetailResponse> createUser(UserRequest userRequest) {
    return userRepository
        .findUserByLogin(userRequest.getLogin())
        .flatMap(
            existingUser ->
                Mono.<UserDetailResponse>error(
                    new UserAlreadyExistsException(
                        "Пользователь с таким логином уже существует: " + existingUser.getLogin())))
        .switchIfEmpty(
            findOrCreateClientRole()
                .flatMap(
                    role ->
                        saveUserAsClient(role, userRequest)
                            .flatMap(
                                user ->
                                    mapToUserDetailResponse(user, role)
                                        .flatMap(
                                            response ->
                                                sendNotification(
                                                        user.getEmail(),
                                                        "Вы успешно зарегистрированы.")
                                                    .thenReturn(response)))));
  }

  @Override
  public Mono<UserDetailResponse> updateUser(UserRequest userRequest) {
    return userRepository
        .findUserByLogin(userRequest.getLogin())
        .switchIfEmpty(
            Mono.error(
                new UserNotFoundException(String.format(NOT_FOUND_USER, userRequest.getLogin()))))
        .flatMap(
            existingUser ->
                updateExistingUser(existingUser, userRequest)
                    .flatMap(this::sendNotificationAfterUpdate));
  }

  private Mono<UserDetailResponse> sendNotificationAfterUpdate(UserDetailResponse response) {
    StringBuilder msg;
    if (response.getErrors().isEmpty()) {
      msg = new StringBuilder("Информация аккаунта успешно обновлена.");
    } else {
      msg =
          new StringBuilder(
              "Информация аккаунта не обновлена или обновлена частично.\nОшибки в процессе обновления:");
      for (String error : response.getErrors()) {
        msg.append("\n").append(error);
      }
    }
    return sendNotification(response.getEmail(), msg.toString()).thenReturn(response);
  }

  @Override
  public Flux<UserListResponseInner> getUserList() {
    return userRepository
        .findAll()
        .map(
            user ->
                UserListResponseInner.builder()
                    .login(user.getLogin())
                    .name(user.getName())
                    .build());
  }

  @Override
  public Mono<UserDetailResponse> getUserByLogin(String login) {
    return Mono.zip(
            userRepository.getUserWithRolesByLogin(login),
            accountRepository.findUserCurrencyAccounts(login).collectList())
        .map(
            tuple2 -> {
              UserDetailResponse existingUser = tuple2.getT1();
              List<AccountInfoRow> accounts = tuple2.getT2();
              existingUser.accounts(accounts);
              return existingUser;
            });
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  @Override
  public Mono<UserDetailResponse> cashOperation(CashRequest cashRequest, String login) {
    return userRepository
        .findUserByLogin(login)
        .switchIfEmpty(
            Mono.error(
                new UserNotFoundException(String.format(NOT_FOUND_USER, login))))
        .flatMap(
            existingUser ->
                checkCashTransaction(cashRequest, existingUser)
                    .flatMap(
                        accountCheckResult -> {
                          if (!accountCheckResult.errors().isEmpty()
                              || accountCheckResult.account() == null) {
                            return getUserDetailResponse(existingUser, accountCheckResult.errors());
                          } else {
                            return getUserDetailResponseAfterCashTransaction(
                                cashRequest, existingUser, accountCheckResult);
                          }
                        }));
  }

  private Mono<UserDetailResponse> getUserDetailResponseAfterCashTransaction(
      CashRequest cashRequest, User existingUser, AccountCheckResult accountCheckResult) {
    Account account = accountCheckResult.account();
    if (cashRequest.getAction() == CashRequest.ActionEnum.GET) {
      account.setBalance(account.getBalance().subtract(cashRequest.getAmount()));
    } else if (cashRequest.getAction() == CashRequest.ActionEnum.PUT) {
      account.setBalance(account.getBalance().add(cashRequest.getAmount()));
    }
    return accountRepository
        .save(account)
        .flatMap(
            savedAccount ->
                getUserDetailResponse(existingUser, List.of())
                    .flatMap(
                        response -> sendNotificationAfterCashTransaction(cashRequest, response)))
        .onErrorResume(
            e -> {
              log.error("Ошибка при сохранении изменений по счету {}", account.getId(), e);
              List<String> errors =
                  List.of("Ошибка при сохранении изменений по счету. Операция отменена");
              return getUserDetailResponse(existingUser, errors);
            });
  }

  private Mono<UserDetailResponse> sendNotificationAfterCashTransaction(
      CashRequest cashRequest, UserDetailResponse response) {
    StringBuilder msg;
    if (cashRequest.getAction() == CashRequest.ActionEnum.GET) {
      msg =
          new StringBuilder(
              String.format(
                  "Снятие наличных в размере %s %s со счета",
                  cashRequest.getAmount(), cashRequest.getCurrency()));
    } else {
      msg =
          new StringBuilder(
              String.format(
                  "Пополнение счета на сумму %s %s",
                  cashRequest.getAmount(), cashRequest.getCurrency()));
    }
    return sendNotification(response.getEmail(), msg.toString()).thenReturn(response);
  }

  private Mono<UserDetailResponse> getUserDetailResponse(User existingUser, List<String> errors) {
    return Mono.zip(
            userRepository.getUserWithRolesByLogin(existingUser.getLogin()),
            accountRepository.findUserCurrencyAccounts(existingUser.getLogin()).collectList())
        .map(
            tuple2 -> {
              UserDetailResponse user = tuple2.getT1();
              List<AccountInfoRow> accounts = tuple2.getT2();
              user.accounts(accounts);
              user.errors(errors);
              return user;
            });
  }

  private Mono<AccountCheckResult> checkCashTransaction(CashRequest cashRequest, User user) {
    return accountRepository
        .findByUserIdAndCurrency(user.getId(), cashRequest.getCurrency().name())
        .map(account -> new AccountCheckResult(account, new ArrayList<>()))
        .switchIfEmpty(
            Mono.just(
                new AccountCheckResult(
                    null, new ArrayList<>(List.of(NOT_ITSELS_ACCOUNT)))))
        .flatMap(
            accountCheckResult -> {
              // Дополнительная проверка и добавление ошибок
              if (cashRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                accountCheckResult.errors().add("Сумма должна быть положительной");
              }
              if (accountCheckResult.account() != null) {
                Account account = accountCheckResult.account();
                if (cashRequest.getAction() == CashRequest.ActionEnum.GET
                    && account.getBalance().compareTo(cashRequest.getAmount()) < 0) {
                  accountCheckResult.errors().add("На счете недостаточно средств");
                }
              }
              return Mono.just(accountCheckResult);
            });
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  @Override
  public Mono<UserDetailResponse> transferOperation(TransferRequest transferRequest, String login) {
    if (!login.equals(transferRequest.getToLogin())) {
      return transferOtherOperation(transferRequest, login);
    } else {
      return transferItselfOperation(transferRequest, login);
    }
  }

  private Mono<UserDetailResponse> transferOtherOperation(
      TransferRequest transferRequest, String login) {
    Mono<User> senderMono = getUserMono(login);
    Mono<User> recipientMono = getUserMono(transferRequest.getToLogin());

    return Mono.zip(senderMono, recipientMono)
        .flatMap(
            tuple -> {
              return processTransferOtherOperation(transferRequest, tuple);
            });
  }

  private Mono<UserDetailResponse> transferItselfOperation(
      TransferRequest transferRequest, String login) {
    return getUserMono(login)
        .flatMap(user -> processTransferItselfOperation(transferRequest, user));
  }

  private Mono<UserDetailResponse> processTransferOtherOperation(
      TransferRequest transferRequest, Tuple2<User, User> userTuple) {
    User sender = userTuple.getT1();
    User recipient = userTuple.getT2();

    Mono<AccountCheckResult> senderAccountCheckResultMono =
        getAccountCheckResultMono(
            transferRequest, sender, FROM_CURRENCY, NOT_ITSELS_ACCOUNT);
      Mono<AccountCheckResult> recipientAccountCheckResultMono =
            getAccountCheckResultMono(transferRequest, recipient, TO_CURRENCY,
                    String.format("У клиента %s отсутствует счет в выбранной валюте", recipient.getName()));

    return Mono.zip(senderAccountCheckResultMono, recipientAccountCheckResultMono)
        .flatMap(
            accountCheckResultTuple ->
                processAccountOperationForTransferOtherOperation(
                    transferRequest, accountCheckResultTuple, sender, recipient));
  }

  private Mono<UserDetailResponse> processTransferItselfOperation(
      TransferRequest transferRequest, User user) {
    if (transferRequest.getFromCurrency() == transferRequest.getToCurrency()) {
      return getUserDetailResponse(
          user, new ArrayList<>(List.of("Перевести можно только между разными счетами")));
    } else {

      Mono<AccountCheckResult> senderAccountCheckResultMono =
          getAccountCheckResultMono(transferRequest, user, FROM_CURRENCY, NOT_ITSELS_ACCOUNT);
      Mono<AccountCheckResult> recipientAccountCheckResultMono =
          getAccountCheckResultMono(transferRequest, user, TO_CURRENCY, NOT_ITSELS_ACCOUNT);

      return Mono.zip(senderAccountCheckResultMono, recipientAccountCheckResultMono)
          .flatMap(
              accountCheckResultTuple ->
                  processAccountOperationForTransferItselfOperation(
                      transferRequest, accountCheckResultTuple, user));
    }
  }

  private Mono<UserDetailResponse> processAccountOperationForTransferOtherOperation(
      TransferRequest transferRequest,
      Tuple2<AccountCheckResult, AccountCheckResult> accountCheckResultTuple,
      User sender,
      User recipient) {

    AccountCheckResult senderAccountCheckResult = accountCheckResultTuple.getT1();
    AccountCheckResult recipientAccountCheckResult = accountCheckResultTuple.getT2();

    Account senderAccount = senderAccountCheckResult.account();
    Account recipientAccount = recipientAccountCheckResult.account();

    if (senderAccount.getBalance().compareTo(transferRequest.getFromAmount()) < 0) {
      senderAccountCheckResult.errors().add("На счете недостаточно средств");

      return getUserDetailResponse(sender, senderAccountCheckResult.errors());
    } else {
      return applyTransferOtherOperation(
          transferRequest, sender, recipient, senderAccount, recipientAccount);
    }
  }

  private Mono<UserDetailResponse> processAccountOperationForTransferItselfOperation(
          TransferRequest transferRequest,
          Tuple2<AccountCheckResult, AccountCheckResult> accountCheckResultTuple,
          User user) {

    AccountCheckResult senderAccountCheckResult = accountCheckResultTuple.getT1();
    AccountCheckResult recipientAccountCheckResult = accountCheckResultTuple.getT2();

    Account senderAccount = senderAccountCheckResult.account();
    Account recipientAccount = recipientAccountCheckResult.account();

    if (senderAccount.getBalance().compareTo(transferRequest.getFromAmount()) < 0) {
      senderAccountCheckResult.errors().add("На счете недостаточно средств");

      return getUserDetailResponse(user, senderAccountCheckResult.errors());
    } else {
      return applyTransferItselfOperation(
              transferRequest, user, senderAccount, recipientAccount);
    }
  }

  private Mono<UserDetailResponse> applyTransferOtherOperation(
      TransferRequest transferRequest,
      User sender,
      User recipient,
      Account senderAccount,
      Account recipientAccount) {
    senderAccount.setBalance(senderAccount.getBalance().subtract(transferRequest.getFromAmount()));
    recipientAccount.setBalance(recipientAccount.getBalance().add(transferRequest.getToAmount()));
    return accountRepository
        .saveAll(List.of(senderAccount, recipientAccount))
        .then()
        .flatMap(
            empty ->
                getUserDetailResponse(sender, List.of())
                    .flatMap(
                        response ->
                            sendNotificationAfterTransferOtherTransaction(
                                transferRequest, response, sender, recipient)))
        .onErrorResume(
            e ->
                processErrorApplyTransferOtherOperation(
                    sender, senderAccount, recipientAccount, e));
  }

  private Mono<UserDetailResponse> applyTransferItselfOperation(
          TransferRequest transferRequest,
          User user,
          Account senderAccount,
          Account recipientAccount) {
    senderAccount.setBalance(senderAccount.getBalance().subtract(transferRequest.getFromAmount()));
    recipientAccount.setBalance(recipientAccount.getBalance().add(transferRequest.getToAmount()));
    return accountRepository
            .saveAll(List.of(senderAccount, recipientAccount))
            .then()
            .flatMap(
                    empty ->
                            getUserDetailResponse(user, List.of())
                                    .flatMap(
                                            response ->
                                                    sendNotificationAfterTransferItselfTransaction(
                                                            transferRequest, response, user)))
            .onErrorResume(
                    e ->
                            processErrorApplyTransferOtherOperation(
                                    user, senderAccount, recipientAccount, e));
  }

  private Mono<UserDetailResponse> processErrorApplyTransferOtherOperation(
      User sender, Account senderAccount, Account recipientAccount, Throwable e) {
    log.error(
        "Ошибка при сохранении изменений по счетам {}, {}",
        senderAccount.getId(),
        recipientAccount.getId(),
        e);
    List<String> errors = List.of("Ошибка при сохранении изменений по счету. Операция отменена");
    return getUserDetailResponse(sender, errors);
  }

  private Mono<AccountCheckResult> getAccountCheckResultMono(
      TransferRequest transferRequest, User sender, String fieldName, String exceptionMessage) {
    CurrencyEnum currency =
        switch (fieldName) {
          case FROM_CURRENCY -> transferRequest.getFromCurrency();
          case TO_CURRENCY -> transferRequest.getToCurrency();
          default -> throw new IllegalArgumentException("Unknown field: " + fieldName);
        };
    return accountRepository
        .findByUserIdAndCurrency(sender.getId(), currency.name())
        .map(account -> new AccountCheckResult(account, new ArrayList<>()))
        .switchIfEmpty(Mono.error(new AccountNotFoundForCurrencyException(exceptionMessage)));
  }

  private Mono<User> getUserMono(String login) {
    return userRepository
        .findUserByLogin(login)
        .switchIfEmpty(
            Mono.error(
                new UserNotFoundException("Пользователь с логином " + login + " не существует.")));
  }

  private Mono<UserDetailResponse> sendNotificationAfterTransferOtherTransaction(
      TransferRequest transferRequest, UserDetailResponse response, User sender, User recipient) {
    String senderMessage =
        String.format(
            "Перевод суммы %s со счета %s клиенту %s выполнен успешно",
            transferRequest.getFromAmount(),
            transferRequest.getFromCurrency().name(),
            recipient.getName());
    String recipientMessage =
        String.format(
            "Поступление суммы %s на счет %s от клиента %s",
            transferRequest.getToAmount(),
            transferRequest.getToCurrency().name(),
            sender.getName());

    return Mono.zip(
            sendNotification(sender.getEmail(), senderMessage),
            sendNotification(recipient.getEmail(), recipientMessage))
        .thenReturn(response);
  }

  private Mono<UserDetailResponse> sendNotificationAfterTransferItselfTransaction(
          TransferRequest transferRequest, UserDetailResponse response, User user) {
    String userMessage =
            String.format(
                    "Перевод между счетами выполнен успешно. Списано со счета %s: %s. Зачисленно на счет %s: %s.",
                    transferRequest.getFromCurrency().name(),
                    transferRequest.getFromAmount(),
                    transferRequest.getToCurrency().name(),
                    transferRequest.getToAmount());
    return sendNotification(user.getEmail(), userMessage)
            .thenReturn(response);
  }

  private Mono<UserDetailResponse> updateExistingUser(User existingUser, UserRequest userRequest) {
    return updateUserDataAndAccounts(existingUser, userRequest)
        .flatMap(updateErrors -> getUserDetailResponse(existingUser, updateErrors));
  }

  private Mono<List<String>> updateUserDataAndAccounts(User existingUser, UserRequest userRequest) {
    return updateAccounts(existingUser, userRequest)
        .zipWith(updateUserData(existingUser, userRequest))
        .map(
            tuple -> {
              List<String> updateUserAccountErrors = tuple.getT1();
              List<String> updateUserDataErrors = tuple.getT2();
              List<String> summaryUpdateErrors = new ArrayList<>(updateUserAccountErrors);
              summaryUpdateErrors.addAll(updateUserDataErrors);
              return summaryUpdateErrors;
            });
  }

  private Mono<List<String>> updateAccounts(User existingUser, UserRequest userRequest) {
    return accountRepository
        .findUserCurrencyAccounts(userRequest.getLogin())
        .flatMap(account -> processAccounts(existingUser, userRequest, account))
        .collectList();
  }

  private Mono<List<String>> updateUserData(User existingUser, UserRequest userRequest) {
    List<String> updateUserDataErrors = new ArrayList<>();
    // Проверяем Фамилия Имя
    if (userRequest.getName().isEmpty()) {
      updateUserDataErrors.add("Заполните поле Фамилия Имя");
    } else {
      existingUser.setName(userRequest.getName());
    }

    // Проверяем email
    if (userRequest.getEmail().isEmpty()) {
      updateUserDataErrors.add("Заполните электронную почту");
    } else {
      existingUser.setEmail(userRequest.getEmail());
    }

    // Проверяем дату рождения
    if (calculateAge(userRequest.getBirthdate()) == 0) {
      updateUserDataErrors.add("Дата рождения не может быть пустой или позже текущей даты");
    } else if (calculateAge(userRequest.getBirthdate()) < 18) {
      updateUserDataErrors.add("Вам должно быть больше 18 лет");
    } else {
      existingUser.setBirthdate(userRequest.getBirthdate());
    }
    return userRepository.save(existingUser).thenReturn(updateUserDataErrors);
  }

  private int calculateAge(LocalDate birthdate) {
    if (birthdate == null || birthdate.isAfter(LocalDate.now())) {
      return 0; // или выбросить исключение
    }
    return Period.between(birthdate, LocalDate.now()).getYears();
  }

  private Mono<String> processAccounts(
      User existingUser, UserRequest userRequest, AccountInfoRow account) {
    CurrencyEnum currency = account.getCurrency();
    if (accountNotExists(account, userRequest)) {
      return createNewAccount(existingUser, currency);
    }
    if (accountForDeleteNotEmpty(account, userRequest)) {
      return Mono.just(String.format("Баланс на счету %s не равен 0", currency.name()));
    } else if (accountForDeleteIsEmpty(account, userRequest)) {
      return accountRepository
          .deleteByUserIdAndCurrency(existingUser.getId(), currency.name())
          .then(Mono.empty());
    }
    return Mono.empty();
  }

  private boolean accountNotExists(AccountInfoRow account, UserRequest userRequest) {
    return !account.getExists() && userRequest.getAccounts().contains(account.getCurrency());
  }

  private boolean accountForDeleteNotEmpty(AccountInfoRow account, UserRequest userRequest) {
    return account.getExists()
        && !userRequest.getAccounts().contains(account.getCurrency())
        && account.getValue().compareTo(BigDecimal.ZERO) != 0;
  }

  private boolean accountForDeleteIsEmpty(AccountInfoRow account, UserRequest userRequest) {
    return account.getExists()
        && !userRequest.getAccounts().contains(account.getCurrency())
        && account.getValue().compareTo(BigDecimal.ZERO) == 0;
  }

  private Mono<String> createNewAccount(User existingUser, CurrencyEnum currency) {
    return accountRepository
        .save(
            Account.builder()
                .userId(existingUser.getId())
                .currency(currency.name())
                .balance(BigDecimal.ZERO)
                .build())
        .then(Mono.empty());
  }

  private Mono<UserDetailResponse> mapToUserDetailResponse(User user, Role role) {
    return accountRepository
        .findUserCurrencyAccounts(user.getLogin())
        .collectList()
        .map(
            accounts ->
                UserDetailResponse.builder()
                    .login(user.getLogin())
                    .password(user.getPassword())
                    .name(user.getName())
                    .email(user.getEmail())
                    .birthdate(user.getBirthdate())
                    .roles(List.of(role.getRoleName()))
                    .accounts(accounts)
                    .build());
  }

  private Mono<Role> findOrCreateClientRole() {
    return roleRepository
        .findByRoleName("client")
        .switchIfEmpty(
            roleRepository.save(
                new Role(null, "client", "Клиент — зарегистрированный пользователь")));
  }

  private Mono<User> saveUserAsClient(Role role, UserRequest userRequest) {
    return userRepository
        .save(userMapper.mapToUserEntity(userRequest))
        .flatMap(
            savedUser ->
                userRoleRepository
                    .save(
                        UserRole.builder()
                            .userId(savedUser.getId())
                            .roleId(role.getId().longValue())
                            .build())
                    .thenReturn(savedUser));
  }

  private Mono<Void> sendNotification(String email, String message) {
    NotificationRequest newNotification =
        new NotificationRequest(
            email, message, NotificationRequest.ApplicationEnum.ACCOUNTS_SERVICE);
    return notificationsServiceApi
        .notificationCreate(newNotification)
        .retryWhen(
            Retry.max(1) // Повторить один раз при возникновении ошибки
                .filter(
                    throwable ->
                        (throwable instanceof WebClientResponseException
                                && ((WebClientResponseException) throwable).getStatusCode()
                                    == HttpStatus.INTERNAL_SERVER_ERROR)
                            || throwable instanceof WebClientRequestException))
        .onErrorResume(
            e -> {
              log.warn("Не удалось отправить уведомление: {}", e.getMessage());
              return Mono.empty(); // Подавляем ошибку
            })
        .then(); // Возвращаем Mono<Void>;
  }
}
