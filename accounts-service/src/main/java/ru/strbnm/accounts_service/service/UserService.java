package ru.strbnm.accounts_service.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.domain.*;

public interface UserService {
    // Операции с пользователями
    Mono<OperationResponse> createUser(UserRequest userRequest);
    Mono<OperationResponse> updateUser(UserRequest userRequest);
    Flux<UserListResponseInner> getUserList();
    Mono<UserDetailResponse> getUserByLogin(String login);
    Mono<OperationResponse> updateUserPassword(UserPasswordRequest userPasswordRequest);

    // Операции со счетами пользователей
    Mono<OperationResponse> cashOperation(CashRequest cashRequest, String login);
    Mono<OperationResponse> transferOperation(TransferRequest transferRequest, String login);
}
