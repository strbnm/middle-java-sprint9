package ru.strbnm.accounts_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.api.AccountsServiceApi;
import ru.strbnm.accounts_service.domain.*;
import ru.strbnm.accounts_service.service.UserService;

@Controller
@RequestMapping("${openapi.accountsService.base-path:/}")
public class AccountsController implements AccountsServiceApi {

    private final UserService userService;

    @Autowired
    public AccountsController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> cashTransaction(String login, Mono<CashRequest> cashRequest, ServerWebExchange exchange) {
        return cashRequest.flatMap(
                request -> userService.cashOperation(request, login))
                .flatMap(this::returnOperationResponse);
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> createUser(Mono<UserRequest> userRequest, ServerWebExchange exchange) {
        return userRequest.flatMap(userService::createUser)
                .flatMap(this::returnOperationResponseCreated);
    }

    @Override
    public Mono<ResponseEntity<UserDetailResponse>> getUser(String login, ServerWebExchange exchange) {
        return userService.getUserByLogin(login)
                .flatMap(userDetailResponse -> Mono.just(ResponseEntity.ok().body(userDetailResponse)));
    }

    @Override
    public Mono<ResponseEntity<Flux<UserListResponseInner>>> getUserList(ServerWebExchange exchange) {
        Flux<UserListResponseInner> userList = userService.getUserList();
        return Mono.just(ResponseEntity.ok(userList));
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> transferTransaction(String login, Mono<TransferRequest> transferRequest, ServerWebExchange exchange) {
        return transferRequest.flatMap(
                request -> userService.transferOperation(request, login))
                .flatMap(this::returnOperationResponse);
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> updateUser(String login, Mono<UserRequest> userRequest, ServerWebExchange exchange) {
        return userRequest.flatMap(userService::updateUser)
                .flatMap(this::returnOperationResponse);
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> updateUserPassword(String login, Mono<UserPasswordRequest> userPasswordRequest, ServerWebExchange exchange) {
        return userPasswordRequest.flatMap(userService::updateUserPassword)
                .flatMap(this::returnOperationResponse);
    }


    private Mono<ResponseEntity<OperationResponse>> returnOperationResponse (OperationResponse operationResponse) {
        if (operationResponse.getOperationStatus() == OperationResponse.OperationStatusEnum.FAILED) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(operationResponse));
        } else {
            return Mono.just(ResponseEntity.ok().body(operationResponse));
        }
    }

    private Mono<ResponseEntity<OperationResponse>> returnOperationResponseCreated (OperationResponse operationResponse) {
        if (operationResponse.getOperationStatus() == OperationResponse.OperationStatusEnum.FAILED) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(operationResponse));
        } else {
            return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(operationResponse));
        }
    }
}
