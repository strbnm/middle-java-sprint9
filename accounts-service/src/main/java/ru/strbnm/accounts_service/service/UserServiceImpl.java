package ru.strbnm.accounts_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import ru.strbnm.accounts_service.domain.*;
import ru.strbnm.accounts_service.dto.AccountCheckResult;
import ru.strbnm.accounts_service.entity.*;
import ru.strbnm.accounts_service.exception.AccountNotFoundForCurrencyException;
import ru.strbnm.accounts_service.exception.UserAlreadyExistsException;
import ru.strbnm.accounts_service.exception.UserNotFoundException;
import ru.strbnm.accounts_service.mapper.UserMapper;
import ru.strbnm.accounts_service.repository.*;
import ru.strbnm.kafka.dto.NotificationMessage;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final AccountRepository accountRepository;
  private final UserMapper userMapper;
  private final Pattern BCRYPT_PATTERN =
      Pattern.compile("\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}");

  private final String FROM_CURRENCY = "fromCurrency";
  private final String TO_CURRENCY = "toCurrency";
  private final String NOT_ITSELF_ACCOUNT = "У Вас отсутствует счет в выбранной валюте";
  private final String NOT_FOUND_USER = "Пользователь с логином %s не существует";

  private final KafkaTemplate<String, NotificationMessage> kafkaTemplate;

  @Autowired
  public UserServiceImpl(
          UserRepository userRepository,
          RoleRepository roleRepository,
          UserRoleRepository userRoleRepository,
          AccountRepository accountRepository,
          UserMapper userMapper, KafkaTemplate<String, NotificationMessage> kafkaTemplate) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
    this.accountRepository = accountRepository;
    this.userMapper = userMapper;
      this.kafkaTemplate = kafkaTemplate;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Override
  public Mono<AccountOperationResponse> createUser(UserRequest userRequest) {
    return checkUserRequest(userRequest)
        .flatMap(
            createUserDataErrors -> {
              if (!createUserDataErrors.isEmpty()) {
                return getAccountOperationResponse(
                    AccountOperationResponse.OperationStatusEnum.FAILED, createUserDataErrors);
              } else {
                return processCreateUser(userRequest);
              }
            });
  }

  private Mono<AccountOperationResponse> processCreateUser(UserRequest userRequest) {
    return userRepository
        .findUserByLogin(userRequest.getLogin())
        .flatMap(this::throwUserAlreadyExistsException)
        .switchIfEmpty(
            findOrCreateClientRole()
                .flatMap(
                    role ->
                        saveUserAsClient(role, userRequest)
                            .flatMap(
                                user ->
                                    sendNotification(
                                            user.getId(),
                                            user.getEmail(),
                                            "Вы успешно зарегистрированы.")
                                        .then(
                                            getAccountOperationResponse(
                                                AccountOperationResponse.OperationStatusEnum
                                                    .SUCCESS,
                                                List.of()))))
                .onErrorResume(
                    e -> {
                      log.error("Ошибка при создании пользователя {}", userRequest, e);
                      List<String> errors =
                          List.of("Ошибка регистрации. Повторите еще раз чуть позже.");
                      return getAccountOperationResponse(
                          AccountOperationResponse.OperationStatusEnum.FAILED, errors);
                    }));
  }

  private Mono<AccountOperationResponse> throwUserAlreadyExistsException(User existingUser) {
    return Mono.<AccountOperationResponse>error(
        new UserAlreadyExistsException(
            "Пользователь с таким логином уже существует: " + existingUser.getLogin()));
  }

  private Mono<List<String>> checkUserRequest(UserRequest userRequest) {
    log.info("checkUserRequest: {}", userRequest);
    List<String> createUserDataErrors = new ArrayList<>();
    // Проверяем Фамилия Имя
    if (userRequest.getName().isEmpty()) {
      createUserDataErrors.add("Заполните поле Фамилия Имя");
    }
    // Проверяем email
    if (userRequest.getEmail().isEmpty()) {
      createUserDataErrors.add("Заполните электронную почту");
    }
    // Проверяем дату рождения
    if (calculateAge(userRequest.getBirthdate()) == 0) {
      createUserDataErrors.add("Дата рождения не может быть пустой или позже текущей даты");
    } else if (calculateAge(userRequest.getBirthdate()) < 18) {
      createUserDataErrors.add("Вам должно быть больше 18 лет");
    }
    return Mono.just(createUserDataErrors);
  }

  private Mono<Role> findOrCreateClientRole() {
    return roleRepository
        .findByRoleName("ROLE_CLIENT")
        .switchIfEmpty(
            roleRepository.save(
                new Role(null, "ROLE_CLIENT", "Клиент — зарегистрированный пользователь")));
  }

  private Mono<User> saveUserAsClient(Role role, UserRequest userRequest) {
    log.info("saveUserAsClient: {}", userRequest);
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

  private Mono<Void> sendNotification(Long userId, String email, String message) {
    NotificationMessage notificationMessage =
        NotificationMessage.builder()
            .email(email)
            .message(message)
            .application("accounts-service")
            .build();
    log.info("sendNotification: {}", notificationMessage);
    return Mono.fromFuture(() ->
                    kafkaTemplate.send("notifications", notificationMessage)
            )
            .doOnSuccess(result -> {
              RecordMetadata metadata = result.getRecordMetadata();
              log.info("Сообщение отправлено. Topic = {}, partition = {}, offset = {}",
                      metadata.topic(), metadata.partition(), metadata.offset());
            })
            .doOnError(e -> log.error("Ошибка при отправке сообщения", e))
            .then(); // Mono<Void>
  }

  private Mono<AccountOperationResponse> getAccountOperationResponse(
      AccountOperationResponse.OperationStatusEnum operationStatus, List<String> errors) {
    return Mono.just(new AccountOperationResponse(operationStatus, errors));
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Override
  public Mono<AccountOperationResponse> updateUser(UserRequest userRequest) {
    return userRepository
        .findUserByLogin(userRequest.getLogin())
        .switchIfEmpty(
            Mono.error(
                new UserNotFoundException(String.format(NOT_FOUND_USER, userRequest.getLogin()))))
        .flatMap(
            existingUser ->
                updateExistingUser(existingUser, userRequest)
                    .flatMap(
                        AccountOperationResponse ->
                            sendNotificationAfterUpdate(
                                AccountOperationResponse,
                                existingUser.getEmail(),
                                existingUser.getId())));
  }

  private Mono<AccountOperationResponse> sendNotificationAfterUpdate(
      AccountOperationResponse AccountOperationResponse, String email, Long userId) {
    StringBuilder msg;
    if (AccountOperationResponse.getErrors().isEmpty()) {
      msg = new StringBuilder("Информация аккаунта успешно обновлена.");
    } else {
      msg =
          new StringBuilder(
              "Информация аккаунта не обновлена или обновлена частично.\nОшибки в процессе обновления:");
      for (String error : AccountOperationResponse.getErrors()) {
        msg.append("\n").append(error);
      }
    }
    return sendNotification(userId, email, msg.toString()).thenReturn(AccountOperationResponse);
  }

  @Override
  public Flux<UserListResponseInner> getUserList() {
    return userRepository
        .findAllOrderByNameAsc()
        .map(user -> new UserListResponseInner(user.getLogin(), user.getName()));
  }

  @Override
  public Mono<UserDetailResponse> getUserByLogin(String login) {
    return userRepository
        .findUserByLogin(login)
        .switchIfEmpty(Mono.error(new UserNotFoundException(String.format(NOT_FOUND_USER, login))))
        .flatMap(
            user ->
                Mono.zip(
                        userRepository.getUserWithRolesByLogin(login),
                        accountRepository.findUserCurrencyAccounts(login).collectList())
                    .map(
                        tuple2 -> {
                          UserDetailResponse existingUser = tuple2.getT1();
                          List<AccountInfoRow> accounts = tuple2.getT2();
                          existingUser.accounts(accounts);
                          return existingUser;
                        }));
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @Override
  public Mono<AccountOperationResponse> updateUserPassword(
      UserPasswordRequest userPasswordRequest) {
    return userRepository
        .findUserByLogin(userPasswordRequest.getLogin())
        .switchIfEmpty(
            Mono.error(
                new UserNotFoundException(
                    String.format(NOT_FOUND_USER, userPasswordRequest.getLogin()))))
        .flatMap(
            existingUser -> {
              if (this.BCRYPT_PATTERN.matcher(userPasswordRequest.getNewPassword()).matches()) {
                existingUser.setPassword(userPasswordRequest.getNewPassword());
                return processUpdateUserPassword(existingUser);
              } else {
                log.warn(
                    "Ошибка при сохранении изменений пароля для userId {}", existingUser.getId());
                String msg = "Ошибка при сохранении изменений пароля. Операция отменена";
                return sendNotification(existingUser.getId(), existingUser.getEmail(), msg)
                    .then(
                        getAccountOperationResponse(
                            AccountOperationResponse.OperationStatusEnum.FAILED, List.of(msg)));
              }
            });
  }

  private Mono<AccountOperationResponse> processUpdateUserPassword(User existingUser) {
    return userRepository
        .save(existingUser)
        .flatMap(
            user ->
                sendNotification(user.getId(), user.getEmail(), "Пароль успешно обновлен")
                    .then(
                        getAccountOperationResponse(
                            AccountOperationResponse.OperationStatusEnum.SUCCESS, List.of())))
        .onErrorResume(
            e -> {
              log.error(
                  "Ошибка при сохранении изменений пароля для userId {}", existingUser.getId(), e);
              List<String> errors =
                  List.of("Ошибка при сохранении изменений пароля. Операция отменена");
              return getAccountOperationResponse(
                  AccountOperationResponse.OperationStatusEnum.FAILED, errors);
            });
  }

  @Transactional(isolation = Isolation.SERIALIZABLE)
  @Override
  public Mono<AccountOperationResponse> cashOperation(CashRequest cashRequest, String login) {
    return userRepository
        .findUserByLogin(login)
        .switchIfEmpty(Mono.error(new UserNotFoundException(String.format(NOT_FOUND_USER, login))))
        .flatMap(
            existingUser ->
                checkCashTransaction(cashRequest, existingUser)
                    .flatMap(
                        accountCheckResult -> {
                          if (!accountCheckResult.errors().isEmpty()
                              || accountCheckResult.account() == null) {
                            return getAccountOperationResponse(
                                AccountOperationResponse.OperationStatusEnum.FAILED,
                                accountCheckResult.errors());
                          } else {
                            return getAccountOperationResponseAfterCashTransaction(
                                cashRequest, existingUser, accountCheckResult);
                          }
                        }));
  }

  private Mono<AccountOperationResponse> getAccountOperationResponseAfterCashTransaction(
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
                sendNotificationAfterCashTransaction(
                        cashRequest, existingUser.getEmail(), existingUser.getId())
                    .then(
                        getAccountOperationResponse(
                            AccountOperationResponse.OperationStatusEnum.SUCCESS, List.of())))
        .onErrorResume(
            e -> {
              log.error("Ошибка при сохранении изменений по счету {}", account.getId(), e);
              List<String> errors =
                  List.of(
                      "Ошибка при сохранении изменений по счету"
                          + cashRequest.getCurrency().name()
                          + ". Операция отменена");
              return getAccountOperationResponse(
                  AccountOperationResponse.OperationStatusEnum.FAILED, errors);
            });
  }

  private Mono<Void> sendNotificationAfterCashTransaction(
      CashRequest cashRequest, String email, Long userId) {

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
    return sendNotification(userId, email, msg.toString()).then();
  }

  private Mono<AccountCheckResult> checkCashTransaction(CashRequest cashRequest, User user) {
    return accountRepository
        .findByUserIdAndCurrency(user.getId(), cashRequest.getCurrency().name())
        .map(account -> new AccountCheckResult(account, new ArrayList<>()))
        .switchIfEmpty(
            Mono.just(new AccountCheckResult(null, new ArrayList<>(List.of(NOT_ITSELF_ACCOUNT)))))
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
  public Mono<AccountOperationResponse> transferOperation(
      TransferRequest transferRequest, String login) {
    if (!login.equals(transferRequest.getToLogin())) {
      return transferOtherOperation(transferRequest, login);
    } else {
      return transferItselfOperation(transferRequest, login);
    }
  }

  private Mono<AccountOperationResponse> transferOtherOperation(
      TransferRequest transferRequest, String login) {

    Mono<User> senderMono = getUserMono(login);

    Mono<User> recipientMono = getUserMono(transferRequest.getToLogin());

    return Mono.zip(senderMono, recipientMono)
        .flatMap(tuple -> processTransferOtherOperation(transferRequest, tuple));
  }

  private Mono<AccountOperationResponse> transferItselfOperation(
      TransferRequest transferRequest, String login) {

    return getUserMono(login)
        .flatMap(user -> processTransferItselfOperation(transferRequest, user));
  }

  private Mono<AccountOperationResponse> processTransferOtherOperation(
      TransferRequest transferRequest, Tuple2<User, User> userTuple) {

    User sender = userTuple.getT1();
    User recipient = userTuple.getT2();

    Mono<AccountCheckResult> senderAccountCheckResultMono =
        getAccountCheckResultMono(transferRequest, sender, FROM_CURRENCY, NOT_ITSELF_ACCOUNT);

    Mono<AccountCheckResult> recipientAccountCheckResultMono =
        getAccountCheckResultMono(
            transferRequest,
            recipient,
            TO_CURRENCY,
            String.format("У клиента %s отсутствует счет в выбранной валюте", recipient.getName()));

    return Mono.zip(senderAccountCheckResultMono, recipientAccountCheckResultMono)
        .flatMap(
            accountCheckResultTuple ->
                processAccountOperationForTransferOtherOperation(
                    transferRequest, accountCheckResultTuple, sender, recipient));
  }

  private Mono<AccountOperationResponse> processTransferItselfOperation(
      TransferRequest transferRequest, User user) {
    if (transferRequest.getFromCurrency() == transferRequest.getToCurrency()) {
      return getAccountOperationResponse(
          AccountOperationResponse.OperationStatusEnum.FAILED,
          new ArrayList<>(List.of("Перевести можно только между разными счетами")));
    } else {

      Mono<AccountCheckResult> senderAccountCheckResultMono =
          getAccountCheckResultMono(transferRequest, user, FROM_CURRENCY, NOT_ITSELF_ACCOUNT);
      Mono<AccountCheckResult> recipientAccountCheckResultMono =
          getAccountCheckResultMono(transferRequest, user, TO_CURRENCY, NOT_ITSELF_ACCOUNT);

      return Mono.zip(senderAccountCheckResultMono, recipientAccountCheckResultMono)
          .flatMap(
              accountCheckResultTuple ->
                  processAccountOperationForTransferItselfOperation(
                      transferRequest, accountCheckResultTuple, user));
    }
  }

  private Mono<AccountOperationResponse> processAccountOperationForTransferOtherOperation(
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

      return getAccountOperationResponse(
          AccountOperationResponse.OperationStatusEnum.FAILED, senderAccountCheckResult.errors());
    } else {
      return applyTransferOtherOperation(
          transferRequest, sender, recipient, senderAccount, recipientAccount);
    }
  }

  private Mono<AccountOperationResponse> processAccountOperationForTransferItselfOperation(
      TransferRequest transferRequest,
      Tuple2<AccountCheckResult, AccountCheckResult> accountCheckResultTuple,
      User user) {

    AccountCheckResult senderAccountCheckResult = accountCheckResultTuple.getT1();
    AccountCheckResult recipientAccountCheckResult = accountCheckResultTuple.getT2();

    Account senderAccount = senderAccountCheckResult.account();
    Account recipientAccount = recipientAccountCheckResult.account();

    if (senderAccount.getBalance().compareTo(transferRequest.getFromAmount()) < 0) {
      senderAccountCheckResult.errors().add("На счете недостаточно средств");

      return getAccountOperationResponse(
          AccountOperationResponse.OperationStatusEnum.FAILED, senderAccountCheckResult.errors());
    } else {
      return applyTransferItselfOperation(transferRequest, user, senderAccount, recipientAccount);
    }
  }

  private Mono<AccountOperationResponse> applyTransferOtherOperation(
      TransferRequest transferRequest,
      User sender,
      User recipient,
      Account senderAccount,
      Account recipientAccount) {

    senderAccount.setBalance(senderAccount.getBalance().subtract(transferRequest.getFromAmount()));
    recipientAccount.setBalance(recipientAccount.getBalance().add(transferRequest.getToAmount()));

    return accountRepository
        .saveAll(List.of(senderAccount, recipientAccount))
        .then(sendNotificationAfterTransferOtherTransaction(transferRequest, sender, recipient))
        .then(
            getAccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.SUCCESS, List.of()))
        .onErrorResume(
            e -> processErrorApplyTransferOtherOperation(senderAccount, recipientAccount, e));
  }

  private Mono<AccountOperationResponse> applyTransferItselfOperation(
      TransferRequest transferRequest, User user, Account senderAccount, Account recipientAccount) {
    senderAccount.setBalance(senderAccount.getBalance().subtract(transferRequest.getFromAmount()));
    recipientAccount.setBalance(recipientAccount.getBalance().add(transferRequest.getToAmount()));
    return accountRepository
        .saveAll(List.of(senderAccount, recipientAccount))
        .then(sendNotificationAfterTransferItselfTransaction(transferRequest, user))
        .then(
            getAccountOperationResponse(
                AccountOperationResponse.OperationStatusEnum.SUCCESS, List.of()))
        .onErrorResume(
            e -> processErrorApplyTransferOtherOperation(senderAccount, recipientAccount, e));
  }

  private Mono<AccountOperationResponse> processErrorApplyTransferOtherOperation(
      Account senderAccount, Account recipientAccount, Throwable e) {
    log.error(
        "Ошибка при сохранении изменений по счетам {}, {}",
        senderAccount.getId(),
        recipientAccount.getId(),
        e);
    List<String> errors = List.of("Ошибка при сохранении изменений по счету. Операция отменена");
    return getAccountOperationResponse(AccountOperationResponse.OperationStatusEnum.FAILED, errors);
  }

  private Mono<AccountCheckResult> getAccountCheckResultMono(
      TransferRequest transferRequest, User sender, String fieldName, String exceptionMessage) {
    AccountCurrencyEnum currency =
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
        .switchIfEmpty(Mono.error(new UserNotFoundException(String.format(NOT_FOUND_USER, login))));
  }

  private Mono<Void> sendNotificationAfterTransferOtherTransaction(
      TransferRequest transferRequest, User sender, User recipient) {
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
            sendNotification(sender.getId(), sender.getEmail(), senderMessage),
            sendNotification(recipient.getId(), recipient.getEmail(), recipientMessage))
        .then();
  }

  private Mono<Void> sendNotificationAfterTransferItselfTransaction(
      TransferRequest transferRequest, User user) {
    String userMessage =
        String.format(
            "Перевод между счетами выполнен успешно. Списано со счета %s: %s. Зачисленно на счет %s: %s.",
            transferRequest.getFromCurrency().name(),
            transferRequest.getFromAmount(),
            transferRequest.getToCurrency().name(),
            transferRequest.getToAmount());
    return sendNotification(user.getId(), user.getEmail(), userMessage).then();
  }

  private Mono<AccountOperationResponse> updateExistingUser(
      User existingUser, UserRequest userRequest) {
    return updateUserDataAndAccounts(existingUser, userRequest)
        .flatMap(
            updateErrors -> {
              if (!updateErrors.isEmpty())
                return getAccountOperationResponse(
                    AccountOperationResponse.OperationStatusEnum.FAILED, updateErrors);
              return getAccountOperationResponse(
                  AccountOperationResponse.OperationStatusEnum.SUCCESS, updateErrors);
            });
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
    AccountCurrencyEnum currency = account.getCurrency();
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

  private Mono<String> createNewAccount(User existingUser, AccountCurrencyEnum currency) {
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
            accounts -> {
              UserDetailResponse response =
                  new UserDetailResponse(
                      user.getLogin(),
                      user.getPassword(),
                      user.getName(),
                      user.getEmail(),
                      user.getBirthdate(),
                      List.of(role.getRoleName()));
              response.setAccounts(accounts);
              return response;
            });
  }
}
