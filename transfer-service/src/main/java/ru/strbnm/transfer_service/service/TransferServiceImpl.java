package ru.strbnm.transfer_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.strbnm.kafka.dto.NotificationMessage;
import ru.strbnm.transfer_service.client.accounts.api.AccountsServiceApi;
import ru.strbnm.transfer_service.client.accounts.domain.*;
import ru.strbnm.transfer_service.client.blocker.api.BlockerServiceApi;
import ru.strbnm.transfer_service.client.blocker.domain.BlockerCurrencyEnum;
import ru.strbnm.transfer_service.client.blocker.domain.CheckTransactionResponse;
import ru.strbnm.transfer_service.client.blocker.domain.CheckTransferTransactionRequest;
import ru.strbnm.transfer_service.client.exchange.api.ExchangeServiceApi;
import ru.strbnm.transfer_service.client.exchange.domain.ConvertRequest;
import ru.strbnm.transfer_service.client.exchange.domain.ConvertedAmount;
import ru.strbnm.transfer_service.domain.TransferOperationRequest;
import ru.strbnm.transfer_service.domain.TransferOperationResponse;
import ru.strbnm.transfer_service.entity.TransferTransactionInfo;
import ru.strbnm.transfer_service.exception.AccountsServiceException;
import ru.strbnm.transfer_service.exception.BlockerServiceException;
import ru.strbnm.transfer_service.exception.CashOperationException;
import ru.strbnm.transfer_service.exception.UnavailabilityAccountsServiceException;
import ru.strbnm.transfer_service.repository.TransferTransactionInfoRepository;

@Slf4j
@Service
public class TransferServiceImpl implements TransferService {

  private final AccountsServiceApi accountsServiceApi;
  private final BlockerServiceApi blockerServiceApi;
  private final ExchangeServiceApi exchangeServiceApi;
  private final TransferTransactionInfoRepository transferTransactionInfoRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final KafkaTemplate<String, NotificationMessage> kafkaTemplate;

  @Autowired
  public TransferServiceImpl(
          @Qualifier("accountsServiceApi") AccountsServiceApi accountsServiceApi,
          BlockerServiceApi blockerServiceApi, ExchangeServiceApi exchangeServiceApi,
          TransferTransactionInfoRepository transferTransactionInfoRepository, KafkaTemplate<String, NotificationMessage> kafkaTemplate) {
    this.accountsServiceApi = accountsServiceApi;
    this.blockerServiceApi = blockerServiceApi;
      this.exchangeServiceApi = exchangeServiceApi;
      this.transferTransactionInfoRepository = transferTransactionInfoRepository;
      this.kafkaTemplate = kafkaTemplate;
  }

    @Override
    public Mono<TransferOperationResponse> processTransferTransaction(TransferOperationRequest transferOperationRequest) {
        log.info("Получен запрос: {}", transferOperationRequest);
        TransferTransactionInfo transactionInfo = buildCashTransactionInfo(transferOperationRequest);

    return transferTransactionInfoRepository
        .save(transactionInfo)
        .flatMap(
            savedInfo -> {
              if (savedInfo.getFromLogin().equals(savedInfo.getToLogin())) {
                return performTransferOperationItself(savedInfo);
              } else {
                return performTransferOperationOther(savedInfo);
              }
            })
        .onErrorResume(e -> handleProcessingError(transactionInfo, e));
    }

    private Mono<TransferOperationResponse> performTransferOperationItself(TransferTransactionInfo savedInfo) {
        return Mono.zip(
                        getUserDetailResponseMono(savedInfo.getFromLogin()),
                        checkTransaction(savedInfo))
                .flatMap(
                        tuple ->
                                handleCheckTransactionItself(tuple.getT1(), tuple.getT2(), savedInfo));
    }

    private Mono<TransferOperationResponse> performTransferOperationOther(TransferTransactionInfo savedInfo) {
        return Mono.zip(
                        getUserDetailResponseMono(savedInfo.getFromLogin()),
                        getUserDetailResponseMono(savedInfo.getToLogin()),
                        checkTransaction(savedInfo))
                .flatMap(
                        tuple ->
                                handleCheckTransactionOther(
                                        tuple.getT1(), tuple.getT2(), tuple.getT3(), savedInfo));
    }

    private Mono<TransferOperationResponse> handleCheckTransactionOther(
            UserDetailResponse fromUser, UserDetailResponse toUser, CheckTransactionResponse check, TransferTransactionInfo info) {
        log.info("Ответ blocked-service: {}", check);
        log.info("Пользователь-отправитель: {}", fromUser);
        log.info("Пользователь-получатель: {}", toUser);
        if (check.getIsBlocked()) {
            assert check.getReason() != null;
            return updateBlockedTransactionAndNotify(info, fromUser, "Блокировка операции: " + check.getReason(),
                    List.of(check.getReason()));
        }

        info.setBlocked(false);
        info.setUpdatedAt(Instant.now().getEpochSecond());
        return transferTransactionInfoRepository.save(info)
                .flatMap(updated -> {
                    if (updated.getFromCurrency().equals(updated.getToCurrency())) {
                        updated.setToAmount(updated.getFromAmount());
                        updated.setUpdatedAt(Instant.now().getEpochSecond());
                        return transferTransactionInfoRepository.save(updated);
                    } else {
                        return performConvertOperation(updated);
                    }
                })
                .flatMap(updatedWithToAmount -> performAccountsOperationOther(updatedWithToAmount, fromUser, toUser));
    }

