package ru.strbnm.accounts_service.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.domain.*;

public interface UserService {
    // Операции с пользователями
    Mono<UserDetailResponse> createUser(UserRequest userRequest);
    Mono<UserDetailResponse> updateUser(UserRequest userRequest);
    Flux<UserListResponseInner> getUserList();
    Mono<UserDetailResponse> getUserByLogin(String login);

    // Операции со счетами
    Mono<UserDetailResponse> cashOperation(CashRequest cashRequest, String login);
    Mono<UserDetailResponse> transferOperation(TransferRequest transferRequest, String login);
}
