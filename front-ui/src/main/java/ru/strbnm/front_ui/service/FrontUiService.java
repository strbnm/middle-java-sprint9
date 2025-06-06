package ru.strbnm.front_ui.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.front_ui.client.accounts.domain.*;
import ru.strbnm.front_ui.client.cash.domain.CashOperationRequest;
import ru.strbnm.front_ui.client.cash.domain.CashOperationResponse;
import ru.strbnm.front_ui.client.transfer.domain.TransferOperationRequest;
import ru.strbnm.front_ui.client.transfer.domain.TransferOperationResponse;

public interface FrontUiService {
    Mono<UserDetailResponse> getUserDetailByLogin(String login);
    Flux<UserListResponseInner> getAllUsers();
    Mono<AccountOperationResponse> updateUser(String login, UserRequest userRequest);
    Mono<AccountOperationResponse> createUser(UserRequest userRequest);
    Mono<AccountOperationResponse> updateUserPassword(String login, String rawPassword);
    Mono<CashOperationResponse> performCashOperation(String login, CashOperationRequest cashOperation);
    Mono<TransferOperationResponse> performTransferOperation(String login, TransferOperationRequest transferOperation);
}
