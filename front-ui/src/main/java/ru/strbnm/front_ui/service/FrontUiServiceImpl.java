package ru.strbnm.front_ui.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.strbnm.front_ui.client.accounts.api.AccountsServiceApi;
import ru.strbnm.front_ui.client.accounts.domain.*;
import ru.strbnm.front_ui.client.cash.api.CashServiceApi;
import ru.strbnm.front_ui.client.cash.domain.CashOperationRequest;
import ru.strbnm.front_ui.client.cash.domain.CashOperationResponse;
import ru.strbnm.front_ui.client.transfer.api.TransferServiceApi;
import ru.strbnm.front_ui.client.transfer.domain.TransferOperationRequest;
import ru.strbnm.front_ui.client.transfer.domain.TransferOperationResponse;
import ru.strbnm.front_ui.exception.*;

import java.io.IOException;
import java.util.function.Function;

@Service
public class FrontUiServiceImpl implements FrontUiService {

    private final AccountsServiceApi accountsServiceApi;
    private final CashServiceApi cashServiceApi;
    private final TransferServiceApi transferServiceApi;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public FrontUiServiceImpl(
            AccountsServiceApi accountsServiceApi,
            CashServiceApi cashServiceApi,
            TransferServiceApi transferServiceApi, PasswordEncoder passwordEncoder) {
        this.accountsServiceApi = accountsServiceApi;
        this.cashServiceApi = cashServiceApi;
        this.transferServiceApi = transferServiceApi;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = new ObjectMapper();
    }


    @Override
    public Mono<UserDetailResponse> getUserDetailByLogin(String login) {
        return withRetryAndErrorHandlingUserDetailResponse(accountsServiceApi.getUser(login));

    }

    @Override
    public Flux<UserListResponseInner> getAllUsers() {
        return  withRetryAndErrorHandlingUserListResponse(accountsServiceApi.getUserList());
    }

    @Override
    public Mono<AccountOperationResponse> updateUser(String login, UserRequest userRequest) {
        return withRetryAndErrorHandlingAccountOperationResponse(accountsServiceApi.updateUser(login, userRequest));
    }

    @Override
    public Mono<AccountOperationResponse> createUser(UserRequest userRequest) {
        String encodedPassword = passwordEncoder.encode(userRequest.getPassword());
        userRequest.setPassword(encodedPassword);
        return withRetryAndErrorHandlingAccountOperationResponse(accountsServiceApi.createUser(userRequest));
    }

    @Override
    public Mono<AccountOperationResponse> updateUserPassword(String login, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);

        UserPasswordRequest userPasswordRequest = new UserPasswordRequest();
        userPasswordRequest.setLogin(login);
        userPasswordRequest.setNewPassword(encodedPassword);

        return withRetryAndErrorHandlingAccountOperationResponse(accountsServiceApi.updateUserPassword(
                login, userPasswordRequest));
    }

    @Override
    public Mono<CashOperationResponse> performCashOperation(String login, CashOperationRequest cashOperation) {
        return withRetryAndErrorHandlingCashOperationResponse(cashServiceApi.cashTransaction(cashOperation));
    }

    @Override
    public Mono<TransferOperationResponse> performTransferOperation(String login, TransferOperationRequest transferOperation) {
        return withRetryAndErrorHandlingTransferOperationResponse(transferServiceApi.transferTransaction(transferOperation));
    }

   private Mono<UserDetailResponse> withRetryAndErrorHandlingUserDetailResponse(Mono<UserDetailResponse> mono) {
        return mono.retryWhen(
                        Retry.max(1)
                                .filter(this::isRetryableException)
                                .onRetryExhaustedThrow(
                                        (spec, signal) ->
                                                new UnavailabilityAccountsServiceException(
                                                        "Сервис аккаунтов временно недоступен.")))
                .onErrorResume(
                        WebClientResponseException.class,
                        ex -> Mono.error(new AccountsServiceException("Ошибка при получении данных клиента: " + ex.getMessage())));
    }

    private Flux<UserListResponseInner> withRetryAndErrorHandlingUserListResponse(Flux<UserListResponseInner> flux) {
        return flux.retryWhen(
                        Retry.max(1)
                                .filter(this::isRetryableException)
                                .onRetryExhaustedThrow(
                                        (spec, signal) ->
                                                new UnavailabilityAccountsServiceException(
                                                        "Сервис аккаунтов временно недоступен.")))
                .onErrorResume(
                        WebClientResponseException.class,
                        ex -> Mono.error(new AccountsServiceException("Ошибка при получении списка клиентов: " + ex.getMessage())));
    }

    private Mono<AccountOperationResponse>
    withRetryAndErrorHandlingAccountOperationResponse(Mono<AccountOperationResponse> mono) {
        return mono
                .retryWhen(Retry.max(1)
                        .filter(this::isRetryableException)
                        .onRetryExhaustedThrow((spec, signal) ->
                                new UnavailabilityAccountsServiceException("Сервис аккаунтов временно не доступен.")))
                .onErrorResume(WebClientResponseException.class,
                        ex -> handleOperationError(ex, AccountOperationResponse.class,
                                AccountsServiceException::new));
    }

    private Mono<CashOperationResponse> withRetryAndErrorHandlingCashOperationResponse(Mono<CashOperationResponse> mono) {
        return mono
                .retryWhen(Retry.max(1)
                        .filter(this::isRetryableException)
                        .onRetryExhaustedThrow((spec, signal) ->
                                new UnavailabilityCashServiceException("Сервис обналичивания временно не доступен.")))
                .onErrorResume(WebClientResponseException.class,
                        ex -> handleOperationError(ex, CashOperationResponse.class,
                                CashServiceException::new));
    }

    private Mono<TransferOperationResponse> withRetryAndErrorHandlingTransferOperationResponse(Mono<TransferOperationResponse> mono) {
        return mono
                .retryWhen(Retry.max(1)
                        .filter(this::isRetryableException)
                        .onRetryExhaustedThrow((spec, signal) ->
                                new UnavailabilityTransferServiceException("Сервис переводов временно не доступен.")))
                .onErrorResume(WebClientResponseException.class,
                        ex -> handleOperationError(ex, TransferOperationResponse.class,
                                TransferServiceException::new));
    }

    private boolean isRetryableException(Throwable throwable) {
        return (throwable instanceof WebClientResponseException
                && ((WebClientResponseException) throwable).getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                || throwable instanceof WebClientRequestException;
    }

    private <T> Mono<T> handleOperationError(
            WebClientResponseException ex,
            Class<T> responseType,
            Function<String, ? extends RuntimeException> errorFactory
    ) {
        if (ex.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
            try {
                T response = objectMapper.readValue(ex.getResponseBodyAsString(), responseType);
                return Mono.just(response);
            } catch (IOException e) {
                return Mono.error(errorFactory.apply("Не удалось прочитать тело ответа: " + e.getMessage()));
            }
        }
        return Mono.error(errorFactory.apply("Ошибка при обработке запроса: " + ex.getMessage()));
    }

}
