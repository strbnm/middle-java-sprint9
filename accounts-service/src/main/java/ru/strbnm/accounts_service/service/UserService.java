package ru.strbnm.accounts_service.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.domain.*;

public interface UserService {
    // Операции с пользователями
    Mono<AccountOperationResponse> createUser(UserRequest userRequest);
    Mono<AccountOperationResponse> updateUser(UserRequest userRequest);
    Flux<UserListResponseInner> getUserList();
    Mono<UserDetailResponse> getUserByLogin(String login);
    Mono<AccountOperationResponse> updateUserPassword(UserPasswordRequest userPasswordRequest);

    // Операции со счетами пользователей
    Mono<AccountOperationResponse> cashOperation(CashRequest cashRequest, String login);
    Mono<AccountOperationResponse> transferOperation(TransferRequest transferRequest, String login);
}