    private TransferTransactionInfo buildCashTransactionInfo(TransferOperationRequest request) {
        return TransferTransactionInfo.builder()
                .fromLogin(request.getFromLogin())
                .toLogin(request.getToLogin())
                .fromCurrency(request.getFromCurrency().name())
                .toCurrency(request.getToCurrency().name())
                .fromAmount(request.getAmount())
                .build();
    }

    private Mono<TransferOperationResponse> handleCheckTransactionItself(
            UserDetailResponse user, CheckTransactionResponse check, TransferTransactionInfo info) {
      log.info("Ответ blocked-service: {}", check);
      log.info("Пользователь: {}", user);
      if (check.getIsBlocked()) {
            assert check.getReason() != null;
            return updateBlockedTransactionAndNotify(info, user, "Блокировка операции: " + check.getReason(),
                    List.of(check.getReason()));
        }

        info.setBlocked(false);
        info.setUpdatedAt(Instant.now().getEpochSecond());
        return transferTransactionInfoRepository.save(info)
                .flatMap(savedInfo -> {
                    if (savedInfo.getFromCurrency().equals(savedInfo.getToCurrency())) {
                        savedInfo.setToAmount(savedInfo.getFromAmount());
                        savedInfo.setUpdatedAt(Instant.now().getEpochSecond());
                        return transferTransactionInfoRepository.save(info)
                                .flatMap(updatedWithToAmount -> performAccountsOperationItself(updatedWithToAmount, user));
                    } else {
                     return performConvertOperation(savedInfo)
                             .flatMap(updatedWithToAmount -> performAccountsOperationItself(updatedWithToAmount, user));
                    }
                });
    }

    private Mono<TransferTransactionInfo> performConvertOperation(TransferTransactionInfo info) {
        ConvertRequest convertRequest = new ConvertRequest();
        convertRequest.setFrom(info.getFromCurrency());
        convertRequest.setTo(info.getToCurrency());
        convertRequest.setAmount(info.getFromAmount());
        return getConvertedAmount(convertRequest)
                .flatMap(convertedAmount -> {
                    info.setToAmount(convertedAmount.getAmount());
                    info.setUpdatedAt(Instant.now().getEpochSecond());
                    return transferTransactionInfoRepository.save(info);
                });
        }

    private Mono<TransferOperationResponse> performAccountsOperationItself(TransferTransactionInfo info, UserDetailResponse user) {
        TransferRequest request = buildTransferRequest(info);
        log.info("Запрос на перевод: {}", request);
        return getAccountOperationResponse(request, user.getLogin())
                .flatMap(response -> {
                    log.info("Ответ сервиса аккаунтов: {}", response);
                    boolean isSuccess = response.getOperationStatus() == AccountOperationResponse.OperationStatusEnum.SUCCESS;
                    String msg = isSuccess
                            ? buildSuccessMessageItself(info)
                            : "Отмена перевода между счетами. Список ошибок: " + response.getErrors();

                    info.setSuccess(isSuccess);
                    info.setUpdatedAt(Instant.now().getEpochSecond());

                    return transferTransactionInfoRepository.save(info)
                            .flatMap(saved -> sendNotification(saved.getId(), user.getEmail(), msg)
                                    .then(getTransferOperationResponse(
                                            isSuccess ? TransferOperationResponse.OperationStatusEnum.SUCCESS : TransferOperationResponse.OperationStatusEnum.FAILED,
                                            isSuccess ? List.of() : response.getErrors()
                                    )));
                });
    }

    private Mono<TransferOperationResponse> performAccountsOperationOther(TransferTransactionInfo info, UserDetailResponse fromUser, UserDetailResponse toUser) {
        TransferRequest request = buildTransferRequest(info);

        return getAccountOperationResponse(request, fromUser.getLogin())
                .flatMap(response -> {
                    log.info("Ответ сервиса аккаунтов: {}", response);
                    boolean isSuccess = response.getOperationStatus() == AccountOperationResponse.OperationStatusEnum.SUCCESS;
                    String fromUserMessage =
                            isSuccess
                                    ? "Успешный перевод " + info.getFromAmount() + info.getFromCurrency() + " клиенту " + toUser.getName()
                                    : "Отмена перевода клиенту " + toUser.getName() + ". Список ошибок: " + response.getErrors();
                    String toUserMessage = "Получен перевод " + info.getToAmount() + info.getToCurrency() + " от клиента " + fromUser.getName();


                    info.setSuccess(isSuccess);
                    info.setUpdatedAt(Instant.now().getEpochSecond());

                    return transferTransactionInfoRepository.save(info)
                            .flatMap(saved -> {
                                if (saved.isSuccess()) {
                                    return sendNotification(saved.getId(), fromUser.getEmail(), fromUserMessage)
                                            .then(sendNotification(saved.getId(), toUser.getEmail(), toUserMessage))
                                            .then(getTransferOperationResponse(TransferOperationResponse.OperationStatusEnum.SUCCESS, List.of()));
                                } else {
                                    return sendNotification(saved.getId(), fromUser.getEmail(), fromUserMessage)
                                            .then(getTransferOperationResponse(TransferOperationResponse.OperationStatusEnum.FAILED, response.getErrors()));
                                }
                            });
                });
    }

