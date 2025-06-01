package ru.strbnm.cash_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.strbnm.cash_service.client.accounts.api.AccountsServiceApi;
import ru.strbnm.cash_service.client.accounts.domain.AccountCurrencyEnum;
import ru.strbnm.cash_service.client.accounts.domain.AccountOperationResponse;
import ru.strbnm.cash_service.client.accounts.domain.CashRequest;
import ru.strbnm.cash_service.client.accounts.domain.UserDetailResponse;
import ru.strbnm.cash_service.client.blocker.api.BlockerServiceApi;
import ru.strbnm.cash_service.client.blocker.domain.BlockerCurrencyEnum;
import ru.strbnm.cash_service.client.blocker.domain.CheckCashTransactionRequest;
import ru.strbnm.cash_service.client.blocker.domain.CheckTransactionResponse;
import ru.strbnm.cash_service.domain.CashOperationRequest;
import ru.strbnm.cash_service.domain.CashOperationResponse;
import ru.strbnm.cash_service.entity.CashTransactionInfo;
import ru.strbnm.cash_service.entity.OutboxNotification;
import ru.strbnm.cash_service.exception.AccountsServiceException;
import ru.strbnm.cash_service.exception.BlockerServiceException;
import ru.strbnm.cash_service.exception.CashOperationException;
import ru.strbnm.cash_service.exception.UnavailabilityAccountsServiceException;
import ru.strbnm.cash_service.repository.CashTransactionInfoRepository;
import ru.strbnm.cash_service.repository.OutboxNotificationRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class CashServiceImpl implements CashService {

  private final AccountsServiceApi accountsServiceApi;
  private final BlockerServiceApi blockerServiceApi;
  private final CashTransactionInfoRepository cashTransactionInfoRepository;
  private final OutboxNotificationRepository outboxNotificationRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  public CashServiceImpl(
      @Qualifier("accountsServiceApi") AccountsServiceApi accountsServiceApi,
      BlockerServiceApi blockerServiceApi,
      CashTransactionInfoRepository cashTransactionInfoRepository,
      OutboxNotificationRepository outboxNotificationRepository) {
    this.accountsServiceApi = accountsServiceApi;
    this.blockerServiceApi = blockerServiceApi;
    this.cashTransactionInfoRepository = cashTransactionInfoRepository;
    this.outboxNotificationRepository = outboxNotificationRepository;
  }

    @Override
    public Mono<CashOperationResponse> processCashTransaction(CashOperationRequest cashOperationRequest) {
        log.info("Получен запрос: {}", cashOperationRequest);
        CashTransactionInfo cashTransactionInfo = buildCashTransactionInfo(cashOperationRequest);

        return cashTransactionInfoRepository.save(cashTransactionInfo)
                .flatMap(savedInfo ->
                        Mono.zip(getUserDetailResponseMono(savedInfo.getLogin()), checkTransaction(savedInfo))
                                .flatMap(tuple -> handleCheckTransaction(tuple.getT1(), tuple.getT2(), savedInfo))
                )
                .onErrorResume(e -> handleProcessingError(cashTransactionInfo, e));
    }

    private CashTransactionInfo buildCashTransactionInfo(CashOperationRequest request) {
        return CashTransactionInfo.builder()
                .login(request.getLogin())
                .currency(request.getCurrency().name())
                .amount(request.getAmount())
                .action(request.getAction().name())
                .build();
    }

    private Mono<CashOperationResponse> handleCheckTransaction(UserDetailResponse user, CheckTransactionResponse check, CashTransactionInfo info) {
      log.info("Ответ blocked-service: {}", check);
      log.info("Пользователь: {}", user);
      if (check.getIsBlocked()) {
            assert check.getReason() != null;
            return updateBlockedTransactionAndNotify(info, user, "Блокировка операции: " + check.getReason(),
                    List.of(check.getReason()));
        }

        info.setBlocked(false);
        info.setUpdatedAt(Instant.now().getEpochSecond());
        return cashTransactionInfoRepository.save(info)
                .flatMap(updated -> performAccountOperation(updated, user));
    }

    private Mono<CashOperationResponse> performAccountOperation(CashTransactionInfo info, UserDetailResponse user) {
        CashRequest request = buildCashRequest(info);

        return getAccountOperationResponse(request, user.getLogin())
                .flatMap(response -> {
                    log.info("Ответ сервиса аккаунтов: {}", response);
                    boolean isSuccess = response.getOperationStatus() == AccountOperationResponse.OperationStatusEnum.SUCCESS;
                    String msg = isSuccess
                            ? buildSuccessMessage(info)
                            : "Отмена операции c наличными. Список ошибок: " + response.getErrors();

                    info.setSuccess(isSuccess);
                    info.setUpdatedAt(Instant.now().getEpochSecond());

                    return cashTransactionInfoRepository.save(info)
                            .flatMap(saved -> saveOutboxNotification(saved.getId(), user.getEmail(), msg)
                                    .then(getCashOperationResponse(
                                            isSuccess ? CashOperationResponse.OperationStatusEnum.SUCCESS : CashOperationResponse.OperationStatusEnum.FAILED,
                                            isSuccess ? List.of() : response.getErrors()
                                    )));
                });
    }

    private Mono<CashOperationResponse> updateBlockedTransactionAndNotify(
            CashTransactionInfo info, UserDetailResponse user, String message, List<String> errors) {
        info.setBlocked(true);
        info.setSuccess(false);
        info.setUpdatedAt(Instant.now().getEpochSecond());
        return cashTransactionInfoRepository.save(info)
                .flatMap(saved -> saveOutboxNotification(saved.getId(), user.getEmail(), message)
                        .then(getCashOperationResponse(CashOperationResponse.OperationStatusEnum.FAILED, errors)));
    }

    private Mono<CashOperationResponse> handleProcessingError(CashTransactionInfo info, Throwable error) {
        info.setBlocked(false);
        info.setSuccess(false);
        info.setUpdatedAt(Instant.now().getEpochSecond());
        log.error("Ошибки в процессе обработки:", error);
        return cashTransactionInfoRepository.save(info)
                .flatMap(saved -> {
                    if (error instanceof CashOperationException err) return Mono.error(err);
                    String message = "Ошибка при обработке операции с наличными: " + error.getMessage();
                    return saveOutboxNotification(saved.getId(), info.getLogin(), message)
                            .then(getCashOperationResponse(CashOperationResponse.OperationStatusEnum.FAILED, List.of(message)));
                });
    }

    private CashRequest buildCashRequest(CashTransactionInfo info) {
        CashRequest request = new CashRequest();
        request.setCurrency(AccountCurrencyEnum.fromValue(info.getCurrency()));
        request.setAction(CashRequest.ActionEnum.fromValue(info.getAction()));
        request.setAmount(info.getAmount());
        return request;
    }

    private String buildSuccessMessage(CashTransactionInfo info) {
        return "Успешная операция " +
                (info.getAction().equals("GET") ? "снятия наличных в размере " : "пополнения счета на сумму ") +
                info.getAmount() + info.getCurrency();
    }

    private Mono<AccountOperationResponse> getAccountOperationResponse(CashRequest cashRequest, String login) {
        return accountsServiceApi.cashTransaction(login, cashRequest)
                .retryWhen(
                        Retry.max(1)
                                .filter(
                                        throwable ->
                                                (throwable instanceof WebClientResponseException
                                                        && ((WebClientResponseException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                                                        || throwable instanceof WebClientRequestException)
                                .onRetryExhaustedThrow(
                                        (spec, signal) ->
                                                new UnavailabilityAccountsServiceException("Сервис аккаунтов временно недоступен.")))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                        try {
                            AccountOperationResponse response = objectMapper
                                    .readValue(ex.getResponseBodyAsString(), AccountOperationResponse.class);
                            return Mono.just(response);
                        } catch (IOException e) {
                            return Mono.error(new AccountsServiceException("Не удалось прочитать тело ответа: " + e.getMessage()));
                        }
                    }
                    return Mono.error(new AccountsServiceException("Ошибка при обработке транзакции: " + ex.getMessage()));
                });
    }

    private Mono<CheckTransactionResponse> checkTransaction(CashTransactionInfo cashTransactionInfo) {
        CheckCashTransactionRequest request = new CheckCashTransactionRequest();
        request.setTransactionId(cashTransactionInfo.getId());
        request.setCurrency(BlockerCurrencyEnum.fromValue(cashTransactionInfo.getCurrency()));
        request.setAmount(cashTransactionInfo.getAmount());
        request.setActionType(CheckCashTransactionRequest.ActionTypeEnum.valueOf(cashTransactionInfo.getAction()));
        return getCheckTransactionResponse(request);
    }

    private Mono<CheckTransactionResponse> getCheckTransactionResponse(CheckCashTransactionRequest request) {
        return blockerServiceApi.checkCashTransaction(request)
                .retryWhen(
                        Retry.max(1)
                                .filter(
                                        throwable ->
                                                (throwable instanceof WebClientResponseException
                                                        && ((WebClientResponseException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                                                        || throwable instanceof WebClientRequestException)
                                .onRetryExhaustedThrow(
                                        (spec, signal) ->
                                                new UnavailabilityAccountsServiceException("Сервис блокировок временно недоступен.")))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(
                        new BlockerServiceException("Ошибка при проверке транзакции: " + ex.getMessage())));
    }

    private Mono<UserDetailResponse> getUserDetailResponseMono(String login) {
        return accountsServiceApi.getUser(login)
                .retryWhen(
                        Retry.max(1)
                                .filter(
                                        throwable ->
                                                (throwable instanceof WebClientResponseException
                                                        && ((WebClientResponseException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                                                        || throwable instanceof WebClientRequestException)
                                .onRetryExhaustedThrow(
                                        (spec, signal) ->
                                                new UnavailabilityAccountsServiceException("Сервис аккаунтов временно недоступен.")))
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(
                        new AccountsServiceException("Ошибка при получении данных клиента: " + ex.getMessage())));
    }

    private Mono<Void> saveOutboxNotification(Long transactionId, String email, String message) {
        OutboxNotification outboxNotification = OutboxNotification.builder()
                .transactionId(transactionId)
                .email(email)
                .message(message)
                .build();
        return outboxNotificationRepository.save(outboxNotification).then();
    }

    private Mono<CashOperationResponse> getCashOperationResponse(CashOperationResponse.OperationStatusEnum status, List<String> errors) {
        return Mono.just(new CashOperationResponse(status, errors));
    }

}