    private Mono<ConvertedAmount> getConvertedAmount(ConvertRequest convertRequest) {
        return exchangeServiceApi.convertCurrency(convertRequest)
                .retryWhen(
                        Retry.max(1)
                                .filter(
                                        throwable ->
                                                (throwable instanceof WebClientResponseException
                                                        && ((WebClientResponseException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                                                        || throwable instanceof WebClientRequestException)
                                .onRetryExhaustedThrow(
                                        (spec, signal) ->
                                                new UnavailabilityAccountsServiceException("Сервис конвертации валют временно недоступен.")))
                .onErrorResume(WebClientResponseException.class, ex ->
                        Mono.error(new AccountsServiceException("Ошибка при обработке транзакции: " + ex.getMessage())));
    }

    private Mono<TransferOperationResponse> updateBlockedTransactionAndNotify(
            TransferTransactionInfo info, UserDetailResponse user, String message, List<String> errors) {
        info.setBlocked(true);
        info.setSuccess(false);
        info.setUpdatedAt(Instant.now().getEpochSecond());
        return transferTransactionInfoRepository.save(info)
                .flatMap(saved -> sendNotification(saved.getId(), user.getEmail(), message)
                        .then(getTransferOperationResponse(TransferOperationResponse.OperationStatusEnum.FAILED, errors)));
    }

    private Mono<TransferOperationResponse> handleProcessingError(TransferTransactionInfo info, Throwable error) {
        info.setBlocked(false);
        info.setSuccess(false);
        info.setUpdatedAt(Instant.now().getEpochSecond());
        log.error("Ошибки в процессе обработки:", error);
        return transferTransactionInfoRepository.save(info)
                .flatMap(saved -> {
                    if (error instanceof CashOperationException err) return Mono.error(err);
                    String message = "Ошибка при обработке перевода: " + error.getMessage();
                    return sendNotification(saved.getId(), info.getFromLogin(), message)
                            .then(getTransferOperationResponse(TransferOperationResponse.OperationStatusEnum.FAILED, List.of(message)));
                });
    }

    private TransferRequest buildTransferRequest(TransferTransactionInfo info) {
        TransferRequest request = new TransferRequest();
        request.setFromCurrency(AccountCurrencyEnum.fromValue(info.getFromCurrency()));
        request.setToCurrency(AccountCurrencyEnum.fromValue(info.getToCurrency()));
        request.setFromAmount(info.getFromAmount());
        request.setToAmount(info.getToAmount());
        request.setToLogin(info.getToLogin());
        return request;
    }

    private String buildSuccessMessageItself(TransferTransactionInfo info) {
        return "Успешная операция перевода денежных средств между счетами: " + info.getFromAmount() + info.getFromCurrency() + " -> " + info.getToAmount() + info.getToCurrency();
    }

    private Mono<AccountOperationResponse> getAccountOperationResponse(TransferRequest transferRequest, String login) {
      log.info("Логин: {}. Запрос: {}", login, transferRequest);
        return accountsServiceApi.transferTransaction(login, transferRequest)
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

    private Mono<CheckTransactionResponse> checkTransaction(TransferTransactionInfo transferTransactionInfo) {
        CheckTransferTransactionRequest request = new CheckTransferTransactionRequest();
        request.setTransactionId(transferTransactionInfo.getId());
        request.setFromCurrency(BlockerCurrencyEnum.fromValue(transferTransactionInfo.getFromCurrency()));
        request.setToCurrency(BlockerCurrencyEnum.fromValue(transferTransactionInfo.getToCurrency()));
        request.setAmount(transferTransactionInfo.getFromAmount());
        request.setIsItself(transferTransactionInfo.getFromLogin().equals(transferTransactionInfo.getToLogin()));
        return getCheckTransactionResponse(request);
    }

    private Mono<CheckTransactionResponse> getCheckTransactionResponse(CheckTransferTransactionRequest request) {
        return blockerServiceApi.checkTransferTransaction(request)
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

    private Mono<Void> sendNotification(Long userId, String email, String message) {
        NotificationMessage notificationMessage =
                NotificationMessage.builder()
                        .email(email)
                        .message(message)
                        .application("transfer-service")
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

    private Mono<TransferOperationResponse> getTransferOperationResponse(TransferOperationResponse.OperationStatusEnum status, List<String> errors) {
        return Mono.just(new TransferOperationResponse(status, errors));
    }

}
